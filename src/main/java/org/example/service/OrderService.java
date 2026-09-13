package org.example.service;

import org.example.domain.Business;
import org.example.domain.Category;
import org.example.domain.CustomerOrder;
import org.example.domain.InventoryStock;
import org.example.domain.OrderItem;
import org.example.domain.OrderStatus;
import org.example.domain.Product;
import org.example.domain.ProductColor;
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
import org.example.session.CartLine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private final BusinessRepository businesses;
    private final CategoryRepository categories;
    private final UserAccountRepository users;
    private final ProductRepository products;
    private final ProductColorRepository colors;
    private final SizeSetRepository sizeSets;
    private final InventoryStockRepository inventory;
    private final CustomerOrderRepository orders;
    private final OrderItemRepository orderItems;
    private final FileStorageService fileStorage;

    public OrderService(
            BusinessRepository businesses,
            CategoryRepository categories,
            UserAccountRepository users,
            ProductRepository products,
            ProductColorRepository colors,
            SizeSetRepository sizeSets,
            InventoryStockRepository inventory,
            CustomerOrderRepository orders,
            OrderItemRepository orderItems,
            FileStorageService fileStorage
    ) {
        this.businesses = businesses;
        this.categories = categories;
        this.users = users;
        this.products = products;
        this.colors = colors;
        this.sizeSets = sizeSets;
        this.inventory = inventory;
        this.orders = orders;
        this.orderItems = orderItems;
        this.fileStorage = fileStorage;
    }

    public Business business(Long id) {
        return businesses.findById(id).orElseThrow();
    }

    public UserAccount user(Long id) {
        return users.findById(id).orElseThrow();
    }

    public List<Product> visibleProducts(Business business, int customerTier) {
        return products.findByBusinessAndActiveTrueAndMinimumTierRequiredGreaterThanEqualOrderByCreatedAtDescIdDesc(business, customerTier).stream()
                .filter(this::hasAvailableSet)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Product> catalogProducts(
            Business business,
            int customerTier,
            String query,
            Long categoryId,
            String colorName,
            String setName,
            boolean inStockOnly,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sort
    ) {
        String normalizedQuery = normalizeFilter(query);
        String normalizedColor = normalizeFilter(colorName);
        String normalizedSet = normalizeFilter(setName);
        List<Product> filtered = visibleProducts(business, customerTier).stream()
                .filter(product -> normalizedQuery.isBlank()
                        || contains(product.getProductCode(), normalizedQuery)
                        || contains(product.getName(), normalizedQuery))
                .filter(product -> categoryId == null
                        || (product.getCategory() != null && product.getCategory().getId().equals(categoryId)))
                .filter(product -> minPrice == null || product.getPrice().compareTo(minPrice) >= 0)
                .filter(product -> maxPrice == null || product.getPrice().compareTo(maxPrice) <= 0)
                .filter(product -> normalizedColor.isBlank() || colors(product).stream().anyMatch(color -> contains(color.getName(), normalizedColor)))
                .filter(product -> normalizedSet.isBlank() || sizeSets(product).stream()
                        .anyMatch(sizeSet -> contains(sizeSet.getName(), normalizedSet)
                                || contains(sizeSet.getSizeLabels(), normalizedSet)
                                || contains(sizeSet.getName() + " (" + sizeSet.getSizeLabels() + ")", normalizedSet)))
                .filter(product -> !inStockOnly || hasAvailableSet(product))
                .collect(Collectors.toCollection(ArrayList::new));

        Map<Long, Integer> demandByProduct = "popular".equals(sort) ? demandByProduct(business) : Map.of();
        filtered.sort(catalogComparator(sort, demandByProduct));
        return filtered;
    }

    public List<Category> catalogCategories(Business business) {
        return categories.findByBusinessOrderByName(business);
    }

    @Transactional(readOnly = true)
    public List<String> catalogColorOptions(Business business, int customerTier) {
        return visibleProducts(business, customerTier).stream()
                .flatMap(product -> colors(product).stream())
                .map(ProductColor::getName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> catalogSetOptions(Business business, int customerTier) {
        return visibleProducts(business, customerTier).stream()
                .flatMap(product -> sizeSets(product).stream())
                .map(sizeSet -> sizeSet.getName() + " (" + sizeSet.getSizeLabels() + ")")
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, String> primaryImageByProduct(List<Product> catalogProducts) {
        Map<Long, String> imageByProduct = new HashMap<>();
        for (Product product : catalogProducts) {
            colors(product).stream()
                    .map(ProductColor::getImagePath)
                    .filter(path -> path != null && !path.isBlank())
                    .findFirst()
                    .ifPresent(path -> imageByProduct.put(product.getId(), path));
        }
        return imageByProduct;
    }

    public Map<Long, BigDecimal> setPriceBySet(Product product) {
        return sizeSets(product).stream()
                .collect(Collectors.toMap(SizeSet::getId, sizeSet -> setPrice(product, sizeSet), (left, right) -> left, LinkedHashMap::new));
    }

    public BigDecimal setPrice(Product product, SizeSet sizeSet) {
        return product.getPrice().multiply(BigDecimal.valueOf(pieceCount(sizeSet)));
    }

    public boolean isCustomerOrderable(Product product) {
        return hasAvailableSet(product);
    }

    public Product product(Long id) {
        return products.findById(id).orElseThrow();
    }

    public List<ProductColor> colors(Product product) {
        return colors.findByProductOrderByName(product).stream().filter(ProductColor::isActive).toList();
    }

    public List<SizeSet> sizeSets(Product product) {
        return sizeSets.findByProductOrderByName(product);
    }

    public ProductColor color(Long id) {
        return colors.findById(id).orElseThrow();
    }

    public SizeSet sizeSet(Long id) {
        return sizeSets.findById(id).orElseThrow();
    }

    public List<InventoryStock> stock(ProductColor color) {
        return inventory.findByProductColorOrderBySizeLabel(color);
    }

    public List<CustomerOrder> ordersForBusiness(Business business) {
        return orders.findByBusinessOrderByCreatedAtDesc(business);
    }

    public List<CustomerOrder> ordersForCustomer(UserAccount customer) {
        return orders.findByCustomerOrderByCreatedAtDesc(customer);
    }

    public List<OrderItem> items(CustomerOrder order) {
        return orderItems.findByOrder(order);
    }

    public Map<Long, String> productCodesForItems(List<OrderItem> items) {
        Map<Long, String> productCodes = new HashMap<>();
        for (OrderItem item : items) {
            if (item.getProductCode() != null && !item.getProductCode().isBlank()) {
                productCodes.put(item.getId(), item.getProductCode());
            } else if (item.getProductColorId() != null) {
                String productCode = colors.findById(item.getProductColorId())
                        .map(color -> color.getProduct().getProductCode())
                        .filter(code -> code != null && !code.isBlank())
                        .orElse("-");
                productCodes.put(item.getId(), productCode);
            } else {
                productCodes.put(item.getId(), "-");
            }
        }
        return productCodes;
    }

    public CustomerOrder order(Long id) {
        return orders.findById(id).orElseThrow();
    }

    @Transactional
    public List<String> cartStockIssues(List<CartLine> cart) {
        return aggregateStockIssues(cart.stream()
                .map(line -> new StockDemand(line.getProductColorId(), line.getProductName(), line.getColorName(), line.getSizeLabels(), line.getQuantity()))
                .toList());
    }

    @Transactional
    public List<String> orderStockIssues(CustomerOrder order) {
        return aggregateStockIssues(orderItems.findByOrder(order).stream()
                .map(item -> new StockDemand(item.getProductColorId(), item.getProductName(), item.getColorName(), item.getSizeLabels(), item.getQuantity()))
                .toList());
    }

    @Transactional
    public CustomerOrder submitOrder(Business business, UserAccount customer, List<CartLine> cart) {
        List<String> issues = cartStockIssues(cart);
        if (!issues.isEmpty()) {
            throw new IllegalStateException(String.join("\n", issues));
        }
        CustomerOrder order = new CustomerOrder();
        order.setBusiness(business);
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING_APPROVAL);
        BigDecimal total = cart.stream().map(CartLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);
        CustomerOrder saved = orders.save(order);
        for (CartLine line : cart) {
            OrderItem item = new OrderItem();
            item.setOrder(saved);
            item.setProductColorId(line.getProductColorId());
            item.setSizeSetId(line.getSizeSetId());
            item.setProductCode(resolveProductCode(line));
            item.setProductName(line.getProductName());
            item.setColorName(line.getColorName());
            item.setSizeSetName(line.getSizeSetName());
            item.setSizeLabels(line.getSizeLabels());
            item.setQuantity(line.getQuantity());
            item.setPriceEach(line.getPriceEach());
            orderItems.save(item);
        }
        return saved;
    }

    @Transactional
    public void approve(Long orderId) {
        CustomerOrder order = orders.findById(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only orders waiting for approval can be approved.");
        }
        List<String> issues = orderStockIssues(order);
        if (!issues.isEmpty()) {
            order.setOutOfStockSummary(String.join("\n", issues));
            orders.save(order);
            throw new IllegalStateException("Cannot approve until stock issues are fixed:\n" + String.join("\n", issues));
        }
        clearStockFlags(order);
        order.setStatus(OrderStatus.APPROVED_AWAITING_PAYMENT);
        order.setApprovedAt(LocalDateTime.now());
        orders.save(order);
    }

    @Transactional
    public void reject(Long orderId) {
        CustomerOrder order = orders.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.REJECTED);
        orders.save(order);
    }

    @Transactional
    public void cancelByCustomer(Long orderId, Long customerId) {
        CustomerOrder order = orders.findById(orderId).orElseThrow();
        if (!order.getCustomer().getId().equals(customerId)) {
            throw new IllegalArgumentException("Order does not belong to this customer.");
        }
        if (order.getStatus() != OrderStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only orders waiting for approval can be cancelled.");
        }
        order.setStatus(OrderStatus.CANCELLED_BY_CUSTOMER);
        orders.save(order);
    }

    @Transactional
    public void submitPaymentProof(Long orderId, String transactionId, MultipartFile proof) {
        CustomerOrder order = orders.findById(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.APPROVED_AWAITING_PAYMENT && order.getStatus() != OrderStatus.OUT_OF_STOCK) {
            throw new IllegalStateException("Payment proof can only be submitted after approval.");
        }
        order.setTransactionId(transactionId);
        String proofPath = fileStorage.store(proof, "payments", order.getBusiness().getId());
        if (proofPath != null) {
            order.setPaymentProofPath(proofPath);
        }
        order.setPaymentSubmittedAt(LocalDateTime.now());
        order.setOutOfStockSummary(null);
        order.setStatus(OrderStatus.PAYMENT_SUBMITTED);
        orderItems.findByOrder(order).forEach(item -> {
            item.setOutOfStock(false);
            item.setOutOfStockReason(null);
            orderItems.save(item);
        });
        orders.save(order);
    }

    @Transactional
    public void ownerSubmitPaymentProof(Long orderId, String transactionId, MultipartFile proof) {
        CustomerOrder order = orders.findById(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.APPROVED_AWAITING_PAYMENT
                && order.getStatus() != OrderStatus.OUT_OF_STOCK
                && order.getStatus() != OrderStatus.PAYMENT_SUBMITTED) {
            throw new IllegalStateException("Owner can enter payment details only after order approval.");
        }
        order.setTransactionId(transactionId);
        String proofPath = fileStorage.store(proof, "payments", order.getBusiness().getId());
        if (proofPath != null) {
            order.setPaymentProofPath(proofPath);
        }
        order.setPaymentSubmittedAt(LocalDateTime.now());
        order.setOutOfStockSummary(null);
        order.setStatus(OrderStatus.PAYMENT_SUBMITTED);
        clearStockFlags(order);
        orders.save(order);
    }

    @Transactional
    public void verifyPaymentAndConfirm(Long orderId) {
        CustomerOrder order = orders.findById(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.PAYMENT_SUBMITTED) {
            throw new IllegalStateException("Only payment-submitted orders can be confirmed.");
        }

        List<OrderItem> items = orderItems.findByOrder(order);
        List<String> failures = orderStockIssues(order);

        if (!failures.isEmpty()) {
            items.forEach(item -> {
                item.setOutOfStock(true);
                item.setOutOfStockReason("Requested quantities exceed available colour/size stock. See order summary.");
                orderItems.save(item);
            });
            order.setStatus(OrderStatus.OUT_OF_STOCK);
            order.setOutOfStockSummary(String.join("\n", failures));
            orders.save(order);
            return;
        }

        for (OrderItem item : items) {
            ProductColor color = colors.findById(item.getProductColorId()).orElseThrow();
            Map<String, InventoryStock> stockBySize = lockedStockBySize(color);
            for (String size : sizes(item.getSizeLabels())) {
                InventoryStock stock = stockBySize.get(size.toUpperCase());
                if (stock == null) {
                    throw new IllegalStateException("Missing locked stock row for " + color.getName() + " / " + size);
                }
                stock.setQuantity(stock.getQuantity() - item.getQuantity());
                inventory.save(stock);
            }
        }
        order.setStatus(OrderStatus.PAID_CONFIRMED);
        order.setConfirmedAt(LocalDateTime.now());
        orders.save(order);
    }

    @Transactional
    public void updateItemQuantity(Long orderId, Long itemId, int quantity) {
        CustomerOrder order = orders.findById(orderId).orElseThrow();
        ensureEditableByOwner(order);
        OrderItem item = orderItems.findById(itemId).orElseThrow();
        if (!item.getOrder().getId().equals(orderId)) {
            throw new IllegalArgumentException("Order item does not belong to this order.");
        }
        item.setQuantity(Math.max(1, quantity));
        item.setOutOfStock(false);
        item.setOutOfStockReason(null);
        orderItems.save(item);
        recalculate(order);
    }

    @Transactional
    public void removeItem(Long orderId, Long itemId) {
        CustomerOrder order = orders.findById(orderId).orElseThrow();
        ensureEditableByOwner(order);
        OrderItem item = orderItems.findById(itemId).orElseThrow();
        if (!item.getOrder().getId().equals(orderId)) {
            throw new IllegalArgumentException("Order item does not belong to this order.");
        }
        orderItems.delete(item);
        recalculate(order);
    }

    @Transactional
    public void addItem(Long orderId, Long productColorId, Long sizeSetId, int quantity) {
        CustomerOrder order = orders.findById(orderId).orElseThrow();
        ensureEditableByOwner(order);
        ProductColor color = colors.findById(productColorId).orElseThrow();
        SizeSet sizeSet = sizeSets.findById(sizeSetId).orElseThrow();
        Product product = color.getProduct();
        if (!sizeSet.getProduct().getId().equals(product.getId())) {
            throw new IllegalArgumentException("Size set does not belong to product.");
        }
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductColorId(color.getId());
        item.setSizeSetId(sizeSet.getId());
        item.setProductCode(product.getProductCode());
        item.setProductName(product.getName());
        item.setColorName(color.getName());
        item.setSizeSetName(sizeSet.getName());
        item.setSizeLabels(sizeSet.getSizeLabels());
        item.setQuantity(Math.max(1, quantity));
        item.setPriceEach(setPrice(product, sizeSet));
        orderItems.save(item);
        recalculate(order);
    }

    @Transactional
    public void complete(Long orderId) {
        CustomerOrder order = orders.findById(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.PAID_CONFIRMED) {
            throw new IllegalStateException("Only paid and inventory-confirmed orders can be marked complete.");
        }
        order.setStatus(OrderStatus.COMPLETED);
        orders.save(order);
    }

    private void ensureEditableByOwner(CustomerOrder order) {
        if (order.getStatus() == OrderStatus.PAID_CONFIRMED || order.getStatus() == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Paid/completed orders cannot be edited.");
        }
    }

    private void recalculate(CustomerOrder order) {
        BigDecimal total = orderItems.findByOrder(order).stream()
                .map(item -> item.getPriceEach().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);
        if (order.getStatus() == OrderStatus.PAYMENT_SUBMITTED || order.getStatus() == OrderStatus.OUT_OF_STOCK) {
            order.setStatus(OrderStatus.APPROVED_AWAITING_PAYMENT);
            order.setTransactionId(null);
            order.setPaymentProofPath(null);
            order.setPaymentSubmittedAt(null);
        }
        List<String> issues = orderStockIssues(order);
        order.setOutOfStockSummary(issues.isEmpty() ? null : String.join("\n", issues));
        orders.save(order);
    }

    private void clearStockFlags(CustomerOrder order) {
        order.setOutOfStockSummary(null);
        orderItems.findByOrder(order).forEach(item -> {
            item.setOutOfStock(false);
            item.setOutOfStockReason(null);
            orderItems.save(item);
        });
    }

    private List<String> missingSizes(OrderItem item) {
        ProductColor color = colors.findById(item.getProductColorId()).orElse(null);
        if (color == null) {
            return List.of("Selected colour is no longer available");
        }
        Map<String, InventoryStock> stockBySize = lockedStockBySize(color);
        return sizes(item.getSizeLabels()).stream()
                .map(size -> {
                    InventoryStock stock = stockBySize.get(size.toUpperCase());
                    if (stock == null) {
                        return size + " needs " + item.getQuantity() + ", available 0";
                    }
                    return stock.getQuantity() >= item.getQuantity()
                            ? null
                            : size + " needs " + item.getQuantity() + ", available " + stock.getQuantity();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Map<String, InventoryStock> lockedStockBySize(ProductColor color) {
        return inventory.findLockedByProductColorId(color.getId()).stream()
                .collect(Collectors.toMap(stock -> stock.getSizeLabel().toUpperCase(), stock -> stock));
    }

    private List<String> sizes(String sizeLabels) {
        return Arrays.stream(sizeLabels.split(","))
                .map(String::trim)
                .filter(size -> !size.isBlank())
                .map(String::toUpperCase)
                .toList();
    }

    private Comparator<Product> catalogComparator(String sort, Map<Long, Integer> demandByProduct) {
        Comparator<Product> recent = Comparator
                .comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Product::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed();
        if ("productId".equals(sort)) {
            return Comparator.comparing((Product product) -> nullSafe(product.getProductCode()), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(recent);
        }
        if ("name".equals(sort)) {
            return Comparator.comparing((Product product) -> nullSafe(product.getName()), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(recent);
        }
        if ("priceAsc".equals(sort)) {
            return Comparator.comparing((Product product) -> product.getPrice()).thenComparing(recent);
        }
        if ("priceDesc".equals(sort)) {
            return Comparator.comparing((Product product) -> product.getPrice()).reversed().thenComparing(recent);
        }
        if ("category".equals(sort)) {
            return Comparator.comparing((Product product) -> product.getCategory() == null ? "" : product.getCategory().getName(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(recent);
        }
        if ("popular".equals(sort)) {
            return Comparator.comparing((Product product) -> demandByProduct.getOrDefault(product.getId(), 0))
                    .reversed()
                    .thenComparing(recent);
        }
        return recent;
    }

    private Map<Long, Integer> demandByProduct(Business business) {
        Map<Long, Integer> demandByProduct = new HashMap<>();
        for (OrderItem item : orderItems.findAll()) {
            if (item.getProductColorId() == null) {
                continue;
            }
            ProductColor color = colors.findById(item.getProductColorId()).orElse(null);
            if (color == null || color.getProduct() == null || color.getProduct().getBusiness() == null
                    || !color.getProduct().getBusiness().getId().equals(business.getId())) {
                continue;
            }
            demandByProduct.merge(color.getProduct().getId(), item.getQuantity(), Integer::sum);
        }
        return demandByProduct;
    }

    private boolean hasAvailableSet(Product product) {
        List<SizeSet> productSizeSets = sizeSets(product);
        if (productSizeSets.isEmpty()) {
            return false;
        }
        for (ProductColor color : colors(product)) {
            Map<String, Integer> availableBySize = inventory.findByProductColorOrderBySizeLabel(color).stream()
                    .collect(Collectors.toMap(stock -> stock.getSizeLabel().toUpperCase(), InventoryStock::getQuantity, (left, right) -> left));
            for (SizeSet sizeSet : productSizeSets) {
                boolean setAvailable = sizes(sizeSet.getSizeLabels()).stream()
                        .allMatch(size -> availableBySize.getOrDefault(size.toUpperCase(), 0) > 0);
                if (setAvailable) {
                    return true;
                }
            }
        }
        return false;
    }

    private int pieceCount(SizeSet sizeSet) {
        return sizes(sizeSet.getSizeLabels()).size();
    }

    private boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase().contains(normalizedQuery);
    }

    private String normalizeFilter(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String resolveProductCode(CartLine line) {
        if (line.getProductCode() != null && !line.getProductCode().isBlank()) {
            return line.getProductCode();
        }
        if (line.getProductColorId() == null) {
            return null;
        }
        return colors.findById(line.getProductColorId())
                .map(color -> color.getProduct().getProductCode())
                .orElse(null);
    }

    private List<String> aggregateStockIssues(List<StockDemand> demands) {
        Map<Long, Map<String, Integer>> requiredByColor = new LinkedHashMap<>();
        Map<Long, StockDemand> sampleByColor = new LinkedHashMap<>();
        for (StockDemand demand : demands) {
            sampleByColor.putIfAbsent(demand.productColorId(), demand);
            Map<String, Integer> requiredBySize = requiredByColor.computeIfAbsent(demand.productColorId(), ignored -> new LinkedHashMap<>());
            for (String size : sizes(demand.sizeLabels())) {
                requiredBySize.merge(size, demand.quantity(), Integer::sum);
            }
        }

        List<String> issues = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Integer>> colorEntry : requiredByColor.entrySet()) {
            ProductColor color = colors.findById(colorEntry.getKey()).orElse(null);
            StockDemand sample = sampleByColor.get(colorEntry.getKey());
            if (color == null) {
                issues.add(sample.productName() + " / " + sample.colorName() + ": selected colour is no longer available");
                continue;
            }
            Map<String, InventoryStock> stockBySize = lockedStockBySize(color);
            for (Map.Entry<String, Integer> sizeEntry : colorEntry.getValue().entrySet()) {
                InventoryStock stock = stockBySize.get(sizeEntry.getKey().toUpperCase());
                int available = stock == null ? 0 : stock.getQuantity();
                int required = sizeEntry.getValue();
                if (available < required) {
                    issues.add(sample.productName() + " / " + sample.colorName() + " / " + sizeEntry.getKey()
                            + ": needs " + required + ", available " + available);
                }
            }
        }
        return issues;
    }

    private record StockDemand(Long productColorId, String productName, String colorName, String sizeLabels, int quantity) {
    }
}
