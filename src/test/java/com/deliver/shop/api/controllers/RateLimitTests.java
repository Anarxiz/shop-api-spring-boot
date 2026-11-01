package com.deliver.shop.api.controllers;

import com.deliver.shop.TestcontainersConfiguration;
import com.deliver.shop.support.ApiTestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Лимит снижен до 3 запросов в минуту, чтобы тест был быстрым. */
@SpringBootTest(properties = "app.rate-limit.auth-requests-per-minute=3")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void login_afterLimitExceeded_returnsTooManyRequests() throws Exception {
        String json = ApiTestClient.loginJson(ApiTestClient.uniqueEmail(), ApiTestClient.PASSWORD);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                            .content(json))
                    .andExpect(status().isUnauthorized()); // неверные данные, но лимит ещё не исчерпан
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content(json))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void register_afterLimitExceeded_returnsTooManyRequests() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                            .content(ApiTestClient.registerJson(
                                    ApiTestClient.uniqueEmail(), ApiTestClient.uniquePhone(),
                                    ApiTestClient.PASSWORD, "Иван")))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content(ApiTestClient.registerJson(
                                ApiTestClient.uniqueEmail(), ApiTestClient.uniquePhone(),
                                ApiTestClient.PASSWORD, "Иван")))
                .andExpect(status().isTooManyRequests());
    }
}
