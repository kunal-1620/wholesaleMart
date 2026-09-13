package org.example.repo;

import org.example.domain.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, Long> {
    List<Business> findAllByOrderByName();

    Optional<Business> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
