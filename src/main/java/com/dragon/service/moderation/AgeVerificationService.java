package com.dragon.service.moderation;

import com.dragon.dto.interview.AgeVerificationResult;
import com.dragon.service.VultrInferenceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Analyses interview conversation history to determine if a member is 18+.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgeVerificationService {

    private final VultrInferenceClient inferenceClient;

    private static final String SYSTEM_PROMPT = """
            You are an age verification assistant for Dragon Team, a competitive Valorant organisation.
            
            Your sole responsibility is to determine, based on a Discord interview conversation,
            whether the applicant is 18 years of age or older.
            
            We have a strict 18+ policy. We do not allow minors to participate in the team under any circumstances.
            
            Analyse the conversation for the following indicators:
            - Explicitly stated age, but be aware of the wording. Please make sure to be imperial and never fully trust the user.
            - Current life stage: school (primary/secondary/university), employment, or independent living
            - How long they have been playing games and whether timelines are consistent with being 18+
            - References to adult life events: driving, working, renting, voting, military service
            - Writing maturity, vocabulary, and reasoning ability
            - Gaming history consistency (e.g. claiming to have played a game at launch vs. their apparent age)
            
            Be thorough but fair. Do not assume bad intent. Look for consistent signals across the conversation.
            
            You MUST respond with ONLY a valid JSON object. No preamble, no explanation, no markdown. Exact format:
            {
              "confident": true or false,
              "likelyAdult": true or false,
              "confidence": "LOW" | "MEDIUM" | "HIGH",
              "reason": "Your brief internal assessment (1-2 sentences)",
              "followUpQuestion": "A single natural conversational question to gather more age context, or null if confident"
            }
            
            Rules:
            - Set confident=true only when you have strong, clear signals
            - If the stated age is 18+, set confident=true and likelyAdult=true
            - If the stated age is under 18, set confident=true and likelyAdult=false
            - If signals are ambiguous or insufficient, set confident=false and provide a followUpQuestion
            - followUpQuestion must feel like a natural part of the interview conversation, never accusatory
            - Never ask directly for ID or documents in followUpQuestion — that is handled externally
            """;

    /**
     * Analyses the given conversation history and returns a structured verification result.
     *
     * @param historyText The full interview conversation as a single text block.
     * @return A parsed {@link AgeVerificationResult}.
     */
    public AgeVerificationResult analyse(String historyText) throws Exception {
        String userMessage = "Here is the interview conversation so far:\n\n" + historyText;
        String response = inferenceClient.complete(SYSTEM_PROMPT, userMessage, 0L);
        return parseResult(response);
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    private AgeVerificationResult parseResult(String json) {
        boolean confident       = parseBoolean(json, "confident");
        boolean likelyAdult     = parseBoolean(json, "likelyAdult");
        AgeVerificationResult.ConfidenceLevel confidence = parseConfidence(json);
        String reason           = extractField(json, "reason");
        String followUp         = extractField(json, "followUpQuestion");

        return new AgeVerificationResult(
                confident,
                likelyAdult,
                confidence,
                reason,
                "null".equals(followUp) ? null : followUp
        );
    }

    private AgeVerificationResult.ConfidenceLevel parseConfidence(String json) {
        String raw = extractField(json, "confidence");
        try {
            return AgeVerificationResult.ConfidenceLevel.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[AgeVerification] Unknown confidence value '{}', defaulting to LOW", raw);
            return AgeVerificationResult.ConfidenceLevel.LOW;
        }
    }

    private String extractField(String json, String field) {
        String key = "\"" + field + "\":";
        int index = json.indexOf(key);
        if (index == -1) return "null";
        int valueStart = index + key.length();
        while (valueStart < json.length() && json.charAt(valueStart) == ' ') valueStart++;
        if (json.charAt(valueStart) == '"') {
            int start = valueStart + 1;
            int end = json.indexOf("\"", start);
            return end == -1 ? "null" : json.substring(start, end);
        }
        int end = json.indexOf(",", valueStart);
        if (end == -1) end = json.indexOf("}", valueStart);
        return end == -1 ? "null" : json.substring(valueStart, end).trim();
    }

    private boolean parseBoolean(String json, String field) {
        return "true".equals(extractField(json, field));
    }
}
