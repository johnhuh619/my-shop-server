package com.minishop.project.minishop.product.service;

import com.minishop.project.minishop.auth.service.TokenBlacklistService;
import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.inventory.domain.Inventory;
import com.minishop.project.minishop.inventory.repository.InventoryRepository;
import com.minishop.project.minishop.product.domain.Product;
import com.minishop.project.minishop.product.domain.ProductStatus;
import com.minishop.project.minishop.product.dto.ProductWithStock;
import com.minishop.project.minishop.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class ProductServiceTest {

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    private Product createAndSaveProduct(String name, long unitPrice) {
        Product product = Product.create(name, "설명", unitPrice);
        product = productRepository.save(product);
        Inventory inventory = Inventory.create(product.getId(), 0L);
        inventoryRepository.save(inventory);
        return product;
    }

    private void addStock(Long productId, long quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow();
        inventory.addStock(quantity);
        inventoryRepository.save(inventory);
    }

    @Test
    void getActiveProductWithStock_ACTIVE상품_재고포함조회() {
        Product product = createAndSaveProduct("테스트 상품", 10000L);
        addStock(product.getId(), 50L);

        ProductWithStock result = productService.getActiveProductWithStock(product.getId());

        assertThat(result.product().getId()).isEqualTo(product.getId());
        assertThat(result.quantityAvailable()).isEqualTo(50L);
    }

    @Test
    void getActiveProductWithStock_INACTIVE상품_예외발생() {
        Product product = createAndSaveProduct("비활성 상품", 10000L);
        product.deactivate();
        productRepository.save(product);

        assertThatThrownBy(() -> productService.getActiveProductWithStock(product.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getActiveProductsWithStock_페이징_정상동작() {
        for (int i = 0; i < 5; i++) {
            createAndSaveProduct("상품" + i, 1000L * (i + 1));
        }

        PageRequest pageable = PageRequest.of(0, 3, Sort.by("createdAt").descending());
        Page<ProductWithStock> result = productService.getActiveProductsWithStock(null, pageable);

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void getActiveProductsWithStock_INACTIVE제외_ACTIVE만반환() {
        Product active = createAndSaveProduct("활성 상품", 10000L);
        Product inactive = createAndSaveProduct("비활성 상품", 20000L);
        inactive.deactivate();
        productRepository.save(inactive);

        PageRequest pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
        Page<ProductWithStock> result = productService.getActiveProductsWithStock(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).product().getId()).isEqualTo(active.getId());
    }

    @Test
    void getActiveProductsWithStock_키워드검색_동작확인() {
        createAndSaveProduct("사과 주스", 5000L);
        createAndSaveProduct("오렌지 주스", 6000L);
        createAndSaveProduct("초콜릿", 3000L);

        PageRequest pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
        Page<ProductWithStock> result = productService.getActiveProductsWithStock("주스", pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(pws -> pws.product().getName())
                .allMatch(name -> name.contains("주스"));
    }

    @Test
    void getActiveProductsWithStock_재고정보_상품별매핑확인() {
        Product product1 = createAndSaveProduct("상품A", 10000L);
        Product product2 = createAndSaveProduct("상품B", 20000L);
        addStock(product1.getId(), 100L);
        addStock(product2.getId(), 200L);

        PageRequest pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
        Page<ProductWithStock> result = productService.getActiveProductsWithStock(null, pageable);

        assertThat(result.getContent()).hasSize(2);
        for (ProductWithStock pws : result.getContent()) {
            if (pws.product().getName().equals("상품A")) {
                assertThat(pws.quantityAvailable()).isEqualTo(100L);
            } else {
                assertThat(pws.quantityAvailable()).isEqualTo(200L);
            }
        }
    }
}
