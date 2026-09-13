package org.example.repo;

import org.example.domain.Product;
import org.example.domain.SizeSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SizeSetRepository extends JpaRepository<SizeSet, Long> {
    List<SizeSet> findByProductOrderByName(Product product);

    List<SizeSet> findByTemplateId(Long templateId);
}
