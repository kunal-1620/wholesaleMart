package org.example.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public void ensureAllowed(String key) {
        AttemptState state = attempts.get(key);
        if (state == null) {
            return;
        }
        Instant now = Instant.now();
        if (state.lockedUntil != null && state.lockedUntil.isAfter(now)) {
            throw new IllegalStateException("Too many failed login attempts. Please try again after 15 minutes.");
        }
        if (state.firstFailureAt.plus(ATTEMPT_WINDOW).isBefore(now)) {
            attempts.remove(key);
        }
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    public void recordFailure(String key) {
        Instant now = Instant.now();
        attempts.compute(key, (ignored, state) -> {
            if (state == null || state.firstFailureAt.plus(ATTEMPT_WINDOW).isBefore(now)) {
                state = new AttemptState(0, now, null);
            }
            int failedCount = state.failedCount + 1;
            Instant lockedUntil = failedCount >= MAX_FAILED_ATTEMPTS ? now.plus(LOCK_DURATION) : state.lockedUntil;
            return new AttemptState(failedCount, state.firstFailureAt, lockedUntil);
        });
    }

    private record AttemptState(int failedCount, Instant firstFailureAt, Instant lockedUntil) {
    }
}
