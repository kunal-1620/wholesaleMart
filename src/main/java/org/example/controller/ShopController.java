package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.domain.Business;
import org.example.domain.CustomerOrder;
import org.example.domain.OrderItem;
import org.example.domain.OrderStatus;
import org.example.domain.Product;
import org.example.domain.ProductColor;
import org.example.domain.SizeSet;
import org.example.domain.UserAccount;
import org.example.service.InvoicePdfService;
import org.example.service.OrderService;
import org.example.session.CartLine;
import org.example.session.CurrentUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class ShopController {
    private final OrderService orderService;
    private final InvoicePdfService invoicePdfService;

    public ShopController(OrderService orderService, InvoicePdfService invoicePdfService) {
        this.orderService = orderService;
        this.invoicePdfService = invoicePdfService;
    }

    @GetMapping({"/shop", "/b/{businessSlug}/shop"})
    public String catalog(
            HttpSession session,
            Model model,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String colorName,
            @RequestParam(required = false) String setName,
            @RequestParam(required = false, defaultValue = "false") boolean inStockOnly,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false, defaultValue = "recent") String sort
    ) {
        CurrentUser user = current(session);
        Business business = orderService.business(user.businessId());
        List<Product> products = orderService.catalogProducts(business, user.tier(), q, categoryId, colorName, setName, inStockOnly, minPrice, maxPrice, sort);
        model.addAttribute("business", business);
        model.addAttribute("products", products);
        model.addAttribute("productImageById", orderService.primaryImageByProduct(products));
        model.addAttribute("categories", orderService.catalogCategories(business));
        model.addAttribute("colorOptions", orderService.catalogColorOptions(business, user.tier()));
        model.addAttribute("setOptions", orderService.catalogSetOptions(business, user.tier()));
        model.addAttribute("q", q);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("colorName", colorName);
        model.addAttribute("setName", setName);
        model.addAttribute("inStockOnly", inStockOnly);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("sort", sort);
        return withCommon(session, model, "shop/catalog");
    }

    @GetMapping({"/shop/products/{id}", "/b/{businessSlug}/shop/products/{id}"})
    public String product(@PathVariable Long id, HttpSession session, Model model) {
        CurrentUser user = current(session);
        Product product = orderService.product(id);
        if (!product.getBusiness().getId().equals(user.businessId()) || product.getMinimumTierRequired() < user.tier() || !product.isActive()
                || !orderService.isCustomerOrderable(product)) {
            return businessRedirect(session, "/shop");
        }
        List<ProductColor> colors = orderService.colors(product);
        List<SizeSet> sizeSets = orderService.sizeSets(product);
        model.addAttribute("product", product);
        model.addAttribute("colors", colors);
        model.addAttribute("stockByColor", colors.stream().collect(Collectors.toMap(ProductColor::getId, orderService::stock)));
        model.addAttribute("sizeSets", sizeSets);
        model.addAttribute("setPriceBySet", orderService.setPriceBySet(product));
        model.addAttribute("cartQuantityLabelsByColor", cartQuantityLabelsByColor(colors, sizeSets, cart(session)));
        return withCommon(session, model, "shop/product");
    }

    @PostMapping({"/cart/add", "/b/{businessSlug}/cart/add"})
    public String addToCart(
            HttpSession session,
            @RequestParam Long productColorId,
            @RequestParam MultiValueMap<String, String> form,
            RedirectAttributes redirectAttributes
    ) {
        ProductColor color = orderService.color(productColorId);
        Product product = color.getProduct();
        CurrentUser user = current(session);
        if (!product.getBusiness().getId().equals(user.businessId()) || product.getMinimumTierRequired() < user.tier() || !product.isActive()) {
            redirectAttributes.addFlashAttribute("message", "That product is not available for your account.");
            return businessRedirect(session, "/shop");
        }
        List<CartLine> additions = new ArrayList<>();
        for (SizeSet sizeSet : orderService.sizeSets(product)) {
            Long sizeSetId = sizeSet.getId();
            if (!sizeSet.getProduct().getId().equals(product.getId())) {
                redirectAttributes.addFlashAttribute("error", "Selected size set does not belong to this product.");
                return businessRedirect(session, "/shop/products/" + product.getId());
            }
            int quantity = parseQuantity(form.getFirst("quantity_" + sizeSetId));
            if (quantity <= 0) {
                continue;
            }
            CartLine line = new CartLine();
            line.setProductId(product.getId());
            line.setProductColorId(productColorId);
            line.setSizeSetId(sizeSetId);
            line.setProductCode(product.getProductCode());
            line.setProductName(product.getName());
            line.setColorName(color.getName());
            line.setSizeSetName(sizeSet.getName());
            line.setSizeLabels(sizeSet.getSizeLabels());
            line.setQuantity(quantity);
            line.setPriceEach(orderService.setPrice(product, sizeSet));
            additions.add(line);
        }
        if (additions.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Enter quantity greater than 0 for at least one fixed size set.");
            return businessRedirect(session, "/shop/products/" + product.getId());
        }
        List<CartLine> cart = cart(session);
        List<CartLine> cartWithAdditions = new ArrayList<>(cart);
        cartWithAdditions.addAll(additions);
        List<String> stockIssues = orderService.cartStockIssues(cartWithAdditions);
        if (!stockIssues.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Some selected sets are not available in the requested quantity.\n" + String.join("\n", stockIssues));
            return businessRedirect(session, "/shop/products/" + product.getId());
        }
        mergeCartLines(cart, additions);
        redirectAttributes.addFlashAttribute("message", additions.size() == 1 ? "Added to cart." : "Added " + additions.size() + " sets to cart.");
        return businessRedirect(session, "/shop/products/" + product.getId());
    }

    @GetMapping({"/cart", "/b/{businessSlug}/cart"})
    public String cartPage(HttpSession session, Model model) {
        List<CartLine> cart = cart(session);
        model.addAttribute("cart", cart);
        model.addAttribute("total", cart.stream().map(CartLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        return withCommon(session, model, "shop/cart");
    }

    @PostMapping({"/cart/clear", "/b/{businessSlug}/cart/clear"})
    public String clearCart(HttpSession session) {
        cart(session).clear();
        return businessRedirect(session, "/cart");
    }

    @PostMapping({"/cart/items/{index}/quantity", "/b/{businessSlug}/cart/items/{index}/quantity"})
    public String updateCartItemQuantity(
            HttpSession session,
            @PathVariable int index,
            @RequestParam String quantity,
            RedirectAttributes redirectAttributes
    ) {
        List<CartLine> cart = cart(session);
        if (index < 0 || index >= cart.size()) {
            redirectAttributes.addFlashAttribute("error", "Cart item was not found.");
            return businessRedirect(session, "/cart");
        }
        int requestedQuantity = parseQuantity(quantity);
        if (requestedQuantity < 1) {
            redirectAttributes.addFlashAttribute("error", "Quantity must be 1 or more.");
            return businessRedirect(session, "/cart");
        }
        CartLine line = cart.get(index);
        int previousQuantity = line.getQuantity();
        line.setQuantity(requestedQuantity);
        List<String> stockIssues = orderService.cartStockIssues(cart);
        if (!stockIssues.isEmpty()) {
            line.setQuantity(previousQuantity);
            redirectAttributes.addFlashAttribute("error", "Quantity could not be updated because stock is not available.\n" + String.join("\n", stockIssues));
            return businessRedirect(session, "/cart");
        }
        redirectAttributes.addFlashAttribute("message", "Cart quantity updated.");
        return businessRedirect(session, "/cart");
    }

    @PostMapping({"/cart/items/{index}/remove", "/b/{businessSlug}/cart/items/{index}/remove"})
    public String removeCartItem(
            HttpSession session,
            @PathVariable int index,
            RedirectAttributes redirectAttributes
    ) {
        List<CartLine> cart = cart(session);
        if (index < 0 || index >= cart.size()) {
            redirectAttributes.addFlashAttribute("error", "Cart item was not found.");
            return businessRedirect(session, "/cart");
        }
        cart.remove(index);
        redirectAttributes.addFlashAttribute("message", "Item removed from cart.");
        return businessRedirect(session, "/cart");
    }

    @PostMapping({"/cart/submit", "/b/{businessSlug}/cart/submit"})
    public String submitCart(HttpSession session, RedirectAttributes redirectAttributes) {
        List<CartLine> cart = cart(session);
        if (cart.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "Your cart is empty.");
            return businessRedirect(session, "/cart");
        }
        CurrentUser currentUser = current(session);
        Business business = orderService.business(currentUser.businessId());
        UserAccount customer = orderService.user(currentUser.userId());
        CustomerOrder order;
        try {
            order = orderService.submitOrder(business, customer, cart);
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", "Some items are no longer available. Please update your cart.\n" + exception.getMessage());
            return businessRedirect(session, "/cart");
        }
        cart.clear();
        redirectAttributes.addFlashAttribute("message", "Order submitted for owner approval.");
        return businessRedirect(session, "/orders/" + order.getId());
    }

    @GetMapping({"/orders", "/b/{businessSlug}/orders"})
    public String orders(
            HttpSession session,
            Model model,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo
    ) {
        UserAccount customer = orderService.user(current(session).userId());
        List<CustomerOrder> orders = orderService.ordersForCustomer(customer);
        OrderStatus selectedStatus = parseOrderStatus(status);
        if (selectedStatus != null) {
            orders = orders.stream().filter(order -> order.getStatus() == selectedStatus).toList();
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
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("createdFrom", createdFrom);
        model.addAttribute("createdTo", createdTo);
        return withCommon(session, model, "shop/orders");
    }

    @GetMapping({"/orders/{id}", "/b/{businessSlug}/orders/{id}"})
    public String orderDetail(@PathVariable Long id, HttpSession session, Model model) {
        CustomerOrder order = orderService.order(id);
        if (!order.getCustomer().getId().equals(current(session).userId())) {
            return businessRedirect(session, "/orders");
        }
        List<OrderItem> items = orderService.items(order);
        model.addAttribute("order", order);
        model.addAttribute("items", items);
        model.addAttribute("productCodesByItem", orderService.productCodesForItems(items));
        model.addAttribute("productIdsByItem", orderService.productIdsForItems(items));
        model.addAttribute("business", order.getBusiness());
        return withCommon(session, model, "shop/order-detail");
    }

    @GetMapping({"/orders/{id}/invoice.pdf", "/b/{businessSlug}/orders/{id}/invoice.pdf"})
    public ResponseEntity<byte[]> invoice(@PathVariable Long id, HttpSession session) {
        CustomerOrder order = orderService.order(id);
        if (!order.getCustomer().getId().equals(current(session).userId())) {
            return ResponseEntity.notFound().build();
        }
        byte[] pdf = invoicePdfService.invoice(order, orderService.items(order));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=order-" + order.getId() + "-invoice.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping({"/orders/{id}/cancel", "/b/{businessSlug}/orders/{id}/cancel"})
    public String cancelOrder(
            HttpSession session,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            orderService.cancelByCustomer(id, current(session).userId());
            redirectAttributes.addFlashAttribute("message", "Order cancelled.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return businessRedirect(session, "/orders/" + id);
    }

    @PostMapping({"/orders/{id}/payment", "/b/{businessSlug}/orders/{id}/payment"})
    public String submitPayment(
            HttpSession session,
            @PathVariable Long id,
            @RequestParam String transactionId,
            @RequestParam(required = false) MultipartFile proof,
            RedirectAttributes redirectAttributes
    ) {
        orderService.submitPaymentProof(id, transactionId, proof);
        redirectAttributes.addFlashAttribute("message", "Payment proof submitted. Owner will verify stock and payment.");
        return businessRedirect(session, "/orders/" + id);
    }

    @SuppressWarnings("unchecked")
    private List<CartLine> cart(HttpSession session) {
        List<CartLine> cart = (List<CartLine>) session.getAttribute("cart");
        if (cart == null) {
            cart = new ArrayList<>();
            session.setAttribute("cart", cart);
        }
        return cart;
    }

    private CurrentUser current(HttpSession session) {
        return (CurrentUser) session.getAttribute("currentUser");
    }

    private String withCommon(HttpSession session, Model model, String view) {
        CurrentUser currentUser = current(session);
        model.addAttribute("me", currentUser);
        model.addAttribute("business", orderService.business(currentUser.businessId()));
        return view;
    }

    private String businessRedirect(HttpSession session, String path) {
        CurrentUser currentUser = current(session);
        String normalized = path.startsWith("/") ? path : "/" + path;
        return "redirect:/b/" + currentUser.businessSlug() + normalized;
    }

    private int parseQuantity(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (Exception exception) {
            return 0;
        }
    }

    private void mergeCartLines(List<CartLine> cart, List<CartLine> additions) {
        for (CartLine addition : additions) {
            CartLine existing = cart.stream()
                    .filter(line -> addition.getProductColorId().equals(line.getProductColorId())
                            && addition.getSizeSetId().equals(line.getSizeSetId()))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                cart.add(addition);
                continue;
            }
            existing.setProductCode(addition.getProductCode());
            existing.setProductName(addition.getProductName());
            existing.setColorName(addition.getColorName());
            existing.setSizeSetName(addition.getSizeSetName());
            existing.setSizeLabels(addition.getSizeLabels());
            existing.setPriceEach(addition.getPriceEach());
            existing.setQuantity(existing.getQuantity() + addition.getQuantity());
        }
    }

    private Map<Long, List<String>> cartQuantityLabelsByColor(List<ProductColor> colors, List<SizeSet> sizeSets, List<CartLine> cart) {
        Map<Long, List<String>> labelsByColor = new LinkedHashMap<>();
        for (ProductColor color : colors) {
            List<String> labels = new ArrayList<>();
            for (SizeSet sizeSet : sizeSets) {
                int quantity = cart.stream()
                        .filter(line -> color.getId().equals(line.getProductColorId()) && sizeSet.getId().equals(line.getSizeSetId()))
                        .mapToInt(CartLine::getQuantity)
                        .sum();
                if (quantity > 0) {
                    labels.add(sizeSet.getName() + " (" + sizeSet.getSizeLabels() + "): " + quantity);
                }
            }
            labelsByColor.put(color.getId(), labels);
        }
        return labelsByColor;
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
}
