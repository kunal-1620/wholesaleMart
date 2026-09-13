package org.example.session;

import org.example.domain.Role;

public record CurrentUser(Long userId, Long businessId, String businessSlug, String name, Role role, int tier) {
    public boolean isAdmin() {
        return role == Role.BUSINESS_ADMIN || role == Role.STAFF || role == Role.PLATFORM_ADMIN;
    }

    public boolean isPlatformAdmin() {
        return role == Role.PLATFORM_ADMIN;
    }

    public boolean isCustomer() {
        return role == Role.CUSTOMER;
    }
}
