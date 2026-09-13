package org.example.controller;

import org.example.domain.Business;
import org.example.domain.Category;
import org.example.domain.CustomerOrder;
import org.example.domain.InventoryStock;
import org.example.domain.OrderItem;
import org.example.domain.Product;
import org.example.domain.ProductColor;
import org.example.domain.Role;
import org.example.domain.SizeSet;
import org.example.domain.UserAccount;
import org.example.repo.BusinessRepository;
import org.example.repo.CategoryRepository;
import org.example.repo.CustomerOrderRepository;
import org.example.repo.InventoryStockRepository;
import org.example.repo.OrderItemRepository;
import org.example.repo.ProductColorRepository;
import org.example.repo.ProductRepository;
import org.example.repo.SizeSetRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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

    @Autowired
    private ProductColorRepository colors;

    @Autowired
    private InventoryStockRepository inventory;

    @Autowired
    private SizeSetRepository sizeSets;

    @Autowired
    private OrderItemRepository orderItems;

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

    @Test
    void productDeleteActionsRedirectCleanly() throws Exception {
        Business business = business();
        UserAccount customer = user(business, "Delete Customer", "8100000020", Role.CUSTOMER);
        UserAccount admin = user(business, "Delete Admin", "8100000021", Role.BUSINESS_ADMIN);
        MockHttpSession adminSession = session(admin);
        withCsrf(adminSession);

        Product plainProduct = product(business, "P0101", "Plain Delete Product");
        mockMvc.perform(get("/b/" + business.getSlug() + "/admin/products").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("aria-label=\"Delete product\""))));

        mockMvc.perform(get("/b/" + business.getSlug() + "/admin/products/" + plainProduct.getId() + "/edit").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-label=\"Delete product\"")))
                .andExpect(content().string(containsString("Delete this product?")))
                .andExpect(content().string(not(containsString("Only available because this product has no order history"))));

        mockMvc.perform(post("/b/" + business.getSlug() + "/admin/products/" + plainProduct.getId() + "/delete")
                        .param("_csrf", "test-csrf-token")
                        .session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/b/" + business.getSlug() + "/admin/products"));

        Product detailedProduct = product(business, "P0102", "Detailed Delete Product");
        ProductColor color = color(detailedProduct);
        stock(color, "38 (M)", 4);
        sizeSet(detailedProduct);
        mockMvc.perform(post("/b/" + business.getSlug() + "/admin/products/" + detailedProduct.getId() + "/delete")
                        .param("_csrf", "test-csrf-token")
                        .session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/b/" + business.getSlug() + "/admin/products"));

        Product orderedProduct = product(business, "P0103", "Ordered Product");
        ProductColor orderedColor = color(orderedProduct);
        CustomerOrder order = order(business, customer);
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductColorId(orderedColor.getId());
        item.setProductCode(orderedProduct.getProductCode());
        item.setProductName(orderedProduct.getName());
        item.setColorName(orderedColor.getName());
        item.setSizeSetName("Set A");
        item.setSizeLabels("38 (M)");
        item.setQuantity(1);
        item.setPriceEach(new BigDecimal("100.00"));
        orderItems.save(item);

        mockMvc.perform(get("/b/" + business.getSlug() + "/admin/products/" + orderedProduct.getId() + "/edit").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Product cannot be deleted")))
                .andExpect(content().string(containsString("We can't delete this product because it has been used in past orders.")))
                .andExpect(content().string(not(containsString("This product exists in one or more orders"))));

        mockMvc.perform(post("/b/" + business.getSlug() + "/admin/products/" + orderedProduct.getId() + "/delete")
                        .param("_csrf", "test-csrf-token")
                        .session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/b/" + business.getSlug() + "/admin/products/" + orderedProduct.getId() + "/edit"));
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
        return product(business, "P0001", "Render Test Product");
    }

    private Product product(Business business, String productCode, String name) {
        Category category = new Category();
        category.setBusiness(business);
        category.setName("Shirts " + productCode);
        category = categories.save(category);

        Product product = new Product();
        product.setBusiness(business);
        product.setCategory(category);
        product.setProductCode(productCode);
        product.setName(name);
        product.setDescription("Test product");
        product.setPrice(new BigDecimal("100.00"));
        product.setMinimumTierRequired(1);
        product.setActive(false);
        product.setCreatedAt(LocalDateTime.now());
        return products.save(product);
    }

    private ProductColor color(Product product) {
        ProductColor color = new ProductColor();
        color.setProduct(product);
        color.setName("Blue");
        return colors.save(color);
    }

    private InventoryStock stock(ProductColor color, String sizeLabel, int quantity) {
        InventoryStock stock = new InventoryStock();
        stock.setProductColor(color);
        stock.setSizeLabel(sizeLabel);
        stock.setQuantity(quantity);
        return inventory.save(stock);
    }

    private SizeSet sizeSet(Product product) {
        SizeSet sizeSet = new SizeSet();
        sizeSet.setProduct(product);
        sizeSet.setName("Set A");
        sizeSet.setSizeLabels("38 (M)");
        return sizeSets.save(sizeSet);
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

    private void withCsrf(MockHttpSession session) {
        session.setAttribute("csrfToken", "test-csrf-token");
    }
}
