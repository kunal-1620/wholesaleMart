package org.example.repo;

import org.example.domain.Business;
import org.example.domain.CustomerOrder;
import org.example.domain.OrderStatus;
import org.example.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    List<CustomerOrder> findByBusinessOrderByCreatedAtDesc(Business business);

    List<CustomerOrder> findByCustomerOrderByCreatedAtDesc(UserAccount customer);

    Optional<CustomerOrder> findByPaymentProofPath(String paymentProofPath);

    long countByBusinessAndStatusIn(Business business, Collection<OrderStatus> statuses);
}
