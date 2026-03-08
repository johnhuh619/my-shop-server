package com.minishop.project.minishop.product.controller;

import com.jayway.jsonpath.JsonPath;
import com.minishop.project.minishop.auth.service.TokenBlacklistService;
import com.minishop.project.minishop.inventory.service.InventoryService;
import com.minishop.project.minishop.product.domain.Product;
import com.minishop.project.minishop.product.domain.ProductStatus;
import com.minishop.project.minishop.product.repository.ProductRepository;
import com.minishop.project.minishop.user.domain.User;
import com.minishop.project.minishop.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AdminProductControllerTest {

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

        adminAccessToken = loginAndGetAccessToken(admin.getEmail(), "admin-password");
        customerAccessToken = loginAndGetAccessToken(customer.getEmail(), "customer-password");
    }

    @Test
    void getProducts_ADMIN요청_INACTIVE필터_비활성상품만반환() throws Exception {
        Product inactive = createProduct("inactive-product", 10000L, ProductStatus.INACTIVE);
        inventoryService.addStock(inactive.getId(), 15L);
        createProduct("active-product", 12000L, ProductStatus.ACTIVE);

        mockMvc.perform(get("/api/admin/products")
                        .param("status", "INACTIVE")
                        .param("keyword", "inactive")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(inactive.getId()))
                .andExpect(jsonPath("$.data.content[0].status").value(ProductStatus.INACTIVE.name()))
                .andExpect(jsonPath("$.data.content[0].quantityAvailable").value(15));
    }

    @Test
    void getProducts_ADMIN요청_ALL필터_활성비활성모두반환() throws Exception {
        createProduct("active-product", 12000L, ProductStatus.ACTIVE);
        createProduct("inactive-product", 10000L, ProductStatus.INACTIVE);

        mockMvc.perform(get("/api/admin/products")
                        .param("status", "ALL")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    void activateProduct_ADMIN요청_INACTIVE상품을ACTIVE로변경() throws Exception {
        Product inactive = createProduct("inactive-product", 10000L, ProductStatus.INACTIVE);

        mockMvc.perform(post("/api/admin/products/{id}/activate", inactive.getId())
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(inactive.getId()))
                .andExpect(jsonPath("$.data.status").value(ProductStatus.ACTIVE.name()));

        Product reloaded = productRepository.findById(inactive.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void activateProduct_CUSTOMER요청_Forbidden() throws Exception {
        Product inactive = createProduct("inactive-product", 10000L, ProductStatus.INACTIVE);

        mockMvc.perform(post("/api/admin/products/{id}/activate", inactive.getId())
                        .header("Authorization", "Bearer " + customerAccessToken))
                .andExpect(status().isForbidden());
    }

    private Product createProduct(String name, Long unitPrice, ProductStatus status) {
        Product product = Product.builder()
                .name(name)
                .description("description")
                .unitPrice(unitPrice)
                .status(status)
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
