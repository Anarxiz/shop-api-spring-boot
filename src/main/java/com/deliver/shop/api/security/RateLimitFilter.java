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

/**
 * Ограничение частоты запросов на вход и регистрацию (защита от подбора паролей).
 * Фиксированное окно: не более N запросов в минуту с одного IP на каждый из эндпоинтов.
 * За обратным прокси нужно настроить определение реального IP клиента.
 */
@Component
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of("/api/auth/login", "/api/auth/register");
    private final RateLimitStore store;
    private final int limit;

    RateLimitFilter(RateLimitStore store, @Value("${app.rate-limit.auth-requests-per-minute:5}") int limit) {
        this.store = store;
        this.limit = limit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimitStore.Window window = store.recordRequest(request.getRequestURI(), request.getRemoteAddr());
        if (window.count() > limit) {
            long retryAfterSeconds = Math.max(1, (window.remainingMillis() + 999) / 1000);
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
