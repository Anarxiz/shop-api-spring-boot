package com.deliver.shop.api.security;

interface RateLimitStore {
    Window recordRequest(String endpoint, String address);

    record Window(long count, long remainingMillis) {
    }
}
