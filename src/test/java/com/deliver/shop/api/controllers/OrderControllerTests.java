package com.deliver.shop.api.controllers;

import com.deliver.shop.TestcontainersConfiguration;
import com.deliver.shop.application.interfaces.repository.CategoryRepository;
import com.deliver.shop.application.interfaces.repository.DishRepository;
import com.deliver.shop.application.interfaces.repository.OrderRepository;
import com.deliver.shop.application.interfaces.repository.UserRepository;
import com.deliver.shop.application.services.PasswordHasher;
import com.deliver.shop.support.ApiTestClient;
import com.deliver.shop.support.FakeNotificationChannel;
import com.deliver.shop.support.FakeNotificationConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static com.deliver.shop.support.ApiTestClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FakeNotificationConfiguration.class})
class OrderControllerTests {

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
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private FakeNotificationChannel fakeChannel;

    private ApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(mockMvc, userRepository, passwordHasher, categoryRepository, dishRepository);
    }

    // ---------- helpers ----------

    private ResultActions run(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        if (json != null) {
            request = request.contentType(MediaType.APPLICATION_JSON)
                    .characterEncoding(StandardCharsets.UTF_8).content(json);
        }
        if (token != null) request = request.header("Authorization", bearer(token));
        return mockMvc.perform(request);
    }

    private static String item(Object dishId, int quantity) {
        return "{\"dishId\":" + (dishId == null ? "null" : "\"" + dishId + "\"") + ",\"quantity\":" + quantity + "}";
    }

    private static String orderJson(Object addressId, String... items) {
        return "{\"addressId\":" + (addressId == null ? "null" : "\"" + addressId + "\"")
                + ",\"items\":[" + String.join(",", items) + "]}";
    }

    private static String statusJson(String status) {
        return "{\"status\":\"" + status + "\"}";
    }

    private UUID createOrder(String token, UUID addressId, UUID dishId) throws Exception {
        String body = run(post("/api/orders"), token, orderJson(addressId, item(dishId, 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return UUID.fromString(body.replace("\"", ""));
    }

    // ---------- POST ----------

    @Test
    void create_withValidData_returnsCreated() throws Exception {
        var session = api.registerAndLogin();
        UUID address = api.createAddress(session.token());
        UUID dish = api.createDish(true, "199.00");

        run(post("/api/orders"), session.token(), orderJson(address, item(dish, 2)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$").isNotEmpty());
    }

    @Test
    void create_calculatesTotalPriceCorrectly() throws Exception {
        var session = api.registerAndLogin();
        UUID address = api.createAddress(session.token());
        UUID cheap = api.createDish(true, "100.00");
        UUID pricey = api.createDish(true, "250.50");

        String body = run(post("/api/orders"), session.token(),
                orderJson(address, item(cheap, 2), item(pricey, 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        UUID orderId = UUID.fromString(body.replace("\"", ""));

        // 100.00 * 2 + 250.50 * 1 = 450.50
        BigDecimal total = orderRepository.findById(orderId).orElseThrow().getTotalPrice();
        assertThat(total).isEqualByComparingTo("450.50");
    }

    @Test
    void create_withNullAddressId_returnsBadRequest() throws Exception {
        var session = api.registerAndLogin();
        UUID dish = api.createDish(true, "100.00");

        run(post("/api/orders"), session.token(), orderJson(null, item(dish, 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withEmptyItems_returnsBadRequest() throws Exception {
        var session = api.registerAndLogin();
        UUID address = api.createAddress(session.token());

        run(post("/api/orders"), session.token(), orderJson(address))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withNullDishIdInItem_returnsBadRequest() throws Exception {
        var session = api.registerAndLogin();
        UUID address = api.createAddress(session.token());

        run(post("/api/orders"), session.token(), orderJson(address, item(null, 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withZeroQuantity_returnsBadRequest() throws Exception {
        var session = api.registerAndLogin();
        UUID address = api.createAddress(session.token());
        UUID dish = api.createDish(true, "100.00");

        run(post("/api/orders"), session.token(), orderJson(address, item(dish, 0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withNegativeQuantity_returnsBadRequest() throws Exception {
        var session = api.registerAndLogin();
        UUID address = api.createAddress(session.token());
        UUID dish = api.createDish(true, "100.00");

        run(post("/api/orders"), session.token(), orderJson(address, item(dish, -3)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withNonExistentDishId_returnsBadRequest() throws Exception {
        var session = api.registerAndLogin();
        UUID address = api.createAddress(session.token());

        run(post("/api/orders"), session.token(), orderJson(address, item(UUID.randomUUID(), 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withUnavailableDish_returnsBadRequest() throws Exception {
        var session = api.registerAndLogin();
        UUID address = api.createAddress(session.token());
        UUID dish = api.createDish(false, "100.00");

        run(post("/api/orders"), session.token(), orderJson(address, item(dish, 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withNonExistentAddressId_returnsBadRequest() throws Exception {
        var session = api.registerAndLogin();
        UUID dish = api.createDish(true, "100.00");

        run(post("/api/orders"), session.token(), orderJson(UUID.randomUUID(), item(dish, 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withOtherUsersAddressId_returnsBadRequest() throws Exception {
        var owner = api.registerAndLogin();
        var stranger = api.registerAndLogin();
        UUID ownersAddress = api.createAddress(owner.token());
        UUID dish = api.createDish(true, "100.00");

        run(post("/api/orders"), stranger.token(), orderJson(ownersAddress, item(dish, 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withoutToken_returnsUnauthorized() throws Exception {
        run(post("/api/orders"), null, orderJson(UUID.randomUUID(), item(UUID.randomUUID(), 1)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- GET ----------

    @Test
    void getMyOrders_returnsOnlyOwnOrders() throws Exception {
        var first = api.registerAndLogin();
        var second = api.registerAndLogin();
        UUID dish = api.createDish(true, "100.00");
        UUID firstOrder = createOrder(first.token(), api.createAddress(first.token()), dish);
        UUID secondOrder = createOrder(second.token(), api.createAddress(second.token()), dish);

        run(get("/api/orders"), first.token(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.orderId == '" + firstOrder + "')]").exists())
                .andExpect(jsonPath("$[?(@.orderId == '" + secondOrder + "')]").doesNotExist());
    }

    @Test
    void getMyOrders_withoutToken_returnsUnauthorized() throws Exception {
        run(get("/api/orders"), null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void getById_withValidId_returnsOk() throws Exception {
        var session = api.registerAndLogin();
        UUID dish = api.createDish(true, "100.00");
        UUID orderId = createOrder(session.token(), api.createAddress(session.token()), dish);

        run(get("/api/orders/{id}", orderId), session.token(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.items[0].dishId").value(dish.toString()));
    }

    @Test
    void getById_withNonExistentId_returnsNotFound() throws Exception {
        var session = api.registerAndLogin();

        run(get("/api/orders/{id}", UUID.randomUUID()), session.token(), null)
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_otherUsersOrder_returnsNotFound() throws Exception {
        var owner = api.registerAndLogin();
        var stranger = api.registerAndLogin();
        UUID dish = api.createDish(true, "100.00");
        UUID orderId = createOrder(owner.token(), api.createAddress(owner.token()), dish);

        run(get("/api/orders/{id}", orderId), stranger.token(), null).andExpect(status().isNotFound());
    }

    @Test
    void getById_withoutToken_returnsUnauthorized() throws Exception {
        run(get("/api/orders/{id}", UUID.randomUUID()), null, null).andExpect(status().isUnauthorized());
    }

    // ---------- PATCH status ----------

    @Test
    void updateStatus_asAdmin_withValidData_returnsOk_andNotifiesUser() throws Exception {
        var session = api.registerAndLogin();
        UUID dish = api.createDish(true, "100.00");
        UUID orderId = createOrder(session.token(), api.createAddress(session.token()), dish);
        String adminToken = api.adminToken();

        run(patch("/api/orders/{id}/status", orderId), adminToken, statusJson("COOKING"))
                .andExpect(status().isOk());

        run(get("/api/orders/{id}", orderId), session.token(), null)
                .andExpect(jsonPath("$.status").value("COOKING"));

        // уведомление уходит асинхронно, поэтому ждём
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(fakeChannel.sent()).anyMatch(s ->
                        s.userId().equals(session.userId()) && s.message().contains("COOKING")));
    }

    @Test
    void updateStatus_asAdmin_withNonExistentId_returnsNotFound() throws Exception {
        String adminToken = api.adminToken();

        run(patch("/api/orders/{id}/status", UUID.randomUUID()), adminToken, statusJson("COOKING"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStatus_asAdmin_withInvalidStatus_returnsBadRequest() throws Exception {
        var session = api.registerAndLogin();
        UUID dish = api.createDish(true, "100.00");
        UUID orderId = createOrder(session.token(), api.createAddress(session.token()), dish);
        String adminToken = api.adminToken();

        run(patch("/api/orders/{id}/status", orderId), adminToken, statusJson("NO_SUCH_STATUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_asRegularUser_returnsForbidden() throws Exception {
        var session = api.registerAndLogin();

        run(patch("/api/orders/{id}/status", UUID.randomUUID()), session.token(), statusJson("COOKING"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_withoutToken_returnsUnauthorized() throws Exception {
        run(patch("/api/orders/{id}/status", UUID.randomUUID()), null, statusJson("COOKING"))
                .andExpect(status().isUnauthorized());
    }
}
