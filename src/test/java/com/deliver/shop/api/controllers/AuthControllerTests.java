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

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static com.deliver.shop.support.ApiTestClient.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerTests {

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

    private ResultActions send(String url, String json, String token) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8).content(json);
        if (token != null) request = request.header("Authorization", bearer(token));
        return mockMvc.perform(request);
    }

    private ResultActions sendPatch(String url, String json, String token) throws Exception {
        var request = patch(url).contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8).content(json);
        if (token != null) request = request.header("Authorization", bearer(token));
        return mockMvc.perform(request);
    }

    private static String changePasswordJson(String current, String next) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
    }

    private static String profileJson(String firstName, String phone) {
        return "{\"firstName\":\"" + firstName + "\",\"lastName\":\"Петров\",\"phone\":\"" + phone + "\"}";
    }

    // ---------- register ----------

    @Test
    void register_withValidData_returnsUser() throws Exception {
        String email = uniqueEmail();

        send("/api/auth/register", registerJson(email, uniquePhone(), PASSWORD, "Иван"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    static Stream<Arguments> invalidRegistrations() {
        return Stream.of(
                Arguments.of("not-an-email", null, null, null),
                Arguments.of(null, "12345", null, null),
                Arguments.of(null, null, "weakpass", null),
                Arguments.of(null, null, null, "John"));
    }

    @ParameterizedTest
    @MethodSource("invalidRegistrations")
    void register_withInvalidData_returnsBadRequest(String email, String phone, String password, String firstName)
            throws Exception {
        String json = registerJson(
                email != null ? email : uniqueEmail(),
                phone != null ? phone : uniquePhone(),
                password != null ? password : PASSWORD,
                firstName != null ? firstName : "Иван");

        send("/api/auth/register", json, null).andExpect(status().isBadRequest());
    }

    @Test
    void register_withDuplicateEmail_returnsBadRequest() throws Exception {
        String email = uniqueEmail();
        send("/api/auth/register", registerJson(email, uniquePhone(), PASSWORD, "Иван"), null)
                .andExpect(status().isOk());

        send("/api/auth/register", registerJson(email, uniquePhone(), PASSWORD, "Иван"), null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withDuplicatePhone_returnsBadRequest() throws Exception {
        String phone = uniquePhone();
        send("/api/auth/register", registerJson(uniqueEmail(), phone, PASSWORD, "Иван"), null)
                .andExpect(status().isOk());

        send("/api/auth/register", registerJson(uniqueEmail(), phone, PASSWORD, "Иван"), null)
                .andExpect(status().isBadRequest());
    }

    // ---------- login ----------

    @Test
    void login_withValidCredentials_returnsToken() throws Exception {
        var session = api.registerAndLogin();

        send("/api/auth/login", loginJson(session.email(), PASSWORD), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(session.userId().toString()));
    }

    @Test
    void login_withWrongPassword_returnsUnauthorized() throws Exception {
        var session = api.registerAndLogin();

        send("/api/auth/login", loginJson(session.email(), "Wrong1234!"), null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withNonExistentEmail_returnsUnauthorized() throws Exception {
        send("/api/auth/login", loginJson(uniqueEmail(), PASSWORD), null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void token_grantsAccessToProtectedEndpoint_andGarbageTokenDoesNot() throws Exception {
        var session = api.registerAndLogin();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/addresses")
                        .header("Authorization", bearer(session.token())))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/addresses")
                        .header("Authorization", bearer("not.a.token")))
                .andExpect(status().isUnauthorized());
    }

    // ---------- change-password ----------

    @Test
    void changePassword_withValidCredentials_returnsOk_andNewPasswordWorks() throws Exception {
        var session = api.registerAndLogin();

        send("/api/auth/change-password", changePasswordJson(PASSWORD, "NewPassw0rd!"), session.token())
                .andExpect(status().isOk());

        send("/api/auth/login", loginJson(session.email(), "NewPassw0rd!"), null).andExpect(status().isOk());
        send("/api/auth/login", loginJson(session.email(), PASSWORD), null).andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_withWrongCurrentPassword_returnsBadRequest() throws Exception {
        var session = api.registerAndLogin();

        send("/api/auth/change-password", changePasswordJson("Wrong1234!", "NewPassw0rd!"), session.token())
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_withSameNewPassword_returnsBadRequest() throws Exception {
        var session = api.registerAndLogin();

        send("/api/auth/change-password", changePasswordJson(PASSWORD, PASSWORD), session.token())
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_withoutToken_returnsUnauthorized() throws Exception {
        send("/api/auth/change-password", changePasswordJson(PASSWORD, "NewPassw0rd!"), null)
                .andExpect(status().isUnauthorized());
    }

    // ---------- profile ----------

    @Test
    void updateProfile_withValidData_returnsOk() throws Exception {
        var session = api.registerAndLogin();

        sendPatch("/api/auth/profile", profileJson("Пётр", uniquePhone()), session.token())
                .andExpect(status().isOk());
    }

    @Test
    void updateProfile_withOwnPhone_returnsOk() throws Exception {
        String email = uniqueEmail();
        String phone = uniquePhone();
        send("/api/auth/register", registerJson(email, phone, PASSWORD, "Иван"), null).andExpect(status().isOk());
        String token = api.login(email, PASSWORD);

        sendPatch("/api/auth/profile", profileJson("Пётр", phone), token).andExpect(status().isOk());
    }

    @Test
    void updateProfile_withOtherUsersPhone_returnsBadRequest() throws Exception {
        String takenPhone = uniquePhone();
        send("/api/auth/register", registerJson(uniqueEmail(), takenPhone, PASSWORD, "Иван"), null)
                .andExpect(status().isOk());
        var session = api.registerAndLogin();

        sendPatch("/api/auth/profile", profileJson("Пётр", takenPhone), session.token())
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfile_withoutToken_returnsUnauthorized() throws Exception {
        sendPatch("/api/auth/profile", profileJson("Пётр", uniquePhone()), null)
                .andExpect(status().isUnauthorized());
    }
}
