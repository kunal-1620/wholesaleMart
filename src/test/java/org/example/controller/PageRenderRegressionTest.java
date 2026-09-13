package org.example.controller;

import org.example.domain.Business;
import org.example.domain.Category;
import org.example.domain.CustomerOrder;
import org.example.domain.Product;
import org.example.domain.Role;
import org.example.domain.UserAccount;
import org.example.repo.BusinessRepository;
import org.example.repo.CategoryRepository;
import org.example.repo.CustomerOrderRepository;
import org.example.repo.ProductRepository;
import org.example.repo.UserAccountRepository;
import org.example.session.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:page-render-regression;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false",
        "app.seed-demo-data=false",
        "app.bootstrap.platform-admin-phone=9000000000",
        "app.bootstrap.platform-admin-pin=0000"
})
@AutoConfigureMockMvc
class PageRenderRegressionTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BusinessRepository businesses;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private CustomerOrderRepository orders;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ProductRepository products;

    @Test
    void recentlyTouchedCreatedDatePagesRenderDateOnly() throws Exception {
        Business business = business();
        UserAccount customer = user(business, "Render Customer", "8100000010", Role.CUSTOMER);
        UserAccount admin = user(business, "Render Admin", "8100000011", Role.BUSINESS_ADMIN);
        CustomerOrder order = order(business, customer);
        Product product = product(business);

        MockHttpSession customerSession = session(customer);
        MockHttpSession adminSession = session(admin);

        assertCustomerTopNavLinksToOrders("/b/" + business.getSlug() + "/shop", business, customerSession);
        assertCustomerTopNavLinksToOrders("/b/" + business.getSlug() + "/cart", business, customerSession);
        assertDateOnlyPage("/b/" + business.getSlug() + "/orders", customerSession);
        assertDateOnlyPage("/b/" + business.getSlug() + "/admin/orders", adminSession);
        assertDateOnlyPage("/b/" + business.getSlug() + "/admin/customers/" + customer.getId() + "/activity", adminSession);
        assertDateOnlyPage("/b/" + business.getSlug() + "/admin/analytics", adminSession);
        assertDateOnlyPage("/b/" + business.getSlug() + "/admin/products", adminSession);
        assertDateOnlyPage("/b/" + business.getSlug() + "/admin/products/" + product.getId() + "/edit", adminSession);

        mockMvc.perform(get("/b/" + business.getSlug() + "/orders/" + order.getId()).session(customerSession))
                .andExpect(status().isOk());
    }

    private void assertCustomerTopNavLinksToOrders(String path, Business business, MockHttpSession session) throws Exception {
        mockMvc.perform(get(path).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/b/" + business.getSlug() + "/orders")));
    }

    private void assertDateOnlyPage(String path, MockHttpSession session) throws Exception {
        String today = LocalDate.now().toString();
        mockMvc.perform(get(path).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(today)))
                .andExpect(content().string(not(containsString(today + "T"))));
    }

    private Business business() {
        String slug = "page-render-" + System.nanoTime();
        Business business = new Business();
        business.setName("Page Render Business");
        business.setSlug(slug);
        business.setLogoPath("/placeholder-logo.svg");
        business.setPaymentInstructions("Manual payment");
        return businesses.save(business);
    }

    private UserAccount user(Business business, String name, String phone, Role role) {
        UserAccount user = new UserAccount();
        user.setBusiness(business);
        user.setName(name);
        user.setPhone(phone);
        user.setPinHash("not-used");
        user.setRole(role);
        user.setTier(1);
        user.setActive(true);
        return users.save(user);
    }

    private CustomerOrder order(Business business, UserAccount customer) {
        CustomerOrder order = new CustomerOrder();
        order.setBusiness(business);
        order.setCustomer(customer);
        order.setTotalAmount(new BigDecimal("123.45"));
        return orders.save(order);
    }

    private Product product(Business business) {
        Category category = new Category();
        category.setBusiness(business);
        category.setName("Shirts");
        category = categories.save(category);

        Product product = new Product();
        product.setBusiness(business);
        product.setCategory(category);
        product.setProductCode("P0001");
        product.setName("Render Test Product");
        product.setDescription("Test product");
        product.setPrice(new BigDecimal("100.00"));
        product.setMinimumTierRequired(1);
        product.setActive(false);
        product.setCreatedAt(LocalDateTime.now());
        return products.save(product);
    }

    private MockHttpSession session(UserAccount user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", new CurrentUser(
                user.getId(),
                user.getBusiness().getId(),
                user.getBusiness().getSlug(),
                user.getName(),
                user.getRole(),
                user.getTier()
        ));
        return session;
    }
}
