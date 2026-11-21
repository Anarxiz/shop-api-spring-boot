package com.deliver.shop.api.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
class LocalRateLimitStore implements RateLimitStore {
    static final long WINDOW_MILLIS = 60_000;
    private static final long WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(WINDOW_MILLIS);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Entry(long start, long count) {
    }

    private final ConcurrentHashMap<String, Entry> windows = new ConcurrentHashMap<>();

    @Override
    public Window recordRequest(String endpoint, String address) {
        if (windows.size() > CLEANUP_THRESHOLD) {
            long now = System.nanoTime();
            windows.values().removeIf(entry -> now - entry.start() >= WINDOW_NANOS);
        }
        Entry entry = windows.compute(endpoint + ':' + address, (key, old) -> {
            long now = System.nanoTime();
            return old == null || now - old.start() >= WINDOW_NANOS
                    ? new Entry(now, 1)
                    : new Entry(old.start(), old.count() + 1);
        });
        return new Window(entry.count(), TimeUnit.NANOSECONDS.toMillis(
                WINDOW_NANOS - (System.nanoTime() - entry.start())));
    }
}
