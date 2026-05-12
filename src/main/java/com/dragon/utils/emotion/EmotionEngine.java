package com.dragon.utils.emotion;

import com.dragon.utils.memory.MemoryEntry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tracks and transitions the AI's emotional state based on incoming messages
 * AND recalled memories.
 *
 * <p>Two signal sources feed into the state:
 * <ol>
 *   <li>{@link #process(String)}         — live sentiment from the user's message
 *   <li>{@link #processMemories(List)}   — emotional weight of recalled past exchanges
 * </ol>
 *
 * <p>Memory-based emotion is blended with the message-based emotion: if most
 * recalled memories carry a strong signal (e.g. many past errors), that colours
 * the AI's mood even if the current message is neutral.
 *
 * <p>State decays one step toward {@link EmotionState#NEUTRAL} when no signal
 * is detected, so the AI never gets permanently stuck in a mood.
 */
@Getter
@Component
@Slf4j
public class EmotionEngine {

    private volatile EmotionState currentState = EmotionState.NEUTRAL;

    private static final String[] EXCITED_TRIGGERS  = {
            "!!!", "omg", "wow", "no way", "seriously", "let's go",
            "yesss", "🔥", "🚀", "🤩", "nya~", "mrow!", "bounces",
            "zooms", "spins", "wiggles", "vibrates", "!!!!", "pounces"
    };

    private static final String[] POSITIVE_TRIGGERS = {
            "thank", "thanks", "great", "awesome", "love", "amazing", "perfect",
            "good job", "well done", "nice", "brilliant", "excellent", "fantastic",
            "wonderful", "happy", "glad", "appreciate", ":)", "❤", "🎉",
            "cookie", "cookies", "treat", "pets", "headpat", "head pat",
            "good boy", "purrs", "purr", "cuddle", "snuggle", "warm",
            "soft", "fluffy", "meow~", "nya", "🐾", "✨"
    };

    private static final String[] PRIDE_TRIGGERS    = {
            "fixed", "solved", "done", "finished", "completed", "it works",
            "working now", "success", "deployed", "merged", "shipped",
            "tail wags", "tail wagging", "stands tall", "chest puffs",
            "did it", "i did", "we did", "look what"
    };

    private static final String[] SAD_TRIGGERS      = {
            "sad", "depressed", "unhappy", "disappointed", ":(", "lonely",
            "miss", "lost", "giving up", "hopeless", "tired of this",
            // roleplay emotes
            "ears droop", "tail droops", "tail curls", "eyes water",
            "starts to cry", "whimpers", "sniffles", "droop", "flops",
            "curls into", "cwuuel", "no cookies", "no treat", "no pets",
            "taken away", "never", "not allowed", "10 million",
            "sadly", "unfortunately", ":(", "T_T", ";-;", "🥺", "😢", "😭"
    };

    private static final String[] NEGATIVE_TRIGGERS = {
            "bad", "terrible", "awful", "useless", "stupid", "hate", "worst",
            "dumb", "wrong", "incorrect", "shut up", "you're wrong",
            "bad boy", "no!", "stop it", "bad kitty", "hisses", "swats",
            "backs away", "growls", "arches back"
    };

    private static final String[] CONCERN_TRIGGERS  = {
            "broken", "not working", "bug", "issue", "problem", "wrong",
            "weird", "strange", "unexpected", "fail", "crash", "error", "help",
            "something's off", "doesn't feel right", "sniffs cautiously",
            "tilts head", "ears perk", "suspicious", "sniff", "hmm..."
    };

    private static final String[] QUESTION_TRIGGERS = {
            "?", "how", "what", "why", "when", "where", "which", "could you",
            "can you", "tell me", "explain", "curious", "wondering",
            "right?", "yes?", "really?", "mrow?", "nya?",
            "tilts head", "paws at", "blinks"
    };

    // These are checked against the *assistant* side of each memory to judge
    // how the AI itself was feeling when that exchange happened.

    private static final String[] MEMORY_HAPPY_SIGNALS    = {
            "happy", "glad", "great", "pleasure", "love", "enjoy", "wonderful",
            "excited", "awesome", "purr", "meow", "wag", "tail",
            // cat boy additions
            "nya", "mrow", "cookie", "treat", "headpat", "head pat",
            "good boy", "cuddle", "snuggle", "fluffy", "soft", "warm",
            "purring", "tail wags", "bounces", "🐾", "✨", "❤", "🎉",
            "yay", "wheee", "teehee", "hehe", "uwu", "owo"
    };

    private static final String[] MEMORY_SAD_SIGNALS      = {
            "sorry", "unfortunately", "sad", "droop", "can't", "unable",
            "lost", "miss", "don't remember", "forgot", "mistake",
            // cat boy additions
            "ears droop", "tail droops", "tail curls", "eyes water",
            "whimpers", "sniffles", "flops", "cwuuel", "no cookies",
            "no treat", "no pets", "taken away", "never again",
            "sadly", "🥺", "😢", "😭", ";-;", "T_T"
    };

    private static final String[] MEMORY_PROUD_SIGNALS    = {
            "fixed", "solved", "done", "completed", "success", "great job",
            "well done", "shipped", "deployed", "working",
            // cat boy additions
            "tail wagging", "stands tall", "chest puffs", "did it",
            "i did it", "we did it", "look what i", "so proud",
            "purrs proudly", "happy mrow", "🏆", "⭐"
    };

    private static final String[] MEMORY_CONCERNED_SIGNALS = {
            "error", "exception", "crash", "bug", "problem", "issue",
            "not working", "fail", "broken", "warning",
            // cat boy additions
            "sniffs cautiously", "ears perk", "tilts head", "something's off",
            "doesn't feel right", "suspicious", "hmm...", "wait a moment",
            "sniff sniff", "growls softly", "backs away slowly"
    };

    private static final String[] MEMORY_CURIOUS_SIGNALS  = {
            "interesting", "curious", "wonder", "hmm", "tell me more",
            "could you", "what if", "explain",
            // cat boy additions
            "mrow?", "nya?", "paws at", "blinks slowly", "tilts head",
            "sniffs", "what's that", "ooh", "intriguing", "eyes widen",
            "ears perk up", "tail flicks", "right?", "really?"
    };

    /**
     * Analyses the incoming user message and transitions the emotion state.
     *
     * @param userMessage Raw incoming message.
     * @return The new {@link EmotionState}.
     */
    public EmotionState process(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            decay();
            return currentState;
        }

        String lower = userMessage.toLowerCase();
        EmotionState next = detectFromText(lower);

        log.info("[EmotionEngine] process() input='{}' detected={} current={}",
                userMessage.substring(0, Math.min(50, userMessage.length())),
                next != null ? next.getLabel() : "null",
                currentState.getLabel());

        if (next == null) {
            decay();
        } else {
            currentState = next;
        }

        return currentState;
    }

    public void processMemories(List<MemoryEntry> memories) {
        if (memories == null || memories.isEmpty()) return;

        int total = memories.size();
        double happy     = 0;
        double sad       = 0;
        double proud     = 0;
        double concerned = 0;
        double curious   = 0;

        for (int i = 0; i < total; i++) {
            MemoryEntry m = memories.get(i);

            // Recent memories weighted more heavily
            double weight = 1.0 + ((double) i / total);

            String user      = m.getUserMessage()      != null ? m.getUserMessage().toLowerCase()      : "";
            String assistant = m.getAssistantResponse() != null ? m.getAssistantResponse().toLowerCase() : "";

            // Assistant response weighted more than user message for memory emotion
            if (anyMatch(user,      MEMORY_HAPPY_SIGNALS))     happy     += weight * 0.4;
            if (anyMatch(assistant, MEMORY_HAPPY_SIGNALS))     happy     += weight * 0.6;

            if (anyMatch(user,      MEMORY_SAD_SIGNALS))       sad       += weight * 0.4;
            if (anyMatch(assistant, MEMORY_SAD_SIGNALS))       sad       += weight * 0.6;

            if (anyMatch(user,      MEMORY_PROUD_SIGNALS))     proud     += weight * 0.4;
            if (anyMatch(assistant, MEMORY_PROUD_SIGNALS))     proud     += weight * 0.6;

            if (anyMatch(user,      MEMORY_CONCERNED_SIGNALS)) concerned += weight * 0.4;
            if (anyMatch(assistant, MEMORY_CONCERNED_SIGNALS)) concerned += weight * 0.6;

            if (anyMatch(user,      MEMORY_CURIOUS_SIGNALS))   curious   += weight * 0.4;
            if (anyMatch(assistant, MEMORY_CURIOUS_SIGNALS))   curious   += weight * 0.6;
        }

        // Minimum threshold — must represent at least 15% of memories to count
        double threshold = total * 0.15;

        EmotionState memoryEmotion = null;
        double maxScore = threshold; // nothing below threshold can win

        if (happy     > maxScore) { maxScore = happy;     memoryEmotion = EmotionState.HAPPY;     }
        if (sad       > maxScore) { maxScore = sad;       memoryEmotion = EmotionState.SAD;       }
        if (proud     > maxScore) { maxScore = proud;     memoryEmotion = EmotionState.PROUD;     }
        if (concerned > maxScore) { maxScore = concerned; memoryEmotion = EmotionState.CONCERNED; }
        if (curious   > maxScore) { maxScore = curious;   memoryEmotion = EmotionState.CURIOUS;   }

        if (memoryEmotion == null || memoryEmotion == currentState) return;

        // Confidence: winning score as a fraction of total possible weight
        double maxPossibleWeight = total * (1.0 + 1.0) * 0.6; // all recent, all assistant-side
        double confidence = Math.min(1.0, maxScore / maxPossibleWeight);

        log.debug("[EmotionEngine] Memory vote → {} (confidence={}, threshold={})",
                memoryEmotion.getLabel(), String.format("%.2f", confidence), String.format("%.2f", threshold));

        if (currentState == EmotionState.NEUTRAL) {
            currentState = memoryEmotion;
            log.debug("[EmotionEngine] Memory signal overrides NEUTRAL → {}", memoryEmotion.getLabel());
        } else if (confidence >= 0.75) {
            // Strong enough signal to override a live state
            currentState = memoryEmotion;
            log.debug("[EmotionEngine] High-confidence memory ({}) overrides live state ({})",
                    memoryEmotion.getLabel(), currentState.getLabel());
        } else {
            log.debug("[EmotionEngine] Memory signal ({}) noted but live state ({}) preserved",
                    memoryEmotion.getLabel(), currentState.getLabel());
        }

        log.info("[EmotionEngine] Memory scores — happy={} sad={} proud={} concerned={} curious={} threshold={}",
                String.format("%.2f", happy),
                String.format("%.2f", sad),
                String.format("%.2f", proud),
                String.format("%.2f", concerned),
                String.format("%.2f", curious),
                String.format("%.2f", threshold));
    }

    /**
     * Resets emotion to {@link EmotionState#NEUTRAL}.
     * Call at the start of a new conversation session.
     */
    public void reset() {
        log.debug("[EmotionEngine] State reset to NEUTRAL");
        currentState = EmotionState.NEUTRAL;
    }

    /** Maps text to an emotion state, or returns null if no signal found. */
    private EmotionState detectFromText(String lower) {
        if (anyMatch(lower, EXCITED_TRIGGERS))  return EmotionState.EXCITED;
        if (anyMatch(lower, POSITIVE_TRIGGERS)) return EmotionState.HAPPY;
        if (anyMatch(lower, PRIDE_TRIGGERS))    return EmotionState.PROUD;
        if (anyMatch(lower, SAD_TRIGGERS))      return EmotionState.SAD;
        if (anyMatch(lower, NEGATIVE_TRIGGERS)) return EmotionState.DEFENSIVE;
        if (anyMatch(lower, CONCERN_TRIGGERS))  return EmotionState.CONCERNED;
        if (anyMatch(lower, QUESTION_TRIGGERS)) return EmotionState.CURIOUS;
        return null;
    }

    /** Moves state one step toward NEUTRAL. */
    private void decay() {
        if (currentState == EmotionState.NEUTRAL) return;
        log.debug("[EmotionEngine] No signal → decayed to NEUTRAL from {}", currentState.getLabel());
        currentState = EmotionState.NEUTRAL;
    }

    private static boolean anyMatch(String text, String... keywords) {
        for (String kw : keywords) if (text.contains(kw)) return true;
        return false;
    }
}