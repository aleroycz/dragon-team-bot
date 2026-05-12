package com.dragon.utils.conversation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.*;

/**
 * Central service for all conversation intelligence features:
 *
 * <ol>
 *   <li>Conversation summarisation  — compresses history every N messages
 *   <li>Proactive follow-up         — tracks unresolved topics, raises them next session
 *   <li>Confidence scoring          — interprets logprobs to flag uncertain replies
 *   <li>Per-user relationship       — tracks sentiment trend, interaction count, topic frequency
 *   <li>Structured error analysis   — parses stack traces into structured fields
 *   <li>Response style adaptation   — learns verbosity, code vs explanation, formality
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationIntelligenceService {

    private static final int SUMMARY_EVERY_N_MESSAGES = 20;

    private final ConversationStore store;

    /**
     * Called after every exchange. When the message count hits the threshold,
     * returns a summary prompt fragment to compress into the next system prompt.
     * Otherwise returns empty.
     */
    public Optional<String> maybeSummarise(long userId, String userMessage, String assistantReply) {
        int count = store.incrementAndGet(userId);

        if (count % SUMMARY_EVERY_N_MESSAGES != 0) return Optional.empty();

        // Build a compressed narrative from the existing summary + new exchange
        String existing = store.getSummary(userId).orElse("");
        String newSummary = compressSummary(existing, userMessage, assistantReply);
        store.saveSummary(userId, newSummary);

        log.info("[ConvIntelligence] Summary updated for user {} at message {}", userId, count);
        return Optional.of(newSummary);
    }

    /**
     * Injects the rolling summary into the system prompt if one exists.
     */
    public String injectSummary(long userId, String basePrompt) {
        return store.getSummary(userId)
                .map(s -> "=== CONVERSATION HISTORY SUMMARY ===\n"
                        + s + "\n"
                        + "=====================================\n\n"
                        + basePrompt)
                .orElse(basePrompt);
    }

    /**
     * Compresses existing summary + latest exchange into a rolling narrative.
     * Keeps it under ~300 words by dropping the oldest detail when too long.
     */
    private String compressSummary(String existing, String userMessage, String assistantReply) {
        StringBuilder sb = new StringBuilder();
        if (!existing.isBlank()) {
            // Retain existing summary, trim if over limit
            String trimmed = existing.length() > 800
                    ? existing.substring(existing.length() - 800)
                    : existing;
            sb.append(trimmed).append("\n");
        }
        sb.append("- User asked about: ").append(truncate(userMessage, 120)).append("\n");
        sb.append("- Bot responded: ").append(truncate(assistantReply, 120)).append("\n");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Proactive follow-up
    // ─────────────────────────────────────────────────────────────────────────

    private static final String[] UNRESOLVED_SIGNALS = {
            "not working", "still broken", "haven't fixed", "will try", "will check",
            "let me know", "get back to", "figure out", "look into", "failing",
            "pending", "todo", "to do", "need to", "should probably"
    };

    private static final String[] RESOLVED_SIGNALS = {
            "fixed", "solved", "done", "working now", "resolved",
            "figured out", "got it working", "deployed", "merged", "closed"
    };

    /**
     * Scans the exchange for unresolved topics and stores them as follow-ups.
     * Also marks existing follow-ups as resolved if the user signals completion.
     */
    public void detectFollowUps(long userId, String userMessage, String assistantReply) {
        String combined = (userMessage + " " + assistantReply).toLowerCase();

        // Check if any existing follow-up was resolved
        if (anyMatch(combined, RESOLVED_SIGNALS)) {
            List<FollowUpEntry> existing = store.getFollowUps(userId);
            for (FollowUpEntry entry : existing) {
                if (combined.contains(entry.getTopic().toLowerCase())) {
                    store.resolveFollowUp(userId, entry.getTopic());
                    log.info("[ConvIntelligence] Follow-up resolved for user {}: '{}'",
                            userId, entry.getTopic());
                }
            }
        }

        // Detect new unresolved topics
        if (anyMatch(combined, UNRESOLVED_SIGNALS)) {
            String topic = extractTopic(userMessage);
            if (topic != null) {
                FollowUpEntry entry = new FollowUpEntry(topic, truncate(userMessage, 200),
                        Instant.now(), false);
                store.addFollowUp(userId, entry);
                log.info("[ConvIntelligence] Follow-up detected for user {}: '{}'", userId, topic);
            }
        }
    }

    /**
     * Returns a prompt fragment for any pending follow-ups, and marks them as raised.
     * Inject this into the system prompt at session start.
     */
    public String buildFollowUpFragment(long userId) {
        List<FollowUpEntry> pending = store.getFollowUps(userId).stream()
                .filter(e -> !e.isRaised())
                .toList();

        if (pending.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("=== UNRESOLVED TOPICS — MENTION NATURALLY ===\n");
        sb.append("The user had these unresolved things last time. ");
        sb.append("Bring them up naturally if relevant, don't force it:\n");
        for (FollowUpEntry e : pending) {
            sb.append("- ").append(e.getTopic())
                    .append(" (detected: ").append(e.getDetectedAt()).append(")\n");
            e.setRaised(true);
        }
        sb.append("==============================================\n\n");
        return sb.toString();
    }

    /**
     * Extracts a short topic label from the user message.
     * Tries to find the subject of the problem being described.
     */
    private String extractTopic(String message) {
        if (message == null || message.isBlank()) return null;
        // Try to pull noun phrases after known problem words
        Pattern p = Pattern.compile(
                "(?:my|the|our)\\s+([a-zA-Z0-9 _\\-]{3,40}?)\\s+" +
                        "(?:is|was|isn't|wasn't|not working|failing|broken|still)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(message);
        if (m.find()) return m.group(1).trim();

        // Fallback: first 6 words
        String[] words = message.trim().split("\\s+");
        return String.join(" ", Arrays.copyOfRange(words, 0, Math.min(6, words.length)));
    }

    /**
     * Analyses the average logprob of the response to determine model confidence.
     * Pass the {@code logprobs} array from the API response choices[0].logprobs.content
     *
     * @param logprobsContent  JsonArray of token logprob objects from the API
     * @return confidence score 0.0–1.0
     */
    public double scoreConfidence(com.google.gson.JsonArray logprobsContent) {
        if (logprobsContent == null || logprobsContent.isEmpty()) return 1.0;

        double sum   = 0;
        int    count = 0;

        for (int i = 0; i < logprobsContent.size(); i++) {
            com.google.gson.JsonObject token = logprobsContent.get(i).getAsJsonObject();
            if (token.has("logprob")) {
                sum += token.get("logprob").getAsDouble();
                count++;
            }
        }

        if (count == 0) return 1.0;

        double avgLogprob = sum / count;

        // logprob of 0 = certainty, -inf = impossible
        // Map [-5, 0] → [0, 1] linearly, clamp outside range
        double confidence = Math.max(0.0, Math.min(1.0, (avgLogprob + 5.0) / 5.0));

        log.debug("[ConvIntelligence] Confidence score: {} (avgLogprob={})",
                String.format("%.2f", confidence),
                String.format("%.3f", avgLogprob));

        return confidence;
    }

    /**
     * Appends a low-confidence disclaimer to a reply if the score is below threshold.
     */
    public String applyConfidenceDisclaimer(String reply, double confidence) {
        if (confidence >= 0.6) return reply;

        String disclaimer = confidence < 0.3
                ? "\n\n> ⚠️ I'm not very confident about this — please double-check before using it."
                : "\n\n> ℹ️ I'm somewhat uncertain here — worth verifying independently.";

        log.info("[ConvIntelligence] Low confidence ({}) — disclaimer appended", String.format("%.2f", confidence));
        return reply + disclaimer;
    }

    private static final String[] POSITIVE_SENTIMENT = {
            "thank", "great", "awesome", "love", "perfect", "helpful",
            "good job", "excellent", "appreciate", "nice", "brilliant"
    };
    private static final String[] NEGATIVE_SENTIMENT = {
            "wrong", "bad", "useless", "hate", "terrible", "awful",
            "not helpful", "incorrect", "shut up", "worst", "dumb"
    };

    /**
     * Records an interaction, updating sentiment trend and topic frequency.
     */
    public void recordRelationship(long userId, String userMessage, String topic) {
        RelationshipProfile profile = store.getOrCreateRelationship(userId);

        String lower = userMessage.toLowerCase();
        double sentiment = 0.5; // neutral baseline
        if (anyMatch(lower, POSITIVE_SENTIMENT)) sentiment = 0.85;
        if (anyMatch(lower, NEGATIVE_SENTIMENT)) sentiment = 0.15;

        profile.recordInteraction(sentiment, topic);
        store.saveRelationship(userId, profile);

        log.debug("[ConvIntelligence] Relationship updated — user={} tier='{}' sentiment={} topic='{}'",
                userId, profile.relationshipTier(),
                String.format("%.2f", profile.getSentimentTrend()), topic);
    }

    /**
     * Builds a relationship context fragment for the system prompt.
     */
    public String buildRelationshipFragment(long userId) {
        RelationshipProfile profile = store.getOrCreateRelationship(userId);
        if (profile.getTotalInteractions() == 0) return "";

        return "=== USER RELATIONSHIP ===\n"
                + "Tier            : " + profile.relationshipTier() + "\n"
                + "Interactions    : " + profile.getTotalInteractions() + "\n"
                + "Sentiment trend : " + String.format("%.2f", profile.getSentimentTrend())
                + " (0=negative, 1=positive)\n"
                + "Dominant topic  : " + profile.dominantTopic() + "\n"
                + "Adjust your warmth, familiarity, and trust level accordingly.\n"
                + "========================\n\n";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Structured error analysis
    // ─────────────────────────────────────────────────────────────────────────

    private static final Pattern EXCEPTION_PATTERN = Pattern.compile(
            "([a-zA-Z0-9_.]+Exception|[a-zA-Z0-9_.]+Error):\\s*(.+?)(?=\\n|$)");
    private static final Pattern AT_LINE_PATTERN   = Pattern.compile(
            "at\\s+([a-zA-Z0-9_.]+)\\(([^)]+)\\)");
    private static final Pattern CAUSED_BY_PATTERN = Pattern.compile(
            "Caused by:\\s*([a-zA-Z0-9_.]+):\\s*(.+?)(?=\\n|$)");

    /**
     * Returns true if the message looks like it contains a stack trace.
     */
    public boolean containsStackTrace(String message) {
        if (message == null) return false;
        return message.contains("at ") && message.contains(".java:")
                || message.contains("Exception:")
                || message.contains("Error:")
                || message.contains("Caused by:");
    }

    /**
     * Parses a stack trace into a structured summary fragment for the system prompt.
     * The model receives this as pre-processed context so it can focus on solving
     * rather than parsing.
     */
    public String analyseStackTrace(String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== STRUCTURED ERROR ANALYSIS ===\n");

        // Root exception
        Matcher exMatcher = EXCEPTION_PATTERN.matcher(message);
        if (exMatcher.find()) {
            sb.append("Exception : ").append(exMatcher.group(1)).append("\n");
            sb.append("Message   : ").append(truncate(exMatcher.group(2), 200)).append("\n");
        }

        // Caused by chain
        Matcher causedBy = CAUSED_BY_PATTERN.matcher(message);
        if (causedBy.find()) {
            sb.append("Caused by : ").append(causedBy.group(1)).append("\n");
            sb.append("Cause msg : ").append(truncate(causedBy.group(2), 200)).append("\n");
        }

        // First few relevant stack frames (skip java.*, sun.*, org.springframework.*)
        Matcher atMatcher = AT_LINE_PATTERN.matcher(message);
        List<String> userFrames = new ArrayList<>();
        while (atMatcher.find() && userFrames.size() < 5) {
            String frame = atMatcher.group(1);
            if (!frame.startsWith("java.")
                    && !frame.startsWith("sun.")
                    && !frame.startsWith("jdk.")
                    && !frame.startsWith("org.springframework.")
                    && !frame.startsWith("org.apache.")) {
                userFrames.add(frame + "(" + atMatcher.group(2) + ")");
            }
        }

        if (!userFrames.isEmpty()) {
            sb.append("Frames    :\n");
            userFrames.forEach(f -> sb.append("  → ").append(f).append("\n"));
        }

        sb.append("=================================\n\n");

        log.info("[ConvIntelligence] Stack trace parsed — {} user frames found", userFrames.size());
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Response style adaptation
    // ─────────────────────────────────────────────────────────────────────────

    private static final String[] WANTS_BRIEF   = {
            "briefly", "short", "quick", "tldr", "tl;dr", "in short",
            "just tell me", "one line", "summarise", "summary"
    };
    private static final String[] WANTS_DETAIL  = {
            "explain", "in detail", "step by step", "walk me through",
            "elaborate", "tell me more", "thoroughly", "comprehensive"
    };
    private static final String[] WANTS_CODE    = {
            "show me the code", "give me code", "code example", "snippet",
            "implementation", "how do i implement", "write a", "code for"
    };
    private static final String[] WANTS_EXPLAIN = {
            "why", "how does", "what is", "explain", "concept",
            "theory", "principle", "reason", "understand"
    };
    private static final String[] WANTS_FORMAL  = {
            "please", "could you", "would you", "kindly", "sir", "ma'am"
    };
    private static final String[] WANTS_CASUAL  = {
            "lol", "lmao", "haha", "ngl", "tbh", "idk", "wtf",
            "omg", "bruh", "dude", "bro", "sis"
    };

    /**
     * Nudges the style profile based on signals in the user message.
     */
    public void adaptStyle(long userId, String userMessage) {
        StyleProfile style = store.getOrCreateStyle(userId);
        String lower = userMessage.toLowerCase();

        if (anyMatch(lower, WANTS_BRIEF))   style.nudge("verbosity",      -0.1);
        if (anyMatch(lower, WANTS_DETAIL))  style.nudge("verbosity",      +0.1);
        if (anyMatch(lower, WANTS_CODE))    style.nudge("codePreference", +0.1);
        if (anyMatch(lower, WANTS_EXPLAIN)) style.nudge("codePreference", -0.1);
        if (anyMatch(lower, WANTS_FORMAL))  style.nudge("formality",      +0.1);
        if (anyMatch(lower, WANTS_CASUAL))  style.nudge("formality",      -0.1);

        store.saveStyle(userId, style);
        log.debug("[ConvIntelligence] Style adapted — user={} verbosity={} code={} formality={}",
                userId,
                style.describeVerbosity(),
                style.describeCode(),
                style.describeFormality());
    }

    /**
     * Builds a style directive fragment for the system prompt.
     */
    public String buildStyleFragment(long userId) {
        StyleProfile style = store.getOrCreateStyle(userId);
        if (style.getSignalCount() == 0) return "";

        return "=== RESPONSE STYLE ===\n"
                + "Verbosity   : " + style.describeVerbosity()      + "\n"
                + "Format      : " + style.describeCode()            + "\n"
                + "Tone        : " + style.describeFormality()       + "\n"
                + "Match this style in your reply.\n"
                + "======================\n\n";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────

    private static boolean anyMatch(String text, String... keywords) {
        for (String kw : keywords) if (text.contains(kw)) return true;
        return false;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}