package com.deliver.shop.infrastructure.cache;

import com.deliver.shop.application.events.CatalogChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.catalog.cache.enabled", havingValue = "true")
class CatalogCacheInvalidator {
    private final CacheManager cacheManager;
    private final CacheErrorHandler errorHandler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidate(CatalogChangedEvent event) {
        for (String name : cacheManager.getCacheNames()) {
            var cache = cacheManager.getCache(name);
            if (cache == null) {
                continue;
            }
            try {
                cache.invalidate();
            } catch (RuntimeException ex) {
                errorHandler.handleCacheClearError(ex, cache);
            }
        }
    }
}
