package org.example.repo;

import org.example.domain.Product;
import org.example.domain.ProductColor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductColorRepository extends JpaRepository<ProductColor, Long> {
    List<ProductColor> findByProductOrderByName(Product product);
}
