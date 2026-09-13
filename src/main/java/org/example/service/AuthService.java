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
    private final LoginAttemptService loginAttempts;

    public AuthService(UserAccountRepository users, BCryptPasswordEncoder passwordEncoder, LoginAttemptService loginAttempts) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
    }

    public Optional<CurrentUser> login(String phone, String pin, HttpSession session) {
        String normalizedPhone = normalizePhone(phone);
        String attemptKey = "platform:" + normalizedPhone;
        loginAttempts.ensureAllowed(attemptKey);
        Optional<CurrentUser> result = users.findByPhone(normalizedPhone).stream()
                .filter(UserAccount::isActive)
                .filter(user -> matchesPin(pin, user.getPinHash()))
                .filter(user -> user.getRole() == Role.PLATFORM_ADMIN)
                .findFirst()
                .map(user -> setCurrentUser(user, session));
        recordAttempt(attemptKey, result);
        return result;
    }

    public Optional<CurrentUser> login(Business business, String phone, String pin, HttpSession session) {
        String normalizedPhone = normalizePhone(phone);
        String attemptKey = "business:" + business.getId() + ":" + normalizedPhone;
        loginAttempts.ensureAllowed(attemptKey);
        Optional<CurrentUser> result = users.findByBusinessAndPhone(business, normalizedPhone).stream()
                .filter(UserAccount::isActive)
                .filter(user -> matchesPin(pin, user.getPinHash()))
                .filter(user -> user.getRole() != Role.PLATFORM_ADMIN)
                .findFirst()
                .map(user -> setCurrentUser(user, session));
        recordAttempt(attemptKey, result);
        return result;
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

    private boolean matchesPin(String pin, String pinHash) {
        return pin != null && pinHash != null && passwordEncoder.matches(pin, pinHash);
    }

    private void recordAttempt(String attemptKey, Optional<CurrentUser> result) {
        if (result.isPresent()) {
            loginAttempts.recordSuccess(attemptKey);
        } else {
            loginAttempts.recordFailure(attemptKey);
        }
    }
}
