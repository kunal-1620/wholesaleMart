package org.example.service;

import jakarta.servlet.http.HttpSession;
import org.example.domain.Business;
import org.example.domain.Role;
import org.example.domain.UserAccount;
import org.example.repo.BusinessRepository;
import org.example.repo.UserAccountRepository;
import org.example.session.CurrentUser;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class PlatformService {
    private final BusinessRepository businesses;
    private final UserAccountRepository users;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final FileStorageService fileStorage;

    public PlatformService(
            BusinessRepository businesses,
            UserAccountRepository users,
            BCryptPasswordEncoder passwordEncoder,
            AuthService authService,
            FileStorageService fileStorage
    ) {
        this.businesses = businesses;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.fileStorage = fileStorage;
    }

    public List<Business> businesses() {
        return businesses.findAllByOrderByName();
    }

    public Business business(Long id) {
        return businesses.findById(id).orElseThrow();
    }

    @Transactional
    public Business saveBusiness(Long id, String name, String slug, String contactPhone, String paymentInstructions, MultipartFile logo) {
        Business business = id == null ? new Business() : businesses.findById(id).orElseThrow();
        String normalizedSlug = normalizeSlug(slug);
        businesses.findBySlug(normalizedSlug)
                .filter(existing -> id == null || !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Business slug is already in use.");
                });
        business.setName(name);
        business.setSlug(normalizedSlug);
        business.setContactPhone(contactPhone);
        business.setPaymentInstructions(paymentInstructions);
        if ((business.getLogoPath() == null || business.getLogoPath().isBlank()) && (logo == null || logo.isEmpty())) {
            throw new IllegalArgumentException("Business logo is required.");
        }
        business = businesses.save(business);
        String logoPath = fileStorage.store(logo, "business-logos", business.getId());
        if (logoPath != null) {
            business.setLogoPath(logoPath);
        }
        return businesses.save(business);
    }

    public List<UserAccount> owners(Business business) {
        return users.findByBusinessAndRoleInOrderByName(business, List.of(Role.BUSINESS_ADMIN, Role.STAFF, Role.PLATFORM_ADMIN));
    }

    @Transactional
    public void saveOwner(Business business, Long id, String name, String phone, String pin, Role role, boolean active) {
        Role businessRole = role == Role.STAFF ? Role.STAFF : Role.BUSINESS_ADMIN;
        UserAccount owner = id == null
                ? users.findByBusinessAndPhone(business, authService.normalizePhone(phone)).orElseGet(UserAccount::new)
                : users.findById(id).orElseThrow();
        owner.setBusiness(business);
        owner.setName(name);
        owner.setPhone(authService.normalizePhone(phone));
        owner.setRole(businessRole);
        owner.setTier(1);
        owner.setCompanyName(business.getName());
        owner.setActive(active);
        if (pin != null && !pin.isBlank()) {
            owner.setPinHash(passwordEncoder.encode(pin));
        }
        if (owner.getPinHash() == null) {
            owner.setPinHash(passwordEncoder.encode("1234"));
        }
        users.save(owner);
    }

    public void switchBusiness(HttpSession session, Long businessId) {
        CurrentUser currentUser = (CurrentUser) session.getAttribute("currentUser");
        if (currentUser == null || currentUser.role() != Role.PLATFORM_ADMIN) {
            throw new IllegalStateException("Only platform admins can switch businesses.");
        }
        session.setAttribute("currentUser", new CurrentUser(
                currentUser.userId(),
                businessId,
                business(businessId).getSlug(),
                currentUser.name(),
                currentUser.role(),
                currentUser.tier()
        ));
    }

    public String normalizeSlug(String value) {
        String slug = value == null ? "" : value.trim().toLowerCase();
        slug = slug.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            throw new IllegalArgumentException("Business slug is required.");
        }
        return slug;
    }
}
