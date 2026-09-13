package org.example.repo;

import org.example.domain.Business;
import org.example.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByBusinessOrderByName(Business business);

    List<Product> findByBusinessOrderByProductCodeAscNameAsc(Business business);

    List<Product> findByBusinessOrderById(Business business);

    Optional<Product> findByBusinessAndProductCodeIgnoreCase(Business business, String productCode);

    List<Product> findByBusinessAndActiveTrueAndMinimumTierRequiredGreaterThanEqualOrderByCreatedAtDescIdDesc(Business business, int tier);

    List<Product> findByBusinessAndActiveTrueAndMinimumTierRequiredGreaterThanEqualOrderByName(Business business, int tier);
}
