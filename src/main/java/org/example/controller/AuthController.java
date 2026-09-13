package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.domain.Business;
import org.example.domain.Role;
import org.example.repo.BusinessRepository;
import org.example.service.AuthService;
import org.example.session.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {
    private final AuthService authService;
    private final BusinessRepository businesses;

    public AuthController(AuthService authService, BusinessRepository businesses) {
        this.authService = authService;
        this.businesses = businesses;
    }

    @GetMapping("/")
    public String home(HttpSession session) {
        CurrentUser user = (CurrentUser) session.getAttribute("currentUser");
        if (user == null) {
            return "redirect:/login";
        }
        if (user.role() == Role.PLATFORM_ADMIN) {
            return "redirect:/platform";
        }
        return user.role() == Role.CUSTOMER
                ? "redirect:/b/" + user.businessSlug() + "/shop"
                : "redirect:/b/" + user.businessSlug() + "/admin";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("loginAction", "/login");
        return "login";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam String phone, @RequestParam String pin, HttpSession session, Model model) {
        try {
            return authService.login(phone, pin, session)
                    .map(user -> "redirect:/platform")
                    .orElseGet(() -> {
                        model.addAttribute("loginAction", "/login");
                        model.addAttribute("error", "Invalid platform admin phone/PIN or inactive account.");
                        return "login";
                    });
        } catch (IllegalStateException exception) {
            model.addAttribute("loginAction", "/login");
            model.addAttribute("error", exception.getMessage());
            return "login";
        }
    }

    @GetMapping("/b/{slug}/login")
    public String businessLogin(@PathVariable String slug, Model model) {
        Business business = business(slug);
        model.addAttribute("business", business);
        model.addAttribute("loginAction", "/b/" + business.getSlug() + "/login");
        return "login";
    }

    @PostMapping("/b/{slug}/login")
    public String doBusinessLogin(@PathVariable String slug, @RequestParam String phone, @RequestParam String pin, HttpSession session, Model model) {
        Business business = business(slug);
        try {
            return authService.login(business, phone, pin, session)
                    .map(user -> user.role() == Role.CUSTOMER
                            ? "redirect:/b/" + business.getSlug() + "/shop"
                            : "redirect:/b/" + business.getSlug() + "/admin")
                    .orElseGet(() -> businessLoginError(business, "Invalid phone/PIN for this business or inactive account.", model));
        } catch (IllegalStateException exception) {
            return businessLoginError(business, exception.getMessage(), model);
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        authService.logout(session);
        return "redirect:/login";
    }

    @PostMapping("/b/{slug}/logout")
    public String businessLogout(@PathVariable String slug, HttpSession session) {
        authService.logout(session);
        return "redirect:/b/" + slug + "/login";
    }

    private Business business(String slug) {
        return businesses.findBySlug(slug).orElseThrow();
    }

    private String businessLoginError(Business business, String error, Model model) {
        model.addAttribute("business", business);
        model.addAttribute("loginAction", "/b/" + business.getSlug() + "/login");
        model.addAttribute("error", error);
        return "login";
    }
}
