package com.dragon.utils.conversation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent in-process store for all conversation intelligence data.
 * One entry per userId — holds summary, follow-ups, relationship, and style profile.
 */
@Slf4j
@Component
public class ConversationStore {

    // ── Per-user data ─────────────────────────────────────────────────────

    private final Map<Long, String>               summaries     = new ConcurrentHashMap<>();
    private final Map<Long, List<FollowUpEntry>>  followUps     = new ConcurrentHashMap<>();
    private final Map<Long, RelationshipProfile>  relationships = new ConcurrentHashMap<>();
    private final Map<Long, StyleProfile>         styles        = new ConcurrentHashMap<>();
    private final Map<Long, Integer>              messageCounts = new ConcurrentHashMap<>();

    // ── Summary ───────────────────────────────────────────────────────────

    public void saveSummary(long userId, String summary) {
        summaries.put(userId, summary);
        log.debug("[ConversationStore] Summary saved for user {}", userId);
    }

    public Optional<String> getSummary(long userId) {
        return Optional.ofNullable(summaries.get(userId));
    }

    // ── Follow-ups ────────────────────────────────────────────────────────

    public void addFollowUp(long userId, FollowUpEntry entry) {
        followUps.computeIfAbsent(userId, k -> new ArrayList<>()).add(entry);
        log.debug("[ConversationStore] Follow-up added for user {}: '{}'", userId, entry.getTopic());
    }

    public List<FollowUpEntry> getFollowUps(long userId) {
        return followUps.getOrDefault(userId, Collections.emptyList());
    }

    public void resolveFollowUp(long userId, String topic) {
        List<FollowUpEntry> entries = followUps.get(userId);
        if (entries != null) entries.removeIf(e -> e.getTopic().equalsIgnoreCase(topic));
        log.debug("[ConversationStore] Follow-up resolved for user {}: '{}'", userId, topic);
    }

    // ── Relationship ──────────────────────────────────────────────────────

    public RelationshipProfile getOrCreateRelationship(long userId) {
        return relationships.computeIfAbsent(userId, k -> new RelationshipProfile());
    }

    public void saveRelationship(long userId, RelationshipProfile profile) {
        relationships.put(userId, profile);
    }

    // ── Style ─────────────────────────────────────────────────────────────

    public StyleProfile getOrCreateStyle(long userId) {
        return styles.computeIfAbsent(userId, k -> new StyleProfile());
    }

    public void saveStyle(long userId, StyleProfile profile) {
        styles.put(userId, profile);
    }

    // ── Message count ─────────────────────────────────────────────────────

    public int incrementAndGet(long userId) {
        return messageCounts.merge(userId, 1, Integer::sum);
    }

    public int getMessageCount(long userId) {
        return messageCounts.getOrDefault(userId, 0);
    }
}