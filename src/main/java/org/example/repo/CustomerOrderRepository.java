package org.example.repo;

import org.example.domain.Business;
import org.example.domain.CustomerOrder;
import org.example.domain.OrderStatus;
import org.example.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    List<CustomerOrder> findByBusinessOrderByCreatedAtDesc(Business business);

    List<CustomerOrder> findByCustomerOrderByCreatedAtDesc(UserAccount customer);

    @Query("""
            select customerOrder from CustomerOrder customerOrder
            where customerOrder.business = :business
              and (:status is null or customerOrder.status = :status)
              and (:customerId is null or customerOrder.customer.id = :customerId)
              and (:createdFrom is null or customerOrder.createdAt >= :createdFrom)
              and (:createdTo is null or customerOrder.createdAt < :createdTo)
            order by customerOrder.createdAt desc
            """)
    List<CustomerOrder> findForBusiness(
            @Param("business") Business business,
            @Param("status") OrderStatus status,
            @Param("customerId") Long customerId,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo
    );

    @Query("""
            select customerOrder from CustomerOrder customerOrder
            where customerOrder.customer = :customer
              and (:status is null or customerOrder.status = :status)
              and (:createdFrom is null or customerOrder.createdAt >= :createdFrom)
              and (:createdTo is null or customerOrder.createdAt < :createdTo)
            order by customerOrder.createdAt desc
            """)
    List<CustomerOrder> findForCustomer(
            @Param("customer") UserAccount customer,
            @Param("status") OrderStatus status,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo
    );

    Optional<CustomerOrder> findByPaymentProofPath(String paymentProofPath);

    long countByBusinessAndStatusIn(Business business, Collection<OrderStatus> statuses);
}
