package com.dragon.service.moderation;

import com.dragon.dto.interview.AgeVerificationResult;
import com.dragon.service.VultrInferenceClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
        return parseResult(sanitizeResponse(response));
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawVerification(
            boolean confident,
            boolean likelyAdult,
            String confidence,
            String reason,
            String followUpQuestion
    ) {}

    private AgeVerificationResult parseResult(String json) {
        try {
            RawVerification raw = objectMapper.readValue(json, RawVerification.class);
            AgeVerificationResult.ConfidenceLevel level = parseConfidence(raw.confidence());
            String followUp = raw.followUpQuestion() == null || raw.followUpQuestion().equals("null")
                    ? null : raw.followUpQuestion();
            return new AgeVerificationResult(raw.confident(), raw.likelyAdult(), level, raw.reason(), followUp);
        } catch (Exception e) {
            log.error("[AgeVerification] Failed to parse verification JSON: {}", json, e);
            return new AgeVerificationResult(false, false, AgeVerificationResult.ConfidenceLevel.LOW, "Parse error", null);
        }
    }

    private AgeVerificationResult.ConfidenceLevel parseConfidence(String raw) {
        if (raw == null) return AgeVerificationResult.ConfidenceLevel.LOW;
        try {
            return AgeVerificationResult.ConfidenceLevel.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[AgeVerification] Unknown confidence value '{}', defaulting to LOW", raw);
            return AgeVerificationResult.ConfidenceLevel.LOW;
        }
    }

    private String sanitizeResponse(String raw) {
        if (raw == null) return "{}";
        raw = raw.replaceAll("(?s)<think>.*?</think>", "").trim();
        raw = raw.replace("```json", "").replace("```", "");
        int start = raw.indexOf("{");
        int end = raw.lastIndexOf("}");
        if (start == -1 || end == -1) {
            log.warn("[AgeVerification] No JSON found in model response: {}", raw);
            return "{}";
        }
        return raw.substring(start, end + 1);
    }
}
