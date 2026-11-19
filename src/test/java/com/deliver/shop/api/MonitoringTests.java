package com.deliver.shop.api;

import com.deliver.shop.api.security.JwtConfig;
import com.deliver.shop.api.security.MonitoringSecurityConfig;
import com.deliver.shop.api.security.SecurityConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Реальные HTTP-серверы проверяют изоляцию портов и сбор HTTP-метрик без БД. */
@SpringBootTest(classes = MonitoringTests.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
        })
class MonitoringTests {

    @Value("${local.server.port}")
    private int apiPort;

    @Value("${local.management.port}")
    private int managementPort;

    @Autowired
    private MeterRegistry registry;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void metricsAreAvailableOnlyOnManagementPort() throws Exception {
        assertThat(get(managementPort, "/actuator/health").statusCode()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/metrics").body()).contains("jvm.memory.used");
        assertThat(get(managementPort, "/actuator/prometheus").statusCode()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/env").statusCode()).isNotEqualTo(200);
        assertThat(get(apiPort, "/actuator/prometheus").statusCode()).isNotEqualTo(200);
        assertThat(get(apiPort, "/actuator/metrics").statusCode()).isNotEqualTo(200);
        assertThat(get(apiPort, "/api/orders").statusCode()).isEqualTo(401);
    }

    @Test
    void exportsHttpHistogramsWithRouteTemplatesAndApplicationTag() throws Exception {
        assertThat(get(apiPort, "/api/categories/first").statusCode()).isEqualTo(200);
        assertThat(get(apiPort, "/api/categories/second").statusCode()).isEqualTo(200);

        String scrape = get(managementPort, "/actuator/prometheus").body();
        assertThat(scrape).contains("http_server_requests_seconds_bucket", "application=\"shop\"",
                "uri=\"/api/categories/{id}\"", "jvm_memory_used_bytes", "process_cpu_usage");
        assertThat(registry.find("http.server.requests")
                .tags("uri", "/api/categories/{id}", "status", "200").timer()).isNotNull();
        assertThat(registry.find("http.server.requests")
                .tags("uri", "/api/categories/{id}", "status", "200").timer().count()).isEqualTo(2);
        assertThat(scrape).doesNotContain("uri=\"/api/categories/first\"", "uri=\"/api/categories/second\"");
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, MonitoringSecurityConfig.class, JwtConfig.class, ProbeController.class})
    static class TestApplication {
    }

    @RestController
    @TestComponent
    static class ProbeController {
        @GetMapping("/api/categories/{id}")
        String category(@PathVariable String id) {
            return id;
        }
    }
}
