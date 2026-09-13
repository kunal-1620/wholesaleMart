package org.example.repo;

import org.example.domain.InventoryStock;
import org.example.domain.ProductColor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface InventoryStockRepository extends JpaRepository<InventoryStock, Long> {
    List<InventoryStock> findByProductColorOrderBySizeLabel(ProductColor productColor);

    Optional<InventoryStock> findByProductColorAndSizeLabel(ProductColor productColor, String sizeLabel);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select stock from InventoryStock stock where stock.productColor.id = :productColorId")
    List<InventoryStock> findLockedByProductColorId(@Param("productColorId") Long productColorId);
}
