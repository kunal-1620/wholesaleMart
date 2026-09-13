package org.example.service;

import org.example.domain.Business;
import org.example.domain.CustomerOrder;
import org.example.domain.OrderItem;
import org.example.domain.OrderStatus;
import org.example.domain.ProductColor;
import org.example.domain.UserAccount;
import org.example.repo.ProductColorRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {
    private final OrderService orderService;
    private final ProductColorRepository colors;

    public AnalyticsService(OrderService orderService, ProductColorRepository colors) {
        this.orderService = orderService;
        this.colors = colors;
    }

    public AnalyticsSummary summary(Business business, LocalDate from, LocalDate to) {
        LocalDateTime fromTime = from == null ? LocalDate.now().minusDays(30).atStartOfDay() : from.atStartOfDay();
        LocalDateTime toTime = to == null ? LocalDate.now().plusDays(1).atStartOfDay() : to.plusDays(1).atStartOfDay();
        List<CustomerOrder> orders = orderService.ordersForBusiness(business).stream()
                .filter(order -> !order.getCreatedAt().isBefore(fromTime) && order.getCreatedAt().isBefore(toTime))
                .toList();
        List<CustomerOrder> soldOrders = orders.stream().filter(this::countsAsSold).toList();

        BigDecimal revenue = soldOrders.stream()
                .map(CustomerOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int setsSold = soldOrders.stream()
                .flatMap(order -> orderService.items(order).stream())
                .mapToInt(OrderItem::getQuantity)
                .sum();
        int piecesSold = soldOrders.stream()
                .flatMap(order -> orderService.items(order).stream())
                .mapToInt(item -> item.getQuantity() * sizeCount(item.getSizeLabels()))
                .sum();

        return new AnalyticsSummary(
                fromTime.toLocalDate(),
                toTime.minusDays(1).toLocalDate(),
                orders.size(),
                soldOrders.size(),
                revenue,
                setsSold,
                piecesSold,
                countByStatus(orders),
                topProducts(orders, false),
                topProducts(soldOrders, true),
                topCategories(orders),
                outOfStockItems(orders),
                customerRows(business, orders)
        );
    }

    public CustomerActivity customerActivity(UserAccount customer) {
        List<CustomerOrder> orders = orderService.ordersForCustomer(customer);
        BigDecimal spend = orders.stream()
                .filter(this::countsAsSold)
                .map(CustomerOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int orderCount = orders.size();
        int confirmedCount = (int) orders.stream().filter(this::countsAsSold).count();
        LocalDateTime lastOrderAt = orders.stream()
                .map(CustomerOrder::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return new CustomerActivity(customer, orderCount, confirmedCount, spend, lastOrderAt, orders);
    }

    private Map<OrderStatus, Long> countByStatus(List<CustomerOrder> orders) {
        return orders.stream().collect(Collectors.groupingBy(CustomerOrder::getStatus, LinkedHashMap::new, Collectors.counting()));
    }

    private List<MetricRow> topProducts(List<CustomerOrder> orders, boolean soldOnly) {
        Map<String, MetricAccumulator> rows = new LinkedHashMap<>();
        for (CustomerOrder order : orders) {
            for (OrderItem item : orderService.items(order)) {
                String productCode = productCode(item);
                String key = productCode + "|" + item.getProductName();
                MetricAccumulator row = rows.computeIfAbsent(key, ignored -> new MetricAccumulator(productCode, item.getProductName()));
                row.orders++;
                row.sets += item.getQuantity();
                row.pieces += item.getQuantity() * sizeCount(item.getSizeLabels());
                if (soldOnly) {
                    row.amount = row.amount.add(item.getPriceEach().multiply(BigDecimal.valueOf(item.getQuantity())));
                }
            }
        }
        return rows.values().stream().map(MetricAccumulator::row)
                .sorted(Comparator.comparing(MetricRow::pieces).reversed())
                .limit(10)
                .toList();
    }

    private List<MetricRow> topCategories(List<CustomerOrder> orders) {
        Map<String, MetricAccumulator> rows = new LinkedHashMap<>();
        for (CustomerOrder order : orders) {
            for (OrderItem item : orderService.items(order)) {
                String category = colors.findById(item.getProductColorId())
                        .map(ProductColor::getProduct)
                        .map(product -> product.getCategory() == null ? "Uncategorized" : product.getCategory().getName())
                        .orElse("Uncategorized");
                MetricAccumulator row = rows.computeIfAbsent(category, ignored -> new MetricAccumulator("-", category));
                row.orders++;
                row.sets += item.getQuantity();
                row.pieces += item.getQuantity() * sizeCount(item.getSizeLabels());
            }
        }
        return rows.values().stream().map(MetricAccumulator::row)
                .sorted(Comparator.comparing(MetricRow::pieces).reversed())
                .limit(10)
                .toList();
    }

    private List<MetricRow> outOfStockItems(List<CustomerOrder> orders) {
        Map<String, MetricAccumulator> rows = new LinkedHashMap<>();
        for (CustomerOrder order : orders) {
            for (OrderItem item : orderService.items(order)) {
                if (item.isOutOfStock()) {
                    String productCode = productCode(item);
                    String key = item.getProductName() + " / " + item.getColorName() + " / " + item.getSizeSetName();
                    MetricAccumulator row = rows.computeIfAbsent(productCode + "|" + key, ignored -> new MetricAccumulator(productCode, key));
                    row.orders++;
                    row.sets += item.getQuantity();
                    row.pieces += item.getQuantity() * sizeCount(item.getSizeLabels());
                }
            }
        }
        return rows.values().stream().map(MetricAccumulator::row)
                .sorted(Comparator.comparing(MetricRow::orders).reversed())
                .limit(10)
                .toList();
    }

    private List<CustomerMetricRow> customerRows(Business business, List<CustomerOrder> orders) {
        Map<UserAccount, List<CustomerOrder>> byCustomer = orders.stream()
                .collect(Collectors.groupingBy(CustomerOrder::getCustomer));
        return byCustomer.entrySet().stream()
                .map(entry -> {
                    BigDecimal spend = entry.getValue().stream()
                            .filter(this::countsAsSold)
                            .map(CustomerOrder::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    LocalDateTime lastOrderAt = entry.getValue().stream()
                            .map(CustomerOrder::getCreatedAt)
                            .max(LocalDateTime::compareTo)
                            .orElse(null);
                    return new CustomerMetricRow(entry.getKey(), entry.getValue().size(), spend, lastOrderAt);
                })
                .sorted(Comparator.comparing(CustomerMetricRow::spend).reversed())
                .toList();
    }

    private int sizeCount(String sizes) {
        if (sizes == null || sizes.isBlank()) {
            return 0;
        }
        return (int) List.of(sizes.split(",")).stream().filter(size -> !size.isBlank()).count();
    }

    private boolean countsAsSold(CustomerOrder order) {
        return order.getStatus() == OrderStatus.PAID_CONFIRMED || order.getStatus() == OrderStatus.COMPLETED;
    }

    private String productCode(OrderItem item) {
        if (item.getProductCode() != null && !item.getProductCode().isBlank()) {
            return item.getProductCode();
        }
        if (item.getProductColorId() == null) {
            return "-";
        }
        return colors.findById(item.getProductColorId())
                .map(ProductColor::getProduct)
                .map(product -> product.getProductCode() == null || product.getProductCode().isBlank() ? "-" : product.getProductCode())
                .orElse("-");
    }

    private static class MetricAccumulator {
        private final String productCode;
        private final String label;
        private int orders;
        private int sets;
        private int pieces;
        private BigDecimal amount = BigDecimal.ZERO;

        private MetricAccumulator(String productCode, String label) {
            this.productCode = productCode;
            this.label = label;
        }

        private MetricRow row() {
            return new MetricRow(productCode, label, orders, sets, pieces, amount);
        }
    }

    public record AnalyticsSummary(
            LocalDate from,
            LocalDate to,
            int orderCount,
            int soldOrderCount,
            BigDecimal revenue,
            int setsSold,
            int piecesSold,
            Map<OrderStatus, Long> statusCounts,
            List<MetricRow> mostDemandedProducts,
            List<MetricRow> highlySoldProducts,
            List<MetricRow> categoryDemand,
            List<MetricRow> frequentlyOutOfStock,
            List<CustomerMetricRow> customerRows
    ) {
    }

    public record MetricRow(String productCode, String label, int orders, int sets, int pieces, BigDecimal amount) {
    }

    public record CustomerMetricRow(UserAccount customer, int orders, BigDecimal spend, LocalDateTime lastOrderAt) {
    }

    public record CustomerActivity(UserAccount customer, int orderCount, int confirmedOrderCount, BigDecimal spend, LocalDateTime lastOrderAt, List<CustomerOrder> orders) {
    }
}
