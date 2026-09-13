package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.domain.Business;
import org.example.domain.CustomerOrder;
import org.example.domain.OrderItem;
import org.example.domain.OrderStatus;
import org.example.domain.Product;
import org.example.domain.ProductColor;
import org.example.service.AdminService;
import org.example.service.AnalyticsService;
import org.example.service.InvoicePdfService;
import org.example.service.OrderService;
import org.example.session.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping({"/admin", "/b/{businessSlug}/admin"})
public class AdminController {
    private final AdminService adminService;
    private final OrderService orderService;
    private final InvoicePdfService invoicePdfService;
    private final AnalyticsService analyticsService;

    public AdminController(AdminService adminService, OrderService orderService, InvoicePdfService invoicePdfService, AnalyticsService analyticsService) {
        this.adminService = adminService;
        this.orderService = orderService;
        this.invoicePdfService = invoicePdfService;
        this.analyticsService = analyticsService;
    }

    @GetMapping({"", "/"})
    public String dashboard(HttpSession session, Model model) {
        Business business = business(session);
        List<CustomerOrder> orders = orderService.ordersForBusiness(business);
        List<CustomerOrder> recentOrders = orders.stream().limit(8).toList();
        model.addAttribute("orders", recentOrders);
        model.addAttribute("recentOrders", recentOrders);
        model.addAttribute("pendingCount", orders.stream().filter(order -> order.getStatus() == OrderStatus.PENDING_APPROVAL).count());
        model.addAttribute("paymentCount", orders.stream().filter(order -> order.getStatus() == OrderStatus.PAYMENT_SUBMITTED).count());
        model.addAttribute("stockCount", orders.stream().filter(order -> order.getStatus() == OrderStatus.OUT_OF_STOCK).count());
        return withCommon(session, model, "admin/dashboard");
    }

    @GetMapping("/customers")
    public String customers(HttpSession session, Model model) {
        Business business = business(session);
        model.addAttribute("customers", adminService.customers(business));
        return withCommon(session, model, "admin/customers");
    }

    @GetMapping("/customers/{id}/activity")
    public String customerActivity(@PathVariable Long id, HttpSession session, Model model) {
        model.addAttribute("activity", analyticsService.customerActivity(orderService.user(id)));
        return withCommon(session, model, "admin/customer-activity");
    }

    @PostMapping("/customers")
    public String saveCustomer(
            HttpSession session,
            @RequestParam(required = false) Long id,
            @RequestParam String name,
            @RequestParam String companyName,
            @RequestParam String phone,
            @RequestParam(required = false) String pin,
            @RequestParam int tier,
            @RequestParam(required = false, defaultValue = "false") boolean active,
            @RequestParam(required = false) String address,
            RedirectAttributes redirectAttributes
    ) {
        adminService.saveCustomer(business(session), id, name, companyName, phone, pin, tier, active, address);
        redirectAttributes.addFlashAttribute("message", "Customer saved.");
        return adminRedirect(session, "/customers");
    }

    @GetMapping("/products")
    public String products(HttpSession session, Model model) {
        Business business = business(session);
        List<Product> products = adminService.products(business);
        model.addAttribute("products", products);
        model.addAttribute("activationIssues", adminService.activationIssues(products));
        model.addAttribute("inventorySummaryByProduct", adminService.inventorySummaryByProduct(products));
        return withCommon(session, model, "admin/products");
    }

    @GetMapping("/products/new")
    public String newProduct(HttpSession session, Model model) {
        Business business = business(session);
        Product product = new Product();
        product.setProductCode(adminService.nextProductCode(business));
        product.setActive(false);
        model.addAttribute("product", product);
        model.addAttribute("selectedSetTemplateIds", List.of());
        return productForm(session, model);
    }

    @GetMapping("/products/{id}/edit")
    public String editProduct(@PathVariable Long id, HttpSession session, Model model) {
        Product product = adminService.product(id);
        List<ProductColor> colors = adminService.colors(product);
        List<String> productSizeLabels = adminService.productSizeLabels(product);
        model.addAttribute("product", product);
        model.addAttribute("colors", colors);
        model.addAttribute("stockByColor", colors.stream().collect(Collectors.toMap(ProductColor::getId, adminService::inventory)));
        model.addAttribute("productSizeLabels", productSizeLabels);
        model.addAttribute("stockQuantityByColor", adminService.stockQuantityByColor(colors, productSizeLabels));
        model.addAttribute("sizeSets", adminService.sizeSets(product));
        model.addAttribute("selectedSetTemplateIds", adminService.selectedSetTemplateIds(product));
        model.addAttribute("activationIssue", adminService.activationIssue(product));
        return productForm(session, model);
    }

    @PostMapping("/products")
    public String saveProduct(
            HttpSession session,
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String productCode,
            @RequestParam String name,
            @RequestParam String description,
            @RequestParam Long categoryId,
            @RequestParam BigDecimal price,
            @RequestParam int minimumTierRequired,
            @RequestParam(required = false, defaultValue = "false") boolean active,
            @RequestParam(required = false) List<Long> setTemplateIds,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Product product = adminService.saveProduct(business(session), id, productCode, name, description, categoryId, price, minimumTierRequired, active, setTemplateIds);
            redirectAttributes.addFlashAttribute("message", "Product saved.");
            return adminRedirect(session, "/products/" + product.getId() + "/edit");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return id == null ? adminRedirect(session, "/products/new") : adminRedirect(session, "/products/" + id + "/edit");
        }
    }

    @GetMapping("/categories")
    public String categories(HttpSession session, Model model) {
        Business business = business(session);
        model.addAttribute("categories", adminService.categories(business));
        return withCommon(session, model, "admin/categories");
    }

    @PostMapping("/categories")
    public String saveCategory(
            HttpSession session,
            @RequestParam(required = false) Long id,
            @RequestParam String name,
            RedirectAttributes redirectAttributes
    ) {
        try {
            adminService.saveCategory(business(session), id, name);
            redirectAttributes.addFlashAttribute("message", "Category saved.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return adminRedirect(session, "/categories");
    }

    @GetMapping("/sizes")
    public String sizes(HttpSession session, Model model) {
        Business business = business(session);
        model.addAttribute("sizes", adminService.sizes(business));
        return withCommon(session, model, "admin/sizes");
    }

    @PostMapping("/sizes")
    public String saveSize(
            HttpSession session,
            @RequestParam(required = false) Long id,
            @RequestParam String label,
            RedirectAttributes redirectAttributes
    ) {
        try {
            adminService.saveSize(business(session), id, label);
            redirectAttributes.addFlashAttribute("message", "Size saved.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return adminRedirect(session, "/sizes");
    }

    @GetMapping("/sets")
    public String sets(HttpSession session, Model model) {
        Business business = business(session);
        model.addAttribute("sets", adminService.setTemplates(business));
        model.addAttribute("sizes", adminService.sizes(business));
        return withCommon(session, model, "admin/sets");
    }

    @PostMapping("/sets")
    public String saveSet(
            HttpSession session,
            @RequestParam(required = false) Long id,
            @RequestParam String name,
            @RequestParam(required = false) List<Long> sizeIds,
            RedirectAttributes redirectAttributes
    ) {
        try {
            adminService.saveSetTemplate(business(session), id, name, sizeIds);
            redirectAttributes.addFlashAttribute("message", "Set saved.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return adminRedirect(session, "/sets");
    }

    @PostMapping("/products/{id}/colors")
    public String addColor(
            HttpSession session,
            @PathVariable Long id,
            @RequestParam String colorName,
            @RequestParam MultipartFile image,
            @RequestParam(required = false) List<String> sizeLabels,
            @RequestParam(required = false) List<String> quantities,
            RedirectAttributes redirectAttributes
    ) {
        Product product = adminService.product(id);
        boolean shouldPromptActivation = !product.isActive();
        adminService.addColor(product, colorName, image, sizeLabels, quantities);
        redirectAttributes.addFlashAttribute("message", "Colour and inventory added.");
        if (shouldPromptActivation) {
            redirectAttributes.addFlashAttribute("activatePromptProductId", id);
        }
        return adminRedirect(session, "/products/" + id + "/edit");
    }

    @PostMapping("/products/{id}/activate")
    public String activateProduct(
            HttpSession session,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            adminService.activateProduct(business(session), id);
            redirectAttributes.addFlashAttribute("message", "Product is now active.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return adminRedirect(session, "/products/" + id + "/edit");
    }

    @PostMapping("/colors/{id}/inventory")
    public String updateInventory(
            HttpSession session,
            @PathVariable Long id,
            @RequestParam(required = false) List<String> sizeLabels,
            @RequestParam(required = false) List<String> quantities,
            RedirectAttributes redirectAttributes
    ) {
        ProductColor color = adminService.color(id);
        adminService.updateInventory(color, sizeLabels, quantities);
        redirectAttributes.addFlashAttribute("message", "Inventory updated.");
        if (!color.getProduct().isActive()) {
            redirectAttributes.addFlashAttribute("activatePromptProductId", color.getProduct().getId());
        }
        return adminRedirect(session, "/products/" + color.getProduct().getId() + "/edit");
    }

    @GetMapping("/orders")
    public String orders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            HttpSession session,
            Model model
    ) {
        Business business = business(session);
        List<CustomerOrder> orders = orderService.ordersForBusiness(business);
        OrderStatus selectedStatus = parseOrderStatus(status);
        if (selectedStatus != null) {
            orders = orders.stream().filter(order -> order.getStatus() == selectedStatus).toList();
        }
        if (customerId != null) {
            orders = orders.stream().filter(order -> order.getCustomer().getId().equals(customerId)).toList();
        }
        if (createdFrom != null) {
            orders = orders.stream()
                    .filter(order -> order.getCreatedAt() != null && !order.getCreatedAt().toLocalDate().isBefore(createdFrom))
                    .toList();
        }
        if (createdTo != null) {
            orders = orders.stream()
                    .filter(order -> order.getCreatedAt() != null && !order.getCreatedAt().toLocalDate().isAfter(createdTo))
                    .toList();
        }
        model.addAttribute("orders", orders);
        model.addAttribute("customers", adminService.customers(business));
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("selectedCustomerId", customerId);
        model.addAttribute("createdFrom", createdFrom);
        model.addAttribute("createdTo", createdTo);
        model.addAttribute("statuses", OrderStatus.values());
        return withCommon(session, model, "admin/orders");
    }

    @GetMapping("/orders/{id}")
    public String orderDetail(@PathVariable Long id, HttpSession session, Model model) {
        CustomerOrder order = orderService.order(id);
        Business business = business(session);
        List<OrderItem> items = orderService.items(order);
        model.addAttribute("order", order);
        model.addAttribute("items", items);
        model.addAttribute("productCodesByItem", orderService.productCodesForItems(items));
        model.addAttribute("productIdsByItem", orderService.productIdsForItems(items));
        model.addAttribute("stockIssues", shouldCheckLiveStock(order) ? orderService.orderStockIssues(order) : List.of());
        model.addAttribute("colors", adminService.colors(business));
        model.addAttribute("sizeSets", adminService.sizeSets(business));
        return withCommon(session, model, "admin/order-detail");
    }

    @GetMapping("/orders/{id}/invoice.pdf")
    public ResponseEntity<byte[]> invoice(@PathVariable Long id) {
        CustomerOrder order = orderService.order(id);
        byte[] pdf = invoicePdfService.invoice(order, orderService.items(order));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=order-" + order.getId() + "-invoice.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/analytics")
    public String analytics(
            HttpSession session,
            Model model,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Business business = business(session);
        model.addAttribute("summary", analyticsService.summary(business, from, to));
        return withCommon(session, model, "admin/analytics");
    }

    @PostMapping("/orders/{id}/approve")
    public String approve(HttpSession session, @PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            orderService.approve(id);
            redirectAttributes.addFlashAttribute("message", "Order approved. Customer can submit payment proof now.");
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return adminRedirect(session, "/orders/" + id);
    }

    @PostMapping("/orders/{id}/items/{itemId}/quantity")
    public String updateItemQuantity(
            HttpSession session,
            @PathVariable Long id,
            @PathVariable Long itemId,
            @RequestParam int quantity,
            RedirectAttributes redirectAttributes
    ) {
        try {
            orderService.updateItemQuantity(id, itemId, quantity);
            redirectAttributes.addFlashAttribute("message", "Order item updated.");
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return adminRedirect(session, "/orders/" + id);
    }

    @PostMapping("/orders/{id}/items/{itemId}/remove")
    public String removeItem(HttpSession session, @PathVariable Long id, @PathVariable Long itemId, RedirectAttributes redirectAttributes) {
        try {
            orderService.removeItem(id, itemId);
            redirectAttributes.addFlashAttribute("message", "Order item removed.");
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return adminRedirect(session, "/orders/" + id);
    }

    @PostMapping("/orders/{id}/items")
    public String addItem(
            HttpSession session,
            @PathVariable Long id,
            @RequestParam Long productColorId,
            @RequestParam Long sizeSetId,
            @RequestParam int quantity,
            RedirectAttributes redirectAttributes
    ) {
        try {
            orderService.addItem(id, productColorId, sizeSetId, quantity);
            redirectAttributes.addFlashAttribute("message", "Replacement item added.");
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return adminRedirect(session, "/orders/" + id);
    }

    @PostMapping("/orders/{id}/payment")
    public String ownerSubmitPayment(
            HttpSession session,
            @PathVariable Long id,
            @RequestParam String transactionId,
            @RequestParam(required = false) MultipartFile proof,
            RedirectAttributes redirectAttributes
    ) {
        try {
            orderService.ownerSubmitPaymentProof(id, transactionId, proof);
            redirectAttributes.addFlashAttribute("message", "Payment details saved. You can now verify payment and reduce inventory.");
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return adminRedirect(session, "/orders/" + id);
    }

    @PostMapping("/orders/{id}/reject")
    public String reject(HttpSession session, @PathVariable Long id, RedirectAttributes redirectAttributes) {
        orderService.reject(id);
        redirectAttributes.addFlashAttribute("message", "Order rejected.");
        return adminRedirect(session, "/orders/" + id);
    }

    @PostMapping("/orders/{id}/verify-payment")
    public String verifyPayment(HttpSession session, @PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            orderService.verifyPaymentAndConfirm(id);
            CustomerOrder order = orderService.order(id);
            if (order.getStatus() == OrderStatus.OUT_OF_STOCK) {
                redirectAttributes.addFlashAttribute("message", "Payment was reviewed, but one or more items are out of stock.");
            } else {
                redirectAttributes.addFlashAttribute("message", "Payment verified and inventory reduced.");
            }
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return adminRedirect(session, "/orders/" + id);
    }

    @PostMapping("/orders/{id}/complete")
    public String complete(HttpSession session, @PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            orderService.complete(id);
            redirectAttributes.addFlashAttribute("message", "Order marked complete.");
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return adminRedirect(session, "/orders/" + id);
    }

    private Business business(HttpSession session) {
        CurrentUser currentUser = (CurrentUser) session.getAttribute("currentUser");
        return adminService.business(currentUser.businessId());
    }

    private String withCommon(HttpSession session, Model model, String view) {
        model.addAttribute("me", session.getAttribute("currentUser"));
        model.addAttribute("business", business(session));
        return view;
    }

    private OrderStatus parseOrderStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean shouldCheckLiveStock(CustomerOrder order) {
        return order.getStatus() == OrderStatus.PENDING_APPROVAL
                || order.getStatus() == OrderStatus.APPROVED_AWAITING_PAYMENT
                || order.getStatus() == OrderStatus.PAYMENT_SUBMITTED
                || order.getStatus() == OrderStatus.OUT_OF_STOCK;
    }

    private String productForm(HttpSession session, Model model) {
        Business business = business(session);
        model.addAttribute("categories", adminService.categories(business));
        model.addAttribute("setTemplates", adminService.setTemplates(business));
        if (!model.containsAttribute("productSizeLabels")) {
            model.addAttribute("productSizeLabels", List.of());
        }
        if (!model.containsAttribute("stockQuantityByColor")) {
            model.addAttribute("stockQuantityByColor", Map.of());
        }
        if (!model.containsAttribute("activationIssue")) {
            model.addAttribute("activationIssue", null);
        }
        return withCommon(session, model, "admin/product-form");
    }

    private String adminRedirect(HttpSession session, String path) {
        CurrentUser currentUser = (CurrentUser) session.getAttribute("currentUser");
        String normalized = path.startsWith("/") ? path : "/" + path;
        return "redirect:/b/" + currentUser.businessSlug() + "/admin" + normalized;
    }
}
