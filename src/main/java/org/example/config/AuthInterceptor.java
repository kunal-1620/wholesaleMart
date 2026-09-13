package org.example.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.domain.Role;
import org.example.session.CurrentUser;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        CurrentUser currentUser = (CurrentUser) request.getSession().getAttribute("currentUser");
        if (path.startsWith("/files")) {
            if (currentUser == null) {
                response.sendRedirect("/login");
                return false;
            }
            return true;
        }
        if (path.startsWith("/b/")) {
            String[] parts = path.split("/");
            if (parts.length >= 4) {
                String slug = parts[2];
                String section = parts[3];
                if ("login".equals(section) || "logout".equals(section)) {
                    return true;
                }
                if (currentUser == null || !slug.equals(currentUser.businessSlug())) {
                    response.sendRedirect("/b/" + slug + "/login");
                    return false;
                }
                if ("admin".equals(section)) {
                    if (!(currentUser.role() == Role.BUSINESS_ADMIN || currentUser.role() == Role.STAFF || currentUser.role() == Role.PLATFORM_ADMIN)) {
                        response.sendRedirect("/b/" + slug + "/login");
                        return false;
                    }
                    return true;
                }
                if ("shop".equals(section) || "cart".equals(section) || "orders".equals(section)) {
                    if (currentUser.role() != Role.CUSTOMER) {
                        response.sendRedirect("/b/" + slug + "/login");
                        return false;
                    }
                    return true;
                }
            }
        }
        if (path.startsWith("/platform")) {
            if (currentUser == null || currentUser.role() != Role.PLATFORM_ADMIN) {
                response.sendRedirect("/login");
                return false;
            }
        }
        if (path.startsWith("/admin")) {
            if (currentUser == null || !(currentUser.role() == Role.BUSINESS_ADMIN || currentUser.role() == Role.STAFF || currentUser.role() == Role.PLATFORM_ADMIN)) {
                response.sendRedirect("/login");
                return false;
            }
            if (currentUser.businessSlug() != null && !currentUser.businessSlug().isBlank()) {
                response.sendRedirect("/b/" + currentUser.businessSlug() + path);
                return false;
            }
        }
        if (path.startsWith("/shop") || path.startsWith("/cart") || path.startsWith("/orders")) {
            if (currentUser == null || currentUser.role() != Role.CUSTOMER) {
                response.sendRedirect("/login");
                return false;
            }
            if (currentUser.businessSlug() != null && !currentUser.businessSlug().isBlank()) {
                response.sendRedirect("/b/" + currentUser.businessSlug() + path);
                return false;
            }
        }
        return true;
    }
}
