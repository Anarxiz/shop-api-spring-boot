package com.deliver.shop.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ограничение частоты запросов на вход и регистрацию (защита от подбора паролей).
 * Фиксированное окно: не более N запросов в минуту с одного IP на каждый из эндпоинтов.
 * За обратным прокси нужно настроить определение реального IP клиента.
 */
@Component
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of("/api/auth/login", "/api/auth/register");
    private static final long WINDOW_MILLIS = 60_000;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(long start, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;

    public RateLimitFilter(@Value("${app.rate-limit.auth-requests-per-minute:5}") int limit) {
        this.limit = limit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> now - w.start() >= WINDOW_MILLIS);
        }

        String key = request.getRemoteAddr() + request.getRequestURI();
        Window window = windows.compute(key, (k, old) ->
                old == null || now - old.start() >= WINDOW_MILLIS
                        ? new Window(now, 1)
                        : new Window(old.start(), old.count() + 1));

        if (window.count() > limit) {
            long retryAfterSeconds = (window.start() + WINDOW_MILLIS - now) / 1000 + 1;
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType("application/problem+json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,"
                    + "\"detail\":\"Слишком много запросов, повторите попытку позже\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
