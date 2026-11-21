package com.deliver.shop.infrastructure.cache;

import com.deliver.shop.application.contracts.category.CategoryResponse;
import com.deliver.shop.application.contracts.dish.DishResponse;
import com.deliver.shop.application.services.CatalogCaches;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableCaching
@ConditionalOnProperty(name = "app.catalog.cache.enabled", havingValue = "true")
class CatalogCacheConfig implements CachingConfigurer {
    private final ObjectProvider<MeterRegistry> metrics;

    CatalogCacheConfig(ObjectProvider<MeterRegistry> metrics) {
        this.metrics = metrics;
    }

    @Bean
    RedisCacheManager catalogCacheManager(RedisConnectionFactory connectionFactory,
                                          @Value("${app.catalog.cache.ttl}") Duration ttl,
                                          JsonMapper mapper) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("app.catalog.cache.ttl must be positive");
        }

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith("shop:catalog:v1:");
        var types = mapper.getTypeFactory();
        var writer = RedisCacheWriter.create(connectionFactory,
                config -> config.batchStrategy(BatchStrategies.scan(128)).immediateWrites());
        return RedisCacheManager.builder(writer)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(Map.of(
                        CatalogCaches.CATEGORY, json(defaults, mapper, types.constructType(CategoryResponse.class)),
                        CatalogCaches.CATEGORIES, json(defaults, mapper, types.constructCollectionType(List.class, CategoryResponse.class)),
                        CatalogCaches.DISH, json(defaults, mapper, types.constructType(DishResponse.class)),
                        CatalogCaches.DISHES, json(defaults, mapper, types.constructCollectionType(List.class, DishResponse.class)),
                        CatalogCaches.DISHES_BY_CATEGORY, json(defaults, mapper, types.constructCollectionType(List.class, DishResponse.class))))
                .disableCreateOnMissingCache()
                .enableStatistics()
                .build();
    }

    private static RedisCacheConfiguration json(RedisCacheConfiguration defaults, JsonMapper mapper, JavaType type) {
        return defaults.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                new JacksonJsonRedisSerializer<>(mapper, type)));
    }

    @Bean
    MeterBinder catalogCacheMetrics(RedisCacheManager cacheManager) {
        return registry -> cacheManager.getCacheNames().forEach(name -> {
            if (cacheManager.getCache(name) instanceof RedisCache cache) {
                FunctionCounter.builder("shop.catalog.cache.lookups", cache, c -> c.getStatistics().getHits())
                        .tags("cache", name, "result", "hit").register(registry);
                FunctionCounter.builder("shop.catalog.cache.lookups", cache, c -> c.getStatistics().getMisses())
                        .tags("cache", name, "result", "miss").register(registry);
            }
        });
    }

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                recordError("get", cache, ex);
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                recordError("put", cache, ex);
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                recordError("evict", cache, ex);
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                recordError("clear", cache, ex);
                log.warn("Не удалось сбросить кэш каталога {}: {}", cache.getName(), ex.getClass().getSimpleName());
            }
        };
    }

    private void recordError(String operation, Cache cache, RuntimeException ex) {
        metrics.ifAvailable(registry -> registry.counter("shop.catalog.cache.errors",
                "operation", operation, "cache", cache.getName()).increment());
        log.debug("Ошибка Redis при {} кэша {}: {}", operation, cache.getName(), ex.getClass().getSimpleName());
    }
}
