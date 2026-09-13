package org.example.service;

import org.example.domain.Business;
import org.example.domain.Category;
import org.example.domain.InventoryStock;
import org.example.domain.Product;
import org.example.domain.ProductColor;
import org.example.domain.Role;
import org.example.domain.SizeOption;
import org.example.domain.SizeSet;
import org.example.domain.SizeSetTemplate;
import org.example.domain.UserAccount;
import org.example.repo.BusinessRepository;
import org.example.repo.CategoryRepository;
import org.example.repo.InventoryStockRepository;
import org.example.repo.OrderItemRepository;
import org.example.repo.ProductColorRepository;
import org.example.repo.ProductRepository;
import org.example.repo.SizeOptionRepository;
import org.example.repo.SizeSetRepository;
import org.example.repo.SizeSetTemplateRepository;
import org.example.repo.UserAccountRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminService {
    private final BusinessRepository businesses;
    private final UserAccountRepository users;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductColorRepository colors;
    private final InventoryStockRepository inventory;
    private final OrderItemRepository orderItems;
    private final SizeSetRepository sizeSets;
    private final SizeOptionRepository sizeOptions;
    private final SizeSetTemplateRepository setTemplates;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final FileStorageService fileStorage;

    public AdminService(
            BusinessRepository businesses,
            UserAccountRepository users,
            CategoryRepository categories,
            ProductRepository products,
            ProductColorRepository colors,
            InventoryStockRepository inventory,
            OrderItemRepository orderItems,
            SizeSetRepository sizeSets,
            SizeOptionRepository sizeOptions,
            SizeSetTemplateRepository setTemplates,
            BCryptPasswordEncoder passwordEncoder,
            AuthService authService,
            FileStorageService fileStorage
    ) {
        this.businesses = businesses;
        this.users = users;
        this.categories = categories;
        this.products = products;
        this.colors = colors;
        this.inventory = inventory;
        this.orderItems = orderItems;
        this.sizeSets = sizeSets;
        this.sizeOptions = sizeOptions;
        this.setTemplates = setTemplates;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.fileStorage = fileStorage;
    }

    public Business business(Long id) {
        return businesses.findById(id).orElseThrow();
    }

    public List<UserAccount> customers(Business business) {
        return users.findByBusinessAndRoleOrderByName(business, Role.CUSTOMER);
    }

    @Transactional
    public void saveCustomer(Business business, Long id, String name, String companyName, String phone, String pin, int tier, boolean active, String address) {
        UserAccount customer = id == null
                ? users.findByBusinessAndPhone(business, authService.normalizePhone(phone)).orElseGet(UserAccount::new)
                : users.findById(id).orElseThrow();
        customer.setBusiness(business);
        customer.setName(name);
        customer.setCompanyName(companyName);
        customer.setPhone(authService.normalizePhone(phone));
        customer.setTier(Math.max(1, Math.min(3, tier)));
        customer.setActive(active);
        customer.setAddress(address);
        customer.setRole(Role.CUSTOMER);
        if (pin != null && !pin.isBlank()) {
            customer.setPinHash(passwordEncoder.encode(pin));
        }
        if (customer.getPinHash() == null) {
            customer.setPinHash(passwordEncoder.encode("1234"));
        }
        users.save(customer);
    }

    public List<Category> categories(Business business) {
        return categories.findByBusinessOrderByName(business);
    }

    public List<SizeOption> sizes(Business business) {
        return sizeOptions.findByBusinessOrderByLabel(business);
    }

    public List<SizeSetTemplate> setTemplates(Business business) {
        return setTemplates.findByBusinessOrderByName(business);
    }

    @Transactional
    public void saveCategory(Business business, Long id, String name) {
        String normalizedName = normalizeRequired(name, "Category name is required.");
        categories.findByBusinessAndNameIgnoreCase(business, normalizedName)
                .filter(existing -> id == null || !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Category already exists.");
                });
        Category category = id == null ? new Category() : categories.findById(id).orElseThrow();
        if (category.getBusiness() != null && !category.getBusiness().getId().equals(business.getId())) {
            throw new IllegalArgumentException("Category does not belong to this business.");
        }
        category.setBusiness(business);
        category.setName(normalizedName);
        categories.save(category);
    }

    @Transactional
    public void saveSize(Business business, Long id, String label) {
        String normalizedLabel = normalizeRequired(label, "Size is required.");
        sizeOptions.findByBusinessAndLabelIgnoreCase(business, normalizedLabel)
                .filter(existing -> id == null || !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Size already exists.");
                });
        SizeOption size = id == null ? new SizeOption() : sizeOptions.findById(id).orElseThrow();
        if (size.getBusiness() != null && !size.getBusiness().getId().equals(business.getId())) {
            throw new IllegalArgumentException("Size does not belong to this business.");
        }
        size.setBusiness(business);
        size.setLabel(normalizedLabel);
        sizeOptions.save(size);
    }

    @Transactional
    public void saveSetTemplate(Business business, Long id, String name, List<Long> sizeIds) {
        String normalizedName = normalizeRequired(name, "Set name is required.");
        if (sizeIds == null || sizeIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one size for the set.");
        }
        setTemplates.findByBusinessAndNameIgnoreCase(business, normalizedName)
                .filter(existing -> id == null || !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Set name already exists.");
                });

        Map<Long, SizeOption> sizesById = sizeOptions.findByBusinessOrderByLabel(business).stream()
                .collect(Collectors.toMap(SizeOption::getId, Function.identity()));
        List<String> labels = new ArrayList<>();
        for (Long sizeId : sizeIds) {
            SizeOption size = sizesById.get(sizeId);
            if (size == null) {
                throw new IllegalArgumentException("Selected size does not belong to this business.");
            }
            labels.add(size.getLabel());
        }
        String sizeLabels = normalizeCsv(String.join(",", labels));

        SizeSetTemplate template = id == null ? new SizeSetTemplate() : setTemplates.findById(id).orElseThrow();
        if (template.getBusiness() != null && !template.getBusiness().getId().equals(business.getId())) {
            throw new IllegalArgumentException("Set does not belong to this business.");
        }
        template.setBusiness(business);
        template.setName(normalizedName);
        template.setSizeLabels(sizeLabels);
        SizeSetTemplate saved = setTemplates.save(template);

        sizeSets.findByTemplateId(saved.getId()).forEach(productSet -> {
            productSet.setName(saved.getName());
            productSet.setSizeLabels(saved.getSizeLabels());
            sizeSets.save(productSet);
        });
    }

    public List<Product> products(Business business) {
        return products.findByBusinessOrderByCreatedAtDescIdDesc(business);
    }

    @Transactional(readOnly = true)
    public List<Product> products(
            Business business,
            String query,
            Long categoryId,
            String colorName,
            String setName,
            boolean inStockOnly,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String status,
            String sort
    ) {
        String normalizedQuery = normalizeFilter(query);
        String normalizedColor = normalizeFilter(colorName);
        String normalizedSet = normalizeFilter(setName);
        String normalizedStatus = normalizeFilter(status);
        List<Product> filtered = products.findByBusinessOrderById(business).stream()
                .filter(product -> normalizedQuery.isBlank()
                        || contains(product.getProductCode(), normalizedQuery)
                        || contains(product.getName(), normalizedQuery))
                .filter(product -> categoryId == null
                        || (product.getCategory() != null && product.getCategory().getId().equals(categoryId)))
                .filter(product -> minPrice == null || product.getPrice().compareTo(minPrice) >= 0)
                .filter(product -> maxPrice == null || product.getPrice().compareTo(maxPrice) <= 0)
                .filter(product -> normalizedStatus.isBlank()
                        || ("active".equals(normalizedStatus) && product.isActive())
                        || ("inactive".equals(normalizedStatus) && !product.isActive()))
                .filter(product -> normalizedColor.isBlank() || colors.findByProductOrderByName(product).stream()
                        .anyMatch(color -> contains(color.getName(), normalizedColor)))
                .filter(product -> normalizedSet.isBlank() || sizeSets.findByProductOrderByName(product).stream()
                        .anyMatch(sizeSet -> contains(sizeSet.getName(), normalizedSet)
                                || contains(sizeSet.getSizeLabels(), normalizedSet)
                                || contains(sizeSet.getName() + " (" + sizeSet.getSizeLabels() + ")", normalizedSet)))
                .filter(product -> !inStockOnly || hasAvailableSet(
                        colors.findByProductOrderByName(product).stream().filter(ProductColor::isActive).toList(),
                        sizeSets.findByProductOrderByName(product)))
                .collect(Collectors.toCollection(ArrayList::new));
        filtered.sort(adminProductComparator(sort));
        return filtered;
    }

    @Transactional(readOnly = true)
    public List<String> productColorOptions(Business business) {
        return products.findByBusinessOrderByName(business).stream()
                .flatMap(product -> colors.findByProductOrderByName(product).stream())
                .map(ProductColor::getName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> productSetOptions(Business business) {
        return products.findByBusinessOrderByName(business).stream()
                .flatMap(product -> sizeSets.findByProductOrderByName(product).stream())
                .map(sizeSet -> sizeSet.getName() + " (" + sizeSet.getSizeLabels() + ")")
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional
    public Product saveProduct(Business business, Long id, String productCode, String name, String description, Long categoryId, BigDecimal price, int minTier, boolean active, List<Long> setTemplateIds) {
        Product product = id == null ? new Product() : products.findById(id).orElseThrow();
        Category category = categories.findById(categoryId).orElseThrow();
        if (product.getBusiness() != null && !product.getBusiness().getId().equals(business.getId())) {
            throw new IllegalArgumentException("Product does not belong to this business.");
        }
        if (!category.getBusiness().getId().equals(business.getId())) {
            throw new IllegalArgumentException("Category does not belong to this business.");
        }
        if (setTemplateIds == null || setTemplateIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one fixed size set.");
        }
        String normalizedProductCode = normalizeProductCode(productCode);
        if (normalizedProductCode.isBlank()) {
            normalizedProductCode = nextProductCode(business);
        }
        String finalProductCode = normalizedProductCode;
        products.findByBusinessAndProductCodeIgnoreCase(business, finalProductCode)
                .filter(existing -> product.getId() == null || !existing.getId().equals(product.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Product ID already exists for this business.");
                });
        product.setBusiness(business);
        product.setCategory(category);
        product.setProductCode(finalProductCode);
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setMinimumTierRequired(Math.max(1, Math.min(3, minTier)));
        product.setActive(active);
        Product saved = products.save(product);

        sizeSets.findByProductOrderByName(saved).forEach(sizeSets::delete);
        Map<Long, SizeSetTemplate> templatesById = setTemplates.findByBusinessOrderByName(business).stream()
                .collect(Collectors.toMap(SizeSetTemplate::getId, Function.identity()));
        for (Long templateId : setTemplateIds) {
            SizeSetTemplate template = templatesById.get(templateId);
            if (template == null) {
                throw new IllegalArgumentException("Selected set does not belong to this business.");
            }
            SizeSet sizeSet = new SizeSet();
            sizeSet.setProduct(saved);
            sizeSet.setTemplateId(template.getId());
            sizeSet.setName(template.getName());
            sizeSet.setSizeLabels(template.getSizeLabels());
            sizeSets.save(sizeSet);
        }
        if (saved.isActive()) {
            String activationIssue = activationIssue(saved);
            if (activationIssue != null) {
                throw new IllegalArgumentException("Cannot activate product yet. " + activationIssue);
            }
        }
        return saved;
    }

    public String nextProductCode(Business business) {
        int max = products.findByBusinessOrderById(business).stream()
                .map(Product::getProductCode)
                .filter(Objects::nonNull)
                .map(this::normalizeProductCode)
                .filter(code -> code.matches("P\\d+"))
                .mapToInt(code -> Integer.parseInt(code.substring(1)))
                .max()
                .orElse(0);
        String code;
        do {
            code = String.format("P%04d", ++max);
        } while (products.findByBusinessAndProductCodeIgnoreCase(business, code).isPresent());
        return code;
    }

    @Transactional
    public void addColor(Product product, String colorName, MultipartFile image, List<String> sizeLabels, List<String> quantities) {
        ProductColor color = new ProductColor();
        color.setProduct(product);
        color.setName(colorName);
        color.setImagePath(fileStorage.store(image, "products", product.getBusiness().getId()));
        ProductColor savedColor = colors.save(color);
        updateInventory(savedColor, sizeLabels, quantities);
    }

    @Transactional
    public void activateProduct(Business business, Long productId) {
        Product product = products.findById(productId).orElseThrow();
        if (!product.getBusiness().getId().equals(business.getId())) {
            throw new IllegalArgumentException("Product does not belong to this business.");
        }
        String issue = activationIssue(product);
        if (issue != null) {
            throw new IllegalArgumentException("Cannot activate product yet. " + issue);
        }
        product.setActive(true);
        products.save(product);
    }

    @Transactional
    public void updateInventory(ProductColor productColor, List<String> sizeLabels, List<String> quantities) {
        Map<String, InventoryStock> existing = inventory.findByProductColorOrderBySizeLabel(productColor).stream()
                .collect(Collectors.toMap(stock -> normalizeSizeKey(stock.getSizeLabel()), Function.identity(), (left, right) -> left));
        if (sizeLabels == null) {
            return;
        }
        for (int index = 0; index < sizeLabels.size(); index++) {
            String sizeLabel = normalizeSizeLabel(sizeLabels.get(index));
            if (sizeLabel.isBlank()) {
                continue;
            }
            InventoryStock stock = existing.getOrDefault(normalizeSizeKey(sizeLabel), new InventoryStock());
            stock.setProductColor(productColor);
            stock.setSizeLabel(sizeLabel);
            stock.setQuantity(quantityAt(quantities, index));
            inventory.save(stock);
        }
    }

    public Product product(Long id) {
        return products.findById(id).orElseThrow();
    }

    public ProductColor color(Long id) {
        return colors.findById(id).orElseThrow();
    }

    public List<ProductColor> colors(Product product) {
        return colors.findByProductOrderByName(product);
    }

    public List<ProductColor> colors(Business business) {
        return products.findByBusinessOrderByName(business).stream()
                .flatMap(product -> colors.findByProductOrderByName(product).stream())
                .toList();
    }

    public List<SizeSet> sizeSets(Business business) {
        return products.findByBusinessOrderByName(business).stream()
                .flatMap(product -> sizeSets.findByProductOrderByName(product).stream())
                .toList();
    }

    public List<InventoryStock> inventory(ProductColor color) {
        return inventory.findByProductColorOrderBySizeLabel(color);
    }

    @Transactional
    public Long deleteColor(Business business, Long colorId) {
        ProductColor color = colors.findById(colorId).orElseThrow();
        Product product = color.getProduct();
        if (!product.getBusiness().getId().equals(business.getId())) {
            throw new IllegalArgumentException("Colour does not belong to this business.");
        }
        if (!canDeleteColor(color)) {
            throw new IllegalStateException("This colour cannot be deleted because it exists in one or more orders.");
        }
        Long productId = product.getId();
        inventory.deleteAll(inventory.findByProductColorOrderBySizeLabel(color));
        fileStorage.deleteByPath(color.getImagePath());
        colors.delete(color);
        return productId;
    }

    @Transactional
    public void deleteProduct(Business business, Long productId) {
        Product product = products.findById(productId).orElseThrow();
        if (!product.getBusiness().getId().equals(business.getId())) {
            throw new IllegalArgumentException("Product does not belong to this business.");
        }
        if (!canDeleteProduct(product)) {
            throw new IllegalStateException("This product cannot be deleted because it exists in one or more orders. Mark it inactive instead.");
        }
        for (ProductColor color : colors.findByProductOrderByName(product)) {
            inventory.deleteAll(inventory.findByProductColorOrderBySizeLabel(color));
            fileStorage.deleteByPath(color.getImagePath());
            colors.delete(color);
        }
        sizeSets.deleteAll(sizeSets.findByProductOrderByName(product));
        products.delete(product);
    }

    @Transactional(readOnly = true)
    public boolean canDeleteColor(ProductColor color) {
        return color != null && color.getId() != null && !orderItems.existsByProductColorId(color.getId());
    }

    @Transactional(readOnly = true)
    public boolean canDeleteProduct(Product product) {
        if (product == null || product.getId() == null) {
            return false;
        }
        return colors.findByProductOrderByName(product).stream()
                .noneMatch(this::hasOrderHistory);
    }

    @Transactional(readOnly = true)
    public Map<Long, Boolean> deletableProducts(List<Product> businessProducts) {
        Map<Long, Boolean> deletable = new LinkedHashMap<>();
        for (Product product : businessProducts) {
            deletable.put(product.getId(), canDeleteProduct(product));
        }
        return deletable;
    }

    @Transactional(readOnly = true)
    public Map<Long, Boolean> deletableColors(List<ProductColor> productColors) {
        Map<Long, Boolean> deletable = new LinkedHashMap<>();
        for (ProductColor color : productColors) {
            deletable.put(color.getId(), canDeleteColor(color));
        }
        return deletable;
    }

    private boolean hasOrderHistory(ProductColor color) {
        return color.getId() != null && orderItems.existsByProductColorId(color.getId());
    }

    public Map<Long, String> inventorySummaryByProduct(List<Product> businessProducts) {
        Map<Long, String> summaries = new LinkedHashMap<>();
        for (Product product : businessProducts) {
            Map<String, Integer> totalsBySize = new LinkedHashMap<>();
            Map<String, String> labelsByKey = new LinkedHashMap<>();
            for (String sizeLabel : productSizeLabels(product)) {
                String key = normalizeSizeKey(sizeLabel);
                labelsByKey.putIfAbsent(key, sizeLabel);
                totalsBySize.putIfAbsent(key, 0);
            }
            for (ProductColor color : colors.findByProductOrderByName(product)) {
                for (InventoryStock stock : inventory.findByProductColorOrderBySizeLabel(color)) {
                    String sizeLabel = normalizeSizeLabel(stock.getSizeLabel());
                    if (sizeLabel.isBlank()) {
                        continue;
                    }
                    String key = normalizeSizeKey(sizeLabel);
                    labelsByKey.putIfAbsent(key, sizeLabel);
                    totalsBySize.merge(key, stock.getQuantity(), Integer::sum);
                }
            }
            String summary = totalsBySize.entrySet().stream()
                    .map(entry -> labelsByKey.getOrDefault(entry.getKey(), entry.getKey()) + ": " + entry.getValue())
                    .collect(Collectors.joining(", "));
            summaries.put(product.getId(), summary.isBlank() ? "-" : summary);
        }
        return summaries;
    }

    public List<SizeSet> sizeSets(Product product) {
        return sizeSets.findByProductOrderByName(product);
    }

    public List<String> productSizeLabels(Product product) {
        Map<String, String> labels = new LinkedHashMap<>();
        sizeSets(product).forEach(sizeSet -> Arrays.stream((sizeSet.getSizeLabels() == null ? "" : sizeSet.getSizeLabels()).split(","))
                .map(this::normalizeSizeLabel)
                .filter(label -> !label.isBlank())
                .forEach(label -> labels.putIfAbsent(normalizeSizeKey(label), label)));
        return new ArrayList<>(labels.values());
    }

    public Map<Long, Map<String, Integer>> stockQuantityByColor(List<ProductColor> productColors, List<String> sizeLabels) {
        Map<Long, Map<String, Integer>> stockByColor = new LinkedHashMap<>();
        for (ProductColor color : productColors) {
            Map<String, Integer> existing = inventory.findByProductColorOrderBySizeLabel(color).stream()
                    .collect(Collectors.toMap(stock -> normalizeSizeKey(stock.getSizeLabel()), InventoryStock::getQuantity, (left, right) -> left));
            Map<String, Integer> quantitiesByLabel = new LinkedHashMap<>();
            for (String sizeLabel : sizeLabels) {
                quantitiesByLabel.put(sizeLabel, existing.getOrDefault(normalizeSizeKey(sizeLabel), 0));
            }
            stockByColor.put(color.getId(), quantitiesByLabel);
        }
        return stockByColor;
    }

    public List<Long> selectedSetTemplateIds(Product product) {
        return sizeSets.findByProductOrderByName(product).stream()
                .map(SizeSet::getTemplateId)
                .filter(Objects::nonNull)
                .toList();
    }

    public Map<Long, String> activationIssues(List<Product> businessProducts) {
        Map<Long, String> issues = new LinkedHashMap<>();
        for (Product product : businessProducts) {
            String issue = activationIssue(product);
            if (issue != null) {
                issues.put(product.getId(), issue);
            }
        }
        return issues;
    }

    public String activationIssue(Product product) {
        List<SizeSet> productSizeSets = sizeSets.findByProductOrderByName(product);
        if (productSizeSets.isEmpty()) {
            return "Select at least one fixed size set before activating this product.";
        }
        List<ProductColor> activeColors = colors.findByProductOrderByName(product).stream()
                .filter(ProductColor::isActive)
                .toList();
        if (activeColors.isEmpty()) {
            return "Add at least one colour before activating this product.";
        }
        if (!hasAvailableSet(activeColors, productSizeSets)) {
            return "Add stock for every size in at least one selected fixed size set before activating this product.";
        }
        return null;
    }

    private boolean hasAvailableSet(List<ProductColor> activeColors, List<SizeSet> productSizeSets) {
        for (ProductColor color : activeColors) {
            Map<String, Integer> availableBySize = inventory.findByProductColorOrderBySizeLabel(color).stream()
                    .collect(Collectors.toMap(stock -> normalizeSizeKey(stock.getSizeLabel()), InventoryStock::getQuantity, (left, right) -> left));
            for (SizeSet sizeSet : productSizeSets) {
                boolean setAvailable = sizes(sizeSet.getSizeLabels()).stream()
                        .allMatch(size -> availableBySize.getOrDefault(normalizeSizeKey(size), 0) > 0);
                if (setAvailable) {
                    return true;
                }
            }
        }
        return false;
    }

    private int quantityAt(List<String> quantities, int index) {
        if (quantities == null || index >= quantities.size()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(quantities.get(index).trim()));
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private String normalizeSizeLabel(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeSizeKey(String value) {
        return normalizeSizeLabel(value).toUpperCase();
    }

    private List<String> sizes(String sizeLabels) {
        return Arrays.stream(sizeLabels == null ? new String[0] : sizeLabels.split(","))
                .map(this::normalizeSizeLabel)
                .filter(size -> !size.isBlank())
                .toList();
    }

    private Comparator<Product> adminProductComparator(String sort) {
        String normalizedSort = normalizeFilter(sort);
        Comparator<Product> createdDesc = Comparator
                .comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(Product::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        return switch (normalizedSort) {
            case "productid" -> Comparator.comparing(Product::getProductCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(Product::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "name" -> Comparator.comparing(Product::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(Product::getProductCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "price_asc" -> Comparator.comparing(Product::getPrice, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Product::getProductCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "price_desc" -> Comparator.comparing(Product::getPrice, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Product::getProductCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "category" -> Comparator.comparing(this::categoryName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(Product::getProductCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "status" -> Comparator.comparing(Product::isActive).reversed()
                    .thenComparing(createdDesc);
            default -> createdDesc;
        };
    }

    private String categoryName(Product product) {
        return product.getCategory() == null ? null : product.getCategory().getName();
    }

    private boolean contains(String value, String normalizedFilter) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedFilter);
    }

    private String normalizeFilter(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeProductCode(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "-").toUpperCase();
    }

    private String normalizeCsv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(size -> !size.isBlank())
                .collect(Collectors.joining(","));
    }

    private String normalizeRequired(String value, String message) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
