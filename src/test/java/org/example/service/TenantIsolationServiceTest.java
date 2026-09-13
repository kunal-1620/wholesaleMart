package org.example.service;

import org.example.domain.Business;
import org.example.domain.CustomerOrder;
import org.example.domain.Role;
import org.example.domain.UserAccount;
import org.example.repo.BusinessRepository;
import org.example.repo.CustomerOrderRepository;
import org.example.repo.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tenant-isolation;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false",
        "app.seed-demo-data=false",
        "app.bootstrap.platform-admin-phone=9000000000",
        "app.bootstrap.platform-admin-pin=0000"
})
class TenantIsolationServiceTest {
    @Autowired
    private BusinessRepository businesses;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private CustomerOrderRepository orders;

    @Autowired
    private OrderService orderService;

    @Autowired
    private AdminService adminService;

    @Test
    void rejectsCrossBusinessAndCrossCustomerOrderAccess() {
        Business businessA = business("Business A", "business-a");
        Business businessB = business("Business B", "business-b");
        UserAccount customerA = customer(businessA, "Customer A", "8100000001");
        UserAccount customerB = customer(businessB, "Customer B", "8100000002");

        CustomerOrder order = new CustomerOrder();
        order.setBusiness(businessA);
        order.setCustomer(customerA);
        order.setTotalAmount(BigDecimal.TEN);
        CustomerOrder savedOrder = orders.save(order);

        assertThrows(IllegalArgumentException.class, () -> orderService.orderForBusiness(businessB, savedOrder.getId()));
        assertThrows(IllegalArgumentException.class, () -> orderService.approve(businessB, savedOrder.getId()));
        assertThrows(IllegalArgumentException.class, () -> orderService.orderForCustomer(savedOrder.getId(), customerB.getId()));
        assertThrows(IllegalArgumentException.class, () -> orderService.submitPaymentProof(savedOrder.getId(), customerB.getId(), "txn", null));
    }

    @Test
    void rejectsCrossBusinessCustomerEdits() {
        Business businessA = business("Business C", "business-c");
        Business businessB = business("Business D", "business-d");
        UserAccount customerA = customer(businessA, "Customer C", "8100000003");

        assertThrows(IllegalArgumentException.class, () -> adminService.customer(businessB, customerA.getId()));
        assertThrows(IllegalArgumentException.class, () -> adminService.saveCustomer(
                businessB,
                customerA.getId(),
                "Moved Customer",
                "Moved Company",
                "8100000004",
                "1234",
                1,
                true,
                "Address"
        ));
    }

    private Business business(String name, String slug) {
        Business business = new Business();
        business.setName(name);
        business.setSlug(slug);
        business.setLogoPath("/placeholder-logo.svg");
        business.setPaymentInstructions("Manual payment");
        return businesses.save(business);
    }

    private UserAccount customer(Business business, String name, String phone) {
        UserAccount customer = new UserAccount();
        customer.setBusiness(business);
        customer.setName(name);
        customer.setPhone(phone);
        customer.setPinHash("not-used");
        customer.setRole(Role.CUSTOMER);
        customer.setTier(1);
        customer.setActive(true);
        return users.save(customer);
    }
}
