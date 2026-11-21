package com.deliver.shop.api.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = {"app.rate-limit.enabled", "app.rate-limit.redis.enabled"}, havingValue = "true")
class RedisRateLimitStore implements RateLimitStore {
    private static final RedisScript<List> RECORD = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return {count, redis.call('PTTL', KEYS[1])}
            """, List.class);

    private final StringRedisTemplate redis;
    private final LocalRateLimitStore fallback;
    private final Counter fallbackRequests;
    private final AtomicBoolean degraded = new AtomicBoolean();

    RedisRateLimitStore(StringRedisTemplate redis, LocalRateLimitStore fallback, MeterRegistry metrics) {
        this.redis = redis;
        this.fallback = fallback;
        this.fallbackRequests = metrics.counter("shop.rate.limit.fallback.requests");
    }

    @Override
    public Window recordRequest(String endpoint, String address) {
        String key = "shop:rate-limit:v1:" + endpoint + ':' + hash(address);
        try {
            List<?> result = redis.execute(RECORD, List.of(key), Long.toString(LocalRateLimitStore.WINDOW_MILLIS));
            if (result != null && result.size() == 2
                    && result.get(0) instanceof Long count && result.get(1) instanceof Long remaining
                    && remaining >= 0) {
                if (degraded.getAndSet(false)) {
                    log.info("Общий лимит запросов через Redis восстановлен");
                }
                return new Window(count, remaining);
            }
        } catch (DataAccessException ex) {
            if (degraded.compareAndSet(false, true)) {
                log.warn("Лимит запросов переключён на локальный счётчик: {}", ex.getClass().getSimpleName());
            }
        }
        if (degraded.compareAndSet(false, true)) {
            log.warn("Redis вернул неожиданный результат, используется локальный лимит");
        }
        fallbackRequests.increment();
        return fallback.recordRequest(endpoint, address);
    }

    private static String hash(String address) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(address.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
