package com.deliver.shop.api.controllers;

import com.deliver.shop.TestcontainersConfiguration;
import com.deliver.shop.application.interfaces.repository.CategoryRepository;
import com.deliver.shop.application.interfaces.repository.DishRepository;
import com.deliver.shop.application.interfaces.repository.UserRepository;
import com.deliver.shop.application.services.PasswordHasher;
import com.deliver.shop.support.ApiTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Stream;

import static com.deliver.shop.support.ApiTestClient.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AddressControllerTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordHasher passwordHasher;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private DishRepository dishRepository;

    private ApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(mockMvc, userRepository, passwordHasher, categoryRepository, dishRepository);
    }

    private ResultActions run(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        if (json != null) {
            request = request.contentType(MediaType.APPLICATION_JSON)
                    .characterEncoding(StandardCharsets.UTF_8).content(json);
        }
        if (token != null) request = request.header("Authorization", bearer(token));
        return mockMvc.perform(request);
    }

    // ---------- POST ----------

    @Test
    void create_withValidData_returnsCreated() throws Exception {
        var session = api.registerAndLogin();

        run(post("/api/addresses"), session.token(), validAddressJson())
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$").isNotEmpty());
    }

    static Stream<Arguments> invalidAddresses() {
        return Stream.of(
                Arguments.of("", "Тверская", "1", "10", null),
                Arguments.of("London", "Тверская", "1", "10", null),
                Arguments.of("Москва", "", "1", "10", null),
                Arguments.of("Москва", "Тверская", "", "10", null),
                Arguments.of("Москва", "Тверская", "1", "123456", null));
    }

    @ParameterizedTest
    @MethodSource("invalidAddresses")
    void create_withInvalidData_returnsBadRequest(String city, String street, String house, String apartment,
                                                  String floor) throws Exception {
        var session = api.registerAndLogin();

        run(post("/api/addresses"), session.token(), addressJson(city, street, house, apartment, floor))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withoutToken_returnsUnauthorized() throws Exception {
        run(post("/api/addresses"), null, validAddressJson()).andExpect(status().isUnauthorized());
    }

    // ---------- GET ----------

    @Test
    void getAll_returnsOnlyOwnAddresses() throws Exception {
        var first = api.registerAndLogin();
        var second = api.registerAndLogin();
        UUID firstAddress = api.createAddress(first.token());
        UUID secondAddress = api.createAddress(second.token());

        run(get("/api/addresses"), first.token(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + firstAddress + "')]").exists())
                .andExpect(jsonPath("$[?(@.id == '" + secondAddress + "')]").doesNotExist());
    }

    @Test
    void getAll_withoutToken_returnsUnauthorized() throws Exception {
        run(get("/api/addresses"), null, null).andExpect(status().isUnauthorized());
    }

    // ---------- PUT ----------

    @Test
    void update_withValidData_returnsOk() throws Exception {
        var session = api.registerAndLogin();
        UUID id = api.createAddress(session.token());

        run(put("/api/addresses/{id}", id), session.token(), addressJson("Казань", "Баумана", "5", "7", "3"))
                .andExpect(status().isOk());

        run(get("/api/addresses"), session.token(), null)
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].city").value("Казань"));
    }

    @Test
    void update_withInvalidData_returnsBadRequest() throws Exception {
        var session = api.registerAndLogin();
        UUID id = api.createAddress(session.token());

        run(put("/api/addresses/{id}", id), session.token(), addressJson("", "Баумана", "5", "7", null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_withNonExistentId_returnsNotFound() throws Exception {
        var session = api.registerAndLogin();

        run(put("/api/addresses/{id}", UUID.randomUUID()), session.token(), validAddressJson())
                .andExpect(status().isNotFound());
    }

    @Test
    void update_otherUsersAddress_returnsNotFound() throws Exception {
        var owner = api.registerAndLogin();
        var stranger = api.registerAndLogin();
        UUID id = api.createAddress(owner.token());

        run(put("/api/addresses/{id}", id), stranger.token(), validAddressJson())
                .andExpect(status().isNotFound());
    }

    @Test
    void update_withoutToken_returnsUnauthorized() throws Exception {
        run(put("/api/addresses/{id}", UUID.randomUUID()), null, validAddressJson())
                .andExpect(status().isUnauthorized());
    }

    // ---------- DELETE ----------

    @Test
    void delete_withValidId_returnsOk() throws Exception {
        var session = api.registerAndLogin();
        UUID id = api.createAddress(session.token());

        run(delete("/api/addresses/{id}", id), session.token(), null).andExpect(status().isOk());
        run(get("/api/addresses"), session.token(), null)
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").doesNotExist());
    }

    @Test
    void delete_withNonExistentId_returnsNotFound() throws Exception {
        var session = api.registerAndLogin();

        run(delete("/api/addresses/{id}", UUID.randomUUID()), session.token(), null)
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_otherUsersAddress_returnsNotFound() throws Exception {
        var owner = api.registerAndLogin();
        var stranger = api.registerAndLogin();
        UUID id = api.createAddress(owner.token());

        run(delete("/api/addresses/{id}", id), stranger.token(), null).andExpect(status().isNotFound());
    }

    @Test
    void delete_withoutToken_returnsUnauthorized() throws Exception {
        run(delete("/api/addresses/{id}", UUID.randomUUID()), null, null).andExpect(status().isUnauthorized());
    }
}
