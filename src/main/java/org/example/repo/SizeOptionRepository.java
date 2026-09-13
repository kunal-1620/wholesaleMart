package org.example.repo;

import org.example.domain.Business;
import org.example.domain.SizeOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SizeOptionRepository extends JpaRepository<SizeOption, Long> {
    List<SizeOption> findByBusinessOrderByLabel(Business business);

    Optional<SizeOption> findByBusinessAndLabelIgnoreCase(Business business, String label);
}
