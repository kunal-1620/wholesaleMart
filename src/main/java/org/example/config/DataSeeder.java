package org.example.config;

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
import org.example.repo.ProductColorRepository;
import org.example.repo.ProductRepository;
import org.example.repo.SizeOptionRepository;
import org.example.repo.SizeSetRepository;
import org.example.repo.SizeSetTemplateRepository;
import org.example.repo.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DataSeeder implements CommandLineRunner {
    private final BusinessRepository businesses;
    private final UserAccountRepository users;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductColorRepository colors;
    private final InventoryStockRepository inventory;
    private final SizeSetRepository sizeSets;
    private final SizeOptionRepository sizeOptions;
    private final SizeSetTemplateRepository setTemplates;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final boolean seedDemoData;
    private final String bootstrapBusinessName;
    private final String bootstrapBusinessSlug;
    private final String bootstrapContactPhone;
    private final String bootstrapPaymentInstructions;
    private final String bootstrapPlatformAdminName;
    private final String bootstrapPlatformAdminPhone;
    private final String bootstrapPlatformAdminPin;

    public DataSeeder(
            BusinessRepository businesses,
            UserAccountRepository users,
            CategoryRepository categories,
            ProductRepository products,
            ProductColorRepository colors,
            InventoryStockRepository inventory,
            SizeSetRepository sizeSets,
            SizeOptionRepository sizeOptions,
            SizeSetTemplateRepository setTemplates,
            BCryptPasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate,
            @Value("${app.seed-demo-data:false}") boolean seedDemoData,
            @Value("${app.bootstrap.business-name}") String bootstrapBusinessName,
            @Value("${app.bootstrap.business-slug}") String bootstrapBusinessSlug,
            @Value("${app.bootstrap.contact-phone}") String bootstrapContactPhone,
            @Value("${app.bootstrap.payment-instructions}") String bootstrapPaymentInstructions,
            @Value("${app.bootstrap.platform-admin-name}") String bootstrapPlatformAdminName,
            @Value("${app.bootstrap.platform-admin-phone}") String bootstrapPlatformAdminPhone,
            @Value("${app.bootstrap.platform-admin-pin}") String bootstrapPlatformAdminPin
    ) {
        this.businesses = businesses;
        this.users = users;
        this.categories = categories;
        this.products = products;
        this.colors = colors;
        this.inventory = inventory;
        this.sizeSets = sizeSets;
        this.sizeOptions = sizeOptions;
        this.setTemplates = setTemplates;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.seedDemoData = seedDemoData;
        this.bootstrapBusinessName = bootstrapBusinessName;
        this.bootstrapBusinessSlug = bootstrapBusinessSlug;
        this.bootstrapContactPhone = bootstrapContactPhone;
        this.bootstrapPaymentInstructions = bootstrapPaymentInstructions;
        this.bootstrapPlatformAdminName = bootstrapPlatformAdminName;
        this.bootstrapPlatformAdminPhone = bootstrapPlatformAdminPhone;
        this.bootstrapPlatformAdminPin = bootstrapPlatformAdminPin;
    }

    @Override
    public void run(String... args) {
        makeUserRoleColumnMigrationSafe();
        backfillBusinessSlugs();
        backfillProductCodes();
        backfillProductCreatedAt();
        backfillDefaultSizesAndSetTemplates();
        if (seedDemoData) {
            backfillDemoStaffUser();
        }
        if (businesses.count() > 0) {
            return;
        }

        if (!seedDemoData) {
            bootstrapPlatformAdmin();
            return;
        }

        Business business = new Business();
        business.setName("Agarwal Wholesale Clothing");
        business.setSlug("agarwal");
        business.setLogoPath("/placeholder-logo.svg");
        business.setContactPhone("+91 99999 99999");
        business.setPaymentInstructions("Pay by UPI/bank transfer, then upload transaction ID and screenshot. Owner verifies payment before stock is reduced.");
        business = businesses.save(business);
        ensureDefaultSizes(business);

        saveUser(business, "Platform Admin", "7000000000", "0000", Role.PLATFORM_ADMIN, 1, "Round1 Platform", true);
        saveUser(business, "Owner Admin", "9999999999", "1234", Role.BUSINESS_ADMIN, 1, "Owner", true);
        saveUser(business, "Staff User", "6666666666", "2222", Role.STAFF, 1, "Store Staff", true);
        saveUser(business, "Tier One Buyer", "8888888888", "1111", Role.CUSTOMER, 1, "Top Retailer", true);
        saveUser(business, "Tier Three Buyer", "7777777777", "3333", Role.CUSTOMER, 3, "Regular Retailer", true);

        Category shirts = new Category();
        shirts.setBusiness(business);
        shirts.setName("Shirts");
        shirts = categories.save(shirts);

        Product premium = product(business, shirts, "Premium Linen Shirt", "Fixed wholesale size sets with colour-wise inventory.", "650.00", 1);
        sizeSet(premium, "Set A", "36 (S),38 (M),40 (L)");
        sizeSet(premium, "Set B", "38 (M),40 (L),42 (XL)");
        color(premium, "Ivory", "36 (S)", 1, "38 (M)", 1, "40 (L)", 1, "42 (XL)", 0);
        color(premium, "Navy", "36 (S)", 8, "38 (M)", 8, "40 (L)", 8, "42 (XL)", 4);

        Product everyday = product(business, shirts, "Everyday Cotton Shirt", "Available for all tiers at the same price.", "420.00", 3);
        sizeSet(everyday, "Set A", "36 (S),38 (M),40 (L)");
        sizeSet(everyday, "Set C", "40 (L),42 (XL),44 (XXL)");
        color(everyday, "Sky Blue", "36 (S)", 12, "38 (M)", 12, "40 (L)", 10, "42 (XL)", 6, "44 (XXL)", 3);
        color(everyday, "Maroon", "36 (S)", 6, "38 (M)", 10, "40 (L)", 10, "42 (XL)", 4, "44 (XXL)", 2);
    }

    private void bootstrapPlatformAdmin() {
        if (bootstrapPlatformAdminPhone == null || bootstrapPlatformAdminPhone.isBlank()
                || bootstrapPlatformAdminPin == null || bootstrapPlatformAdminPin.isBlank()) {
            throw new IllegalStateException("Fresh production database requires APP_PLATFORM_ADMIN_PHONE and APP_PLATFORM_ADMIN_PIN.");
        }
        Business business = new Business();
        business.setName(blankToDefault(bootstrapBusinessName, "WholesaleMart Platform"));
        business.setSlug(uniqueSlug(blankToDefault(bootstrapBusinessSlug, "platform"), null));
        business.setLogoPath("/placeholder-logo.svg");
        business.setContactPhone(bootstrapContactPhone);
        business.setPaymentInstructions(blankToDefault(bootstrapPaymentInstructions, "Manual payment. Upload transaction proof after approval."));
        business = businesses.save(business);
        ensureDefaultSizes(business);
        saveUser(
                business,
                blankToDefault(bootstrapPlatformAdminName, "Platform Admin"),
                bootstrapPlatformAdminPhone,
                bootstrapPlatformAdminPin,
                Role.PLATFORM_ADMIN,
                1,
                business.getName(),
                true
        );
    }

    private void saveUser(Business business, String name, String phone, String pin, Role role, int tier, String company, boolean active) {
        UserAccount user = new UserAccount();
        user.setBusiness(business);
        user.setName(name);
        user.setPhone(phone);
        user.setPinHash(passwordEncoder.encode(pin));
        user.setRole(role);
        user.setTier(tier);
        user.setCompanyName(company);
        user.setActive(active);
        users.save(user);
    }

    private Product product(Business business, Category category, String name, String description, String price, int minimumTier) {
        Product product = new Product();
        product.setBusiness(business);
        product.setCategory(category);
        product.setProductCode(nextProductCode(business));
        product.setName(name);
        product.setDescription(description);
        product.setPrice(new BigDecimal(price));
        product.setMinimumTierRequired(minimumTier);
        product.setActive(true);
        return products.save(product);
    }

    private void sizeSet(Product product, String name, String sizes) {
        SizeSetTemplate template = findOrCreateSetTemplate(product.getBusiness(), name, sizes);
        SizeSet sizeSet = new SizeSet();
        sizeSet.setProduct(product);
        sizeSet.setTemplateId(template.getId());
        sizeSet.setName(name);
        sizeSet.setSizeLabels(template.getSizeLabels());
        sizeSets.save(sizeSet);
    }

    private void color(Product product, String colorName, Object... pairs) {
        ProductColor color = new ProductColor();
        color.setProduct(product);
        color.setName(colorName);
        color = colors.save(color);
        List<Object> values = List.of(pairs);
        for (int i = 0; i < values.size(); i += 2) {
            InventoryStock stock = new InventoryStock();
            stock.setProductColor(color);
            stock.setSizeLabel(values.get(i).toString());
            stock.setQuantity((Integer) values.get(i + 1));
            inventory.save(stock);
        }
    }

    private void backfillBusinessSlugs() {
        for (Business business : businesses.findAll()) {
            boolean changed = false;
            if (business.getSlug() == null || business.getSlug().isBlank()) {
                business.setSlug(uniqueSlug(business.getName(), business.getId()));
                changed = true;
            }
            if (business.getLogoPath() == null || business.getLogoPath().isBlank()) {
                business.setLogoPath("/placeholder-logo.svg");
                changed = true;
            }
            if (changed) {
                businesses.save(business);
            }
        }
    }

    private void backfillDemoStaffUser() {
        businesses.findBySlug("agarwal").ifPresent(business -> {
            if (users.findByBusinessAndPhone(business, "6666666666").isEmpty()) {
                saveUser(business, "Staff User", "6666666666", "2222", Role.STAFF, 1, "Store Staff", true);
            }
        });
    }

    private void backfillProductCodes() {
        for (Business business : businesses.findAll()) {
            List<Product> businessProducts = products.findByBusinessOrderById(business);
            Set<String> usedCodes = businessProducts.stream()
                    .map(Product::getProductCode)
                    .filter(code -> code != null && !code.isBlank())
                    .map(this::normalizeProductCode)
                    .collect(Collectors.toCollection(HashSet::new));
            int sequence = 1;
            for (Product product : businessProducts) {
                String existingCode = normalizeProductCode(product.getProductCode());
                if (!existingCode.isBlank()) {
                    if (!existingCode.equals(product.getProductCode())) {
                        product.setProductCode(existingCode);
                        products.save(product);
                    }
                    continue;
                }
                String newCode;
                do {
                    newCode = String.format("P%04d", sequence++);
                } while (usedCodes.contains(newCode));
                product.setProductCode(newCode);
                usedCodes.add(newCode);
                products.save(product);
            }
        }
    }

    private void backfillDefaultSizesAndSetTemplates() {
        for (Business business : businesses.findAll()) {
            ensureDefaultSizes(business);
            for (Product product : products.findByBusinessOrderByName(business)) {
                for (SizeSet sizeSet : sizeSets.findByProductOrderByName(product)) {
                    ensureSizesExist(business, sizeSet.getSizeLabels());
                    if (sizeSet.getTemplateId() == null) {
                        SizeSetTemplate template = findOrCreateSetTemplate(business, sizeSet.getName(), sizeSet.getSizeLabels());
                        sizeSet.setTemplateId(template.getId());
                        sizeSets.save(sizeSet);
                    }
                }
            }
        }
    }

    private void backfillProductCreatedAt() {
        for (Business business : businesses.findAll()) {
            List<Product> businessProducts = products.findByBusinessOrderById(business);
            LocalDateTime baseTime = LocalDateTime.now().minusMinutes(businessProducts.size());
            for (int index = 0; index < businessProducts.size(); index++) {
                Product product = businessProducts.get(index);
                if (product.getCreatedAt() == null) {
                    product.setCreatedAt(baseTime.plusMinutes(index));
                    products.save(product);
                }
            }
        }
    }

    private void ensureDefaultSizes(Business business) {
        List.of("34 (XS)", "36 (S)", "38 (M)", "40 (L)", "42 (XL)", "44 (XXL)")
                .forEach(label -> ensureSizeExists(business, label));
    }

    private void ensureSizesExist(Business business, String sizeLabels) {
        Arrays.stream(sizeLabels == null ? new String[0] : sizeLabels.split(","))
                .map(String::trim)
                .filter(label -> !label.isBlank())
                .forEach(label -> ensureSizeExists(business, label));
    }

    private void ensureSizeExists(Business business, String label) {
        sizeOptions.findByBusinessAndLabelIgnoreCase(business, label).orElseGet(() -> {
            SizeOption size = new SizeOption();
            size.setBusiness(business);
            size.setLabel(label);
            return sizeOptions.save(size);
        });
    }

    private SizeSetTemplate findOrCreateSetTemplate(Business business, String name, String sizes) {
        return setTemplates.findByBusinessAndNameIgnoreCase(business, name).orElseGet(() -> {
            ensureSizesExist(business, sizes);
            SizeSetTemplate template = new SizeSetTemplate();
            template.setBusiness(business);
            template.setName(name);
            template.setSizeLabels(normalizeCsv(sizes));
            return setTemplates.save(template);
        });
    }

    private String nextProductCode(Business business) {
        int max = products.findByBusinessOrderById(business).stream()
                .map(Product::getProductCode)
                .filter(code -> code != null && !code.isBlank())
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

    private void makeUserRoleColumnMigrationSafe() {
        try {
            jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN role VARCHAR(255) NOT NULL");
        } catch (Exception ignored) {
            // Best-effort local H2 migration for existing dev databases.
        }
    }

    private String uniqueSlug(String name, Long id) {
        String base = name == null ? "business" : name.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isBlank()) {
            base = "business";
        }
        String slug = base;
        int suffix = 2;
        while (businesses.findBySlug(slug).filter(existing -> !existing.getId().equals(id)).isPresent()) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }

    private String normalizeProductCode(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "-").toUpperCase();
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String normalizeCsv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(size -> !size.isBlank())
                .collect(Collectors.joining(","));
    }
}
