package org.example.service;

import jakarta.servlet.http.HttpSession;
import org.example.domain.Business;
import org.example.domain.Role;
import org.example.domain.UserAccount;
import org.example.repo.UserAccountRepository;
import org.example.session.CurrentUser;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {
    private final UserAccountRepository users;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UserAccountRepository users, BCryptPasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<CurrentUser> login(String phone, String pin, HttpSession session) {
        String normalizedPhone = normalizePhone(phone);
        return users.findByPhone(normalizedPhone).stream()
                .filter(UserAccount::isActive)
                .filter(user -> passwordEncoder.matches(pin, user.getPinHash()))
                .filter(user -> user.getRole() == Role.PLATFORM_ADMIN)
                .findFirst()
                .map(user -> setCurrentUser(user, session));
    }

    public Optional<CurrentUser> login(Business business, String phone, String pin, HttpSession session) {
        String normalizedPhone = normalizePhone(phone);
        return users.findByBusinessAndPhone(business, normalizedPhone).stream()
                .filter(UserAccount::isActive)
                .filter(user -> passwordEncoder.matches(pin, user.getPinHash()))
                .filter(user -> user.getRole() != Role.PLATFORM_ADMIN)
                .findFirst()
                .map(user -> setCurrentUser(user, session));
    }

    private CurrentUser setCurrentUser(UserAccount user, HttpSession session) {
        CurrentUser currentUser = new CurrentUser(
                user.getId(),
                user.getBusiness().getId(),
                user.getBusiness().getSlug(),
                user.getName(),
                user.getRole(),
                user.getTier()
        );
        session.setAttribute("currentUser", currentUser);
        return currentUser;
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    public String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9+]", "");
    }
}
