package com.dragon.config;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window counter for unfriendly voice events per user.
 * Thread-safe: each user has their own Deque, and all mutations
 * are synchronized on that Deque.
 */
@Component
public class BehaviorTracker {
    public static final int WARNING_THRESHOLD = 3;
    private static final long WINDOW_SECONDS = 120;

    private final Map<String, Deque<Instant>> eventWindows = new ConcurrentHashMap<>();

    /**
     * Records an unfriendly event for the user and returns whether
     * the warning threshold has been reached within the window.
     */
    public boolean recordAndCheck(String userId) {
        Deque<Instant> window = eventWindows.computeIfAbsent(userId, _ -> new ArrayDeque<>());
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(WINDOW_SECONDS);

        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }
            window.addLast(now);
            return window.size() >= WARNING_THRESHOLD;
        }
    }

    /**
     * Resets the user's event window after a warning has been issued,
     * so they aren't spammed with repeated TTS warnings.
     */
    public void reset(String userId) {
        eventWindows.remove(userId);
    }
}