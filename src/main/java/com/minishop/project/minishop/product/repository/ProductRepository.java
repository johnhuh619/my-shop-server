package com.minishop.project.minishop.product.repository;

import com.minishop.project.minishop.product.domain.Product;
import com.minishop.project.minishop.product.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByStatus(ProductStatus status);

    Optional<Product> findByIdAndStatus(Long id, ProductStatus status);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findByStatusAndNameContainingIgnoreCase(ProductStatus status, String name, Pageable pageable);
}
