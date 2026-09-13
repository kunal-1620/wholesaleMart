package org.example.repo;

import org.example.domain.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StoredFileRepository extends JpaRepository<StoredFile, String> {
    @Query("select coalesce(sum(file.sizeBytes), 0) from StoredFile file")
    long totalSizeBytes();
}
