package com.deliver.shop.support;

import com.deliver.shop.application.interfaces.repository.CategoryRepository;
import com.deliver.shop.application.interfaces.repository.DishRepository;
import com.deliver.shop.application.interfaces.repository.UserRepository;
import com.deliver.shop.application.services.PasswordHasher;
import com.deliver.shop.domain.entities.CategoryEntity;
import com.deliver.shop.domain.entities.DishEntity;
import com.deliver.shop.domain.entities.UserEntity;
import com.deliver.shop.domain.enums.Role;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Помощник для интеграционных тестов: регистрация, вход, создание тестовых данных. */
public class ApiTestClient {

    public static final String PASSWORD = "Passw0rd!";

    private static final String LETTERS = "абвгдежзиклмнопрстуфхцчшэюя";
    private static final Random RANDOM = new Random();

    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final CategoryRepository categoryRepository;
    private final DishRepository dishRepository;

    public ApiTestClient(MockMvc mockMvc, UserRepository userRepository, PasswordHasher passwordHasher,
                         CategoryRepository categoryRepository, DishRepository dishRepository) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.categoryRepository = categoryRepository;
        this.dishRepository = dishRepository;
    }

    public record Session(UUID userId, String email, String token) {
    }

    // ---------- уникальные данные ----------

    public static String uniqueEmail() {
        return UUID.randomUUID().toString().replace("-", "") + "@test.com";
    }

    public static String uniquePhone() {
        return "+7" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L);
    }

    public static String uniqueCategoryName() {
        StringBuilder sb = new StringBuilder("Кат");
        for (int i = 0; i < 10; i++) {
            sb.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        }
        return sb.toString();
    }

    // ---------- JSON ----------

    public static String registerJson(String email, String phone, String password, String firstName) {
        return "{\"firstName\":\"" + firstName + "\",\"lastName\":\"Иванов\",\"email\":\"" + email
                + "\",\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}";
    }

    public static String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    public static String addressJson(String city, String street, String house, String apartment, String floor) {
        return "{\"city\":\"" + city + "\",\"street\":\"" + street + "\",\"house\":\"" + house
                + "\",\"apartment\":\"" + apartment + "\",\"floor\":" + (floor == null ? "null" : "\"" + floor + "\"") + "}";
    }

    public static String validAddressJson() {
        return addressJson("Москва", "Тверская", "1", "10", "2");
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    // ---------- действия через API ----------

    public Session registerAndLogin() throws Exception {
        String email = uniqueEmail();
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content(registerJson(email, uniquePhone(), PASSWORD, "Иван")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        UUID userId = UUID.fromString(JsonPath.read(body, "$.id"));
        return new Session(userId, email, login(email, PASSWORD));
    }

    public String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.token");
    }

    /** Админа нельзя создать через API, поэтому кладём его в БД напрямую и входим как обычно. */
    public String adminToken() throws Exception {
        String email = uniqueEmail();
        PasswordHasher.HashedPassword password = passwordHasher.hash(PASSWORD);

        UserEntity admin = new UserEntity();
        admin.setFirstName("Админ");
        admin.setLastName("Админов");
        admin.setEmail(email);
        admin.setPhone(uniquePhone());
        admin.setRole(Role.ADMINISTRATOR);
        admin.setPasswordHash(password.hash());
        admin.setPasswordSalt(password.salt());
        userRepository.save(admin);

        return login(email, PASSWORD);
    }

    public UUID createAddress(String token) throws Exception {
        String body = mockMvc.perform(post("/api/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content(validAddressJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return UUID.fromString(body.replace("\"", ""));
    }

    // ---------- данные напрямую в БД ----------

    public UUID createCategory() {
        CategoryEntity category = new CategoryEntity();
        category.setName(uniqueCategoryName());
        return categoryRepository.save(category).getId();
    }

    public UUID createDish(boolean available, String price) {
        DishEntity dish = new DishEntity();
        dish.setName("Борщ");
        dish.setDescription("Описание");
        dish.setStructure("Состав");
        dish.setPrice(new BigDecimal(price));
        dish.setAvailable(available);
        dish.setCategory(categoryRepository.getReferenceById(createCategory()));
        return dishRepository.save(dish).getId();
    }
}
