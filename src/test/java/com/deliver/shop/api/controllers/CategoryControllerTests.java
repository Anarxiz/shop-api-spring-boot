package com.deliver.shop.api.controllers;

import com.deliver.shop.TestcontainersConfiguration;
import com.deliver.shop.application.interfaces.repository.CategoryRepository;
import com.deliver.shop.application.interfaces.repository.DishRepository;
import com.deliver.shop.domain.entities.CategoryEntity;
import com.deliver.shop.domain.entities.DishEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CategoryControllerTests {

    private static final String LETTERS = "абвгдежзиклмнопрстуфхцчшэюя";
    private static final Random RANDOM = new Random();

    private static final RequestPostProcessor ADMIN = user("admin").roles("ADMINISTRATOR");
    private static final RequestPostProcessor USER = user("user").roles("USER");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private DishRepository dishRepository;

    // ---------- helpers ----------

    /** Уникальное русское название: в БД стоит уникальный индекс по имени категории. */
    private static String randomName() {
        StringBuilder sb = new StringBuilder("Кат");
        for (int i = 0; i < 10; i++) {
            sb.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        }
        return sb.toString();
    }

    private static String json(String name) {
        return "{\"name\":\"" + name + "\"}";
    }

    private UUID createCategory() {
        CategoryEntity category = new CategoryEntity();
        category.setName(randomName());
        return categoryRepository.save(category).getId();
    }

    // ---------- POST ----------

    @Test
    void create_asAdmin_withValidData_returnsCreated() throws Exception {
        mockMvc.perform(post("/api/categories").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content(json(randomName())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Drinks"})
    void create_asAdmin_withInvalidData_returnsBadRequest(String name) throws Exception {
        mockMvc.perform(post("/api/categories").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content(json(name)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").isNotEmpty());
    }

    @Test
    void create_withDuplicateName_returnsBadRequest() throws Exception {
        String name = randomName();
        mockMvc.perform(post("/api/categories").with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                .content(json(name))).andExpect(status().isCreated());

        mockMvc.perform(post("/api/categories").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content(json(name)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_asRegularUser_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/categories").with(USER)
                        .contentType(MediaType.APPLICATION_JSON).content(json(randomName())))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON).content(json(randomName())))
                .andExpect(status().isUnauthorized());
    }

    // ---------- PUT ----------

    @Test
    void update_asAdmin_withValidData_returnsOk() throws Exception {
        UUID id = createCategory();

        mockMvc.perform(put("/api/categories/{id}", id).with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content(json("Десерты" + randomName().substring(3, 6))))
                .andExpect(status().isOk());
    }

    @Test
    void update_asAdmin_withNonExistentId_returnsNotFound() throws Exception {
        mockMvc.perform(put("/api/categories/{id}", UUID.randomUUID()).with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content(json(randomName())))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_asRegularUser_returnsForbidden() throws Exception {
        mockMvc.perform(put("/api/categories/{id}", UUID.randomUUID()).with(USER)
                        .contentType(MediaType.APPLICATION_JSON).content(json(randomName())))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/categories/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(json(randomName())))
                .andExpect(status().isUnauthorized());
    }

    // ---------- DELETE ----------

    @Test
    void delete_asAdmin_withValidId_returnsOk() throws Exception {
        UUID id = createCategory();

        mockMvc.perform(delete("/api/categories/{id}", id).with(ADMIN))
                .andExpect(status().isOk());
    }

    @Test
    void delete_asAdmin_withExistingDishes_returnsBadRequest() throws Exception {
        UUID id = createCategory();
        DishEntity dish = new DishEntity();
        dish.setName("Борщ");
        dish.setDescription("Описание");
        dish.setStructure("Состав");
        dish.setPrice(new BigDecimal("199.00"));
        dish.setAvailable(true);
        dish.setCategory(categoryRepository.getReferenceById(id));
        dishRepository.save(dish);

        mockMvc.perform(delete("/api/categories/{id}", id).with(ADMIN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_asAdmin_withNonExistentId_returnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/categories/{id}", UUID.randomUUID()).with(ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_asRegularUser_returnsForbidden() throws Exception {
        mockMvc.perform(delete("/api/categories/{id}", UUID.randomUUID()).with(USER))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/categories/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- GET ----------

    @Test
    void getAll_withoutToken_returnsOk() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void getAll_afterCreate_containsNewCategory() throws Exception {
        UUID id = createCategory();

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").exists());
    }

    @Test
    void getById_withExistingId_returnsCategory() throws Exception {
        UUID id = createCategory();

        mockMvc.perform(get("/api/categories/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.dishes").isArray());
    }

    @Test
    void getById_withNonExistentId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/categories/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
