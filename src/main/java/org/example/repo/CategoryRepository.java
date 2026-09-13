package org.example.repo;

import org.example.domain.Business;
import org.example.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByBusinessOrderByName(Business business);

    Optional<Category> findByBusinessAndNameIgnoreCase(Business business, String name);
}
