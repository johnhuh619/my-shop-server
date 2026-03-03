package com.minishop.project.minishop.order.controller;

import com.jayway.jsonpath.JsonPath;
import com.minishop.project.minishop.auth.service.TokenBlacklistService;
import com.minishop.project.minishop.inventory.service.InventoryService;
import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderStatus;
import com.minishop.project.minishop.order.dto.OrderItemRequest;
import com.minishop.project.minishop.order.service.OrderService;
import com.minishop.project.minishop.product.domain.Product;
import com.minishop.project.minishop.product.domain.ProductStatus;
import com.minishop.project.minishop.product.repository.ProductRepository;
import com.minishop.project.minishop.user.domain.User;
import com.minishop.project.minishop.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminOrderControllerTest {

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private OrderService orderService;

    private Long customerUserId;
    private String adminAccessToken;
    private String customerAccessToken;

    @BeforeEach
    void setUp() throws Exception {
        User admin = userRepository.save(User.createAdmin(
                "admin@test.com",
                passwordEncoder.encode("admin-password"),
                "Admin"
        ));
        User customer = userRepository.save(User.create(
                "customer@test.com",
                passwordEncoder.encode("customer-password"),
                "Customer"
        ));

        customerUserId = customer.getId();
        adminAccessToken = loginAndGetAccessToken(admin.getEmail(), "admin-password");
        customerAccessToken = loginAndGetAccessToken(customer.getEmail(), "customer-password");
    }

    @Test
    void completeOrder_ADMIN호출_PAID주문_COMPLETED전이() throws Exception {
        Order paidOrder = createPaidOrder(customerUserId);

        mockMvc.perform(post("/api/admin/orders/{id}/complete", paidOrder.getId())
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(paidOrder.getId()))
                .andExpect(jsonPath("$.data.status").value(OrderStatus.COMPLETED.name()));

        Order completed = orderService.getOrderById(paidOrder.getId());
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void completeOrder_ADMIN호출_이미COMPLETED_멱등성성공() throws Exception {
        Order paidOrder = createPaidOrder(customerUserId);
        orderService.completeOrder(paidOrder.getId());

        mockMvc.perform(post("/api/admin/orders/{id}/complete", paidOrder.getId())
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value(OrderStatus.COMPLETED.name()));
    }

    @Test
    void completeOrder_ADMIN호출_CREATED주문_상태에러() throws Exception {
        Order createdOrder = createCreatedOrder(customerUserId);

        mockMvc.perform(post("/api/admin/orders/{id}/complete", createdOrder.getId())
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("O002"));
    }

    @Test
    void completeOrder_CUSTOMER호출_권한없음() throws Exception {
        Order paidOrder = createPaidOrder(customerUserId);

        mockMvc.perform(post("/api/admin/orders/{id}/complete", paidOrder.getId())
                        .header("Authorization", "Bearer " + customerAccessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void completeOrder_미인증요청_인증오류() throws Exception {
        Order paidOrder = createPaidOrder(customerUserId);

        mockMvc.perform(post("/api/admin/orders/{id}/complete", paidOrder.getId()))
                .andExpect(status().isForbidden());
    }

    private Order createCreatedOrder(Long userId) {
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        return orderService.createOrder(userId, List.of(new OrderItemRequest(product.getId(), 2L)),
                "홍길동", "010-1234-5678", "서울시 강남구", null, "06000");
    }

    private Order createPaidOrder(Long userId) {
        Order order = createCreatedOrder(userId);
        return orderService.markAsPaid(order.getId());
    }

    private Product createProduct(String name, Long unitPrice) {
        Product product = Product.builder()
                .name(name)
                .description("description")
                .unitPrice(unitPrice)
                .status(ProductStatus.ACTIVE)
                .build();
        Product saved = productRepository.save(product);
        inventoryService.initializeInventory(saved.getId());
        return saved;
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        String requestBody = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        return JsonPath.read(responseJson, "$.data.accessToken");
    }
}
