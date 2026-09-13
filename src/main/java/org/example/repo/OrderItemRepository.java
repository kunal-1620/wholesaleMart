package org.example.repo;

import org.example.domain.CustomerOrder;
import org.example.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrder(CustomerOrder order);

    boolean existsByProductColorId(Long productColorId);
}
