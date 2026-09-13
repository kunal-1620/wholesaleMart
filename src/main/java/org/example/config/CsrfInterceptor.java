package org.example.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

@Component
public class CsrfInterceptor implements HandlerInterceptor {
    public static final String REQUEST_ATTRIBUTE = "csrfToken";
    private static final String SESSION_ATTRIBUTE = "csrfToken";
    private static final String PARAMETER = "_csrf";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = ensureToken(request.getSession());
        request.setAttribute(REQUEST_ATTRIBUTE, token);

        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }

        String submitted = request.getParameter(PARAMETER);
        if (submitted == null || !constantTimeEquals(token, submitted)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid form token. Please refresh the page and try again.");
            return false;
        }
        return true;
    }

    private String ensureToken(HttpSession session) {
        Object existing = session.getAttribute(SESSION_ATTRIBUTE);
        if (existing instanceof String token && !token.isBlank()) {
            return token;
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SESSION_ATTRIBUTE, token);
        return token;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected.length() != actual.length()) {
            return false;
        }
        int result = 0;
        for (int index = 0; index < expected.length(); index++) {
            result |= expected.charAt(index) ^ actual.charAt(index);
        }
        return result == 0;
    }
}
