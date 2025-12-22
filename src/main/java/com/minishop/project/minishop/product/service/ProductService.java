package com.minishop.project.minishop.product.service;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.inventory.service.InventoryService;
import com.minishop.project.minishop.product.domain.Product;
import com.minishop.project.minishop.product.domain.ProductStatus;
import com.minishop.project.minishop.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    @Transactional
    public Product createProduct(String name, String description, Long unitPrice) {
        validateProductInput(name, description, unitPrice);

        Product product = Product.create(name, description, unitPrice);
        Product savedProduct = productRepository.save(product);

        inventoryService.initializeInventory(savedProduct.getId());

        return savedProduct;
    }

    @Transactional(readOnly = true)
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Product> getActiveProducts() {
        return productRepository.findByStatus(ProductStatus.ACTIVE);
    }

    @Transactional
    public Product updateProduct(Long id, String name, String description, Long unitPrice) {
        validateProductInput(name, description, unitPrice);

        Product product = getProductById(id);
        product.updateInfo(name, description, unitPrice);
        return productRepository.save(product);
    }

    @Transactional
    public Product deactivateProduct(Long id) {
        Product product = getProductById(id);
        product.deactivate();
        return productRepository.save(product);
    }

    private void validateProductInput(String name, String description, Long unitPrice) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Product name is required");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Product description is required");
        }
        if (unitPrice == null || unitPrice <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Unit price must be positive");
        }
    }
}
