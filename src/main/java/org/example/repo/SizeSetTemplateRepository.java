package org.example.repo;

import org.example.domain.Business;
import org.example.domain.SizeSetTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SizeSetTemplateRepository extends JpaRepository<SizeSetTemplate, Long> {
    List<SizeSetTemplate> findByBusinessOrderByName(Business business);

    Optional<SizeSetTemplate> findByBusinessAndNameIgnoreCase(Business business, String name);
}
