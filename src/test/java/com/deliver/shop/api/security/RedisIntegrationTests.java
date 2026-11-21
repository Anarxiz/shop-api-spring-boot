package com.deliver.shop.api.security;

import com.deliver.shop.TestcontainersConfiguration;
import com.deliver.shop.application.contracts.category.CreateCategoryRequest;
import com.deliver.shop.application.contracts.dish.CreateDishRequest;
import com.deliver.shop.application.contracts.dish.UpdateDishRequest;
import com.deliver.shop.application.services.CategoryService;
import com.deliver.shop.application.services.DishService;
import com.deliver.shop.support.ApiTestClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.rate-limit.redis.enabled=true",
        "app.rate-limit.auth-requests-per-minute=1",
        "app.catalog.cache.enabled=true"
})
@Import(TestcontainersConfiguration.class)
class RedisIntegrationTests {
    private static final String LOGIN = "/api/auth/login";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.8.0-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry properties) {
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private MeterRegistry metrics;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private CategoryService categories;
    @Autowired
    private DishService dishes;
    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void loginUsesSharedCounterAndRedisExpiry() throws Exception {
        String address = "192.0.2.15";
        var otherInstance = new RedisRateLimitStore(redis, new LocalRateLimitStore(), metrics);
        assertThat(otherInstance.recordRequest(LOGIN, address).count()).isEqualTo(1);

        mvc.perform(post(LOGIN)
                        .with(request -> {
                            request.setRemoteAddr(address);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ApiTestClient.loginJson(ApiTestClient.uniqueEmail(), ApiTestClient.PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]?")));

        assertThat(otherInstance.recordRequest(LOGIN, address).count()).isEqualTo(3);
        var keys = redis.keys("shop:rate-limit:v1:" + LOGIN + ":*");
        assertThat(keys).hasSize(1);
        String key = keys.iterator().next();
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(1L, 60_000L);
        // Ускоряем истечение окна, чтобы проверить сброс счётчика без минутной паузы.
        redis.expire(key, Duration.ofMillis(50));
        await().atMost(Duration.ofSeconds(2)).until(() -> !redis.hasKey(key));
        assertThat(otherInstance.recordRequest(LOGIN, address).count()).isEqualTo(1);
    }

    @Test
    void failedRedisCallFallsBackToLocalCounter() {
        var unavailable = mock(StringRedisTemplate.class);
        when(unavailable.execute(ArgumentMatchers.<RedisScript<Object>>any(), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        var registry = new SimpleMeterRegistry();
        var store = new RedisRateLimitStore(unavailable, new LocalRateLimitStore(), registry);
        assertThat(store.recordRequest(LOGIN, "192.0.2.16").count()).isEqualTo(1);
        assertThat(store.recordRequest(LOGIN, "192.0.2.16").count()).isEqualTo(2);
        assertThat(registry.get("shop.rate.limit.fallback.requests").counter().count()).isEqualTo(2);
    }

    @Test
    void catalogUsesTypedCacheAndInvalidatesBothViewsOnlyAfterCommit() {
        UUID category = categories.create(new CreateCategoryRequest(ApiTestClient.uniqueCategoryName()));
        UUID dish = dishes.create(new CreateDishRequest("Борщ", "Описание", "Состав",
                new BigDecimal("100.00"), category, null));
        var initialCategory = categories.getById(category);
        var initialDish = dishes.getById(dish);
        var initialList = dishes.getAll();
        String categoryKey = "shop:catalog:v1:category::" + category;
        String dishKey = "shop:catalog:v1:dish::" + dish;
        String listKey = "shop:catalog:v1:dishes::SimpleKey []";

        assertThat(redis.opsForValue().get(categoryKey)).contains(dish.toString());
        assertThat(redis.getExpire(dishKey, TimeUnit.MILLISECONDS)).isBetween(1L, 45_000L);
        assertThat(redis.opsForValue().get(listKey)).contains(dish.toString());
        assertThat(categories.getById(category)).isEqualTo(initialCategory);
        assertThat(dishes.getById(dish)).isEqualTo(initialDish);
        assertThat(dishes.getAll()).isEqualTo(initialList);
        assertThat(metrics.get("shop.catalog.cache.lookups").tags("cache", "dish", "result", "hit")
                .functionCounter().count()).isPositive();

        var update = new UpdateDishRequest("Борщ", "Описание", "Состав",
                new BigDecimal("150.00"), category, null, true);
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            dishes.update(dish, update);
            transaction.setRollbackOnly();
        });
        assertThat(redis.hasKey(categoryKey)).isTrue();
        assertThat(redis.hasKey(dishKey)).isTrue();
        assertThat(redis.hasKey(listKey)).isTrue();

        dishes.update(dish, update);
        assertThat(redis.hasKey(categoryKey)).isFalse();
        assertThat(redis.hasKey(dishKey)).isFalse();
        assertThat(redis.hasKey(listKey)).isFalse();
        assertThat(dishes.getById(dish).price()).isEqualByComparingTo("150.00");
        assertThat(categories.getById(category).dishes().getFirst().price()).isEqualByComparingTo("150.00");
    }

    @Test
    void catalogSurvivesRedisCommandFailures() throws Exception {
        UUID category = categories.create(new CreateCategoryRequest(ApiTestClient.uniqueCategoryName()));
        UUID dish = dishes.create(new CreateDishRequest("Борщ", "Описание", "Состав",
                new BigDecimal("100.00"), category, null));
        assertThat(REDIS.execInContainer("redis-cli", "ACL", "SETUSER", "default",
                "-get", "-set", "-scan").getStdout()).contains("OK");
        try {
            dishes.update(dish, new UpdateDishRequest("Борщ", "Описание", "Состав",
                    new BigDecimal("175.00"), category, null, true));
            assertThat(dishes.getById(dish).price()).isEqualByComparingTo("175.00");
            assertThat(metrics.get("shop.catalog.cache.errors").tags("cache", "dish", "operation", "get")
                    .counter().count()).isPositive();
        } finally {
            assertThat(REDIS.execInContainer("redis-cli", "ACL", "SETUSER", "default",
                    "+get", "+set", "+scan").getStdout()).contains("OK");
        }
    }
}
