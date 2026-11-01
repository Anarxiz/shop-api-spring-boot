package com.deliver.shop.api.controllers;

import com.deliver.shop.TestcontainersConfiguration;
import com.deliver.shop.application.interfaces.repository.CategoryRepository;
import com.deliver.shop.domain.entities.CategoryEntity;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DishControllerTests {

    private static final String LETTERS = "абвгдежзиклмнопрстуфхцчшэюя";
    private static final Random RANDOM = new Random();
    private static final String EMPTY_UUID = "00000000-0000-0000-0000-000000000000";

    private static final RequestPostProcessor ADMIN = user("admin").roles("ADMINISTRATOR");
    private static final RequestPostProcessor USER = user("user").roles("USER");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CategoryRepository categoryRepository;

    // ---------- helpers ----------

    private static String randomName() {
        StringBuilder sb = new StringBuilder("Кат");
        for (int i = 0; i < 10; i++) {
            sb.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        }
        return sb.toString();
    }

    private UUID createCategory() {
        CategoryEntity category = new CategoryEntity();
        category.setName(randomName());
        return categoryRepository.save(category).getId();
    }

    private static String str(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String createJson(String name, String description, String structure,
                                     String price, String categoryId, String imageUrl) {
        return "{\"name\":" + str(name) + ",\"description\":" + str(description)
                + ",\"structure\":" + str(structure) + ",\"price\":" + price
                + ",\"categoryId\":" + str(categoryId) + ",\"imageUrl\":" + str(imageUrl) + "}";
    }

    private static String validJson(UUID categoryId) {
        return createJson("Пицца", "Описание", "Состав", "500", categoryId.toString(), null);
    }

    private static String updateJson(String categoryId, boolean available) {
        return "{\"name\":\"Пицца\",\"description\":\"Описание\",\"structure\":\"Состав\",\"price\":650,"
                + "\"categoryId\":" + str(categoryId) + ",\"imageUrl\":null,\"isAvailable\":" + available + "}";
    }

    private org.springframework.test.web.servlet.ResultActions postDish(String json, RequestPostProcessor auth) throws Exception {
        var request = post("/api/dishes")
                .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8).content(json);
        return mockMvc.perform(auth == null ? request : request.with(auth));
    }

    private org.springframework.test.web.servlet.ResultActions putDish(UUID id, String json, RequestPostProcessor auth) throws Exception {
        var request = put("/api/dishes/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8).content(json);
        return mockMvc.perform(auth == null ? request : request.with(auth));
    }

    /** Создаёт блюдо от имени админа и возвращает его id. */
    private UUID createDish(UUID categoryId) throws Exception {
        String body = postDish(validJson(categoryId), ADMIN)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replace("\"", ""));
    }

    // ---------- POST ----------

    @Test
    void create_asAdmin_withValidData_returnsCreated() throws Exception {
        postDish(validJson(createCategory()), ADMIN)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$").isNotEmpty());
    }

    @Test
    void create_asAdmin_newDishIsUnavailableByDefault() throws Exception {
        UUID dishId = createDish(createCategory());

        mockMvc.perform(get("/api/dishes/{id}", dishId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(false));
    }

    static Stream<Arguments> invalidDishes() {
        return Stream.of(
                Arguments.of("", "Описание", "Состав", "500", null),
                Arguments.of("Пицца", "", "Состав", "500", null),
                Arguments.of("Пицца", "Описание", "", "500", null),
                Arguments.of("Пицца", "Описание", "Состав", "0", null),
                Arguments.of("Пицца", "Описание", "Состав", "-10", null),
                Arguments.of("Пицца", "Описание", "Состав", "500", "not-a-url"));
    }

    @ParameterizedTest
    @MethodSource("invalidDishes")
    void create_asAdmin_withInvalidData_returnsBadRequest(
            String name, String description, String structure, String price, String imageUrl) throws Exception {
        UUID categoryId = createCategory();

        postDish(createJson(name, description, structure, price, categoryId.toString(), imageUrl), ADMIN)
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_asAdmin_withEmptyCategoryId_returnsBadRequest() throws Exception {
        postDish(createJson("Пицца", "Описание", "Состав", "500", EMPTY_UUID, null), ADMIN)
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_asAdmin_withNonExistentCategoryId_returnsBadRequest() throws Exception {
        postDish(validJson(UUID.randomUUID()), ADMIN)
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_asRegularUser_returnsForbidden() throws Exception {
        postDish(validJson(UUID.randomUUID()), USER).andExpect(status().isForbidden());
    }

    @Test
    void create_withoutToken_returnsUnauthorized() throws Exception {
        postDish(validJson(UUID.randomUUID()), null).andExpect(status().isUnauthorized());
    }

    // ---------- PUT ----------

    @Test
    void update_asAdmin_withValidData_returnsOk() throws Exception {
        UUID categoryId = createCategory();
        UUID dishId = createDish(categoryId);

        putDish(dishId, updateJson(categoryId.toString(), true), ADMIN).andExpect(status().isOk());

        mockMvc.perform(get("/api/dishes/{id}", dishId))
                .andExpect(jsonPath("$.isAvailable").value(true));
    }

    @Test
    void update_asAdmin_withNonExistentId_returnsNotFound() throws Exception {
        putDish(UUID.randomUUID(), updateJson(createCategory().toString(), true), ADMIN)
                .andExpect(status().isNotFound());
    }

    @Test
    void update_asAdmin_withNonExistentCategoryId_returnsBadRequest() throws Exception {
        UUID dishId = createDish(createCategory());

        putDish(dishId, updateJson(UUID.randomUUID().toString(), true), ADMIN)
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_asRegularUser_returnsForbidden() throws Exception {
        putDish(UUID.randomUUID(), updateJson(UUID.randomUUID().toString(), true), USER)
                .andExpect(status().isForbidden());
    }

    @Test
    void update_withoutToken_returnsUnauthorized() throws Exception {
        putDish(UUID.randomUUID(), updateJson(UUID.randomUUID().toString(), true), null)
                .andExpect(status().isUnauthorized());
    }

    // ---------- DELETE ----------

    @Test
    void delete_asAdmin_withValidId_returnsOk() throws Exception {
        UUID dishId = createDish(createCategory());

        mockMvc.perform(delete("/api/dishes/{id}", dishId).with(ADMIN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/dishes/{id}", dishId)).andExpect(status().isNotFound());
    }

    @Test
    void delete_asAdmin_withNonExistentId_returnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/dishes/{id}", UUID.randomUUID()).with(ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_asRegularUser_returnsForbidden() throws Exception {
        mockMvc.perform(delete("/api/dishes/{id}", UUID.randomUUID()).with(USER))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/dishes/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- GET ----------

    @Test
    void getAll_returnsOk() throws Exception {
        mockMvc.perform(get("/api/dishes")).andExpect(status().isOk());
    }

    @Test
    void getAll_afterCreate_containsNewDish() throws Exception {
        UUID dishId = createDish(createCategory());

        mockMvc.perform(get("/api/dishes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + dishId + "')]").exists());
    }

    @Test
    void getById_withNonExistentId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/dishes/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void getByCategory_withValidCategoryId_returnsOk() throws Exception {
        UUID categoryId = createCategory();
        UUID dishId = createDish(categoryId);

        mockMvc.perform(get("/api/dishes/by-category/{id}", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(dishId.toString()));
    }

    @Test
    void getByCategory_withNonExistentCategoryId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/dishes/by-category/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
