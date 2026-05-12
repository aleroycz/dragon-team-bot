package com.dragon.service.audio;

import com.dragon.service.UserConsentService;
import com.dragon.service.VultrInferenceClient;
import com.dragon.config.BehaviorTracker;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.UserSnowflake;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceAnalysisService {
    private final UserConsentService userConsentService;
    private final VoiceScoreService voiceScoreService;
    private final VultrInferenceClient vultrInferenceClient;
    private final BehaviorTracker behaviorTracker;
    private final VoiceWarningService voiceWarningService;

    private static final Pattern JSON_FENCE_PATTERN =
            Pattern.compile("```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.DOTALL);

    public void analyzeAsync(String userId, String username, String transcript, AudioCallback cb) {

       if (this.userConsentService.hasConsent(UserSnowflake.fromId(userId))) {
           CompletableFuture.runAsync(() -> {
               try {
                   AIAnalysisResponse analysis = analyzeWithClaude(username, transcript);
                   if (analysis == null) return;

                   log.info("[Analysis] {}: score={} friendly={} feedback={}",
                           username, analysis.score(), analysis.isFriendly(), analysis.feedback());

                   if (!analysis.isFriendly()) {
                       voiceScoreService.save(userId, username, transcript, analysis.score(), analysis.feedback());
                       handleUnfriendlyBehavior(userId, username, analysis, cb);
                   }

               } catch (Exception ex) {
                   log.error("Failed to analyze transcript for {}: {}", username, ex.getMessage(), ex);
                   cb.onError(userId, transcript, ex.getMessage());
               }
           });
       } else {
            cb.onError(userId, "No transcript", "The user did not gave consent for transcription.");
       }
    }

    private void handleUnfriendlyBehavior(String userId, String username, AIAnalysisResponse analysis, AudioCallback cb) {
        if (analysis.score() > 4) return;

        boolean thresholdBreached = behaviorTracker.recordAndCheck(userId);
        if (!thresholdBreached) {
            log.debug("[Behavior] {}: poor call recorded, threshold not yet reached", username);
            return;
        }

        log.warn("[Behavior] {}: warning threshold reached — issuing TTS warning", username);
        behaviorTracker.reset(userId);

        byte[] audioWarning = voiceWarningService.generateCalmWarning(username);
        if (audioWarning.length > 0) {
            deliverAudioWarning(userId, username, audioWarning, cb);
        }
    }

    /**
     * TODO: Route the audio bytes into the user's voice channel.
     * Wire in your JDA AudioSendHandler, LavaPlayer, or WebSocket audio frame sender here.
     */
    private void deliverAudioWarning(String userId, String username, byte[] audioBytes, AudioCallback cb) {
        log.info("[TTS] Delivering {}-byte warning to {}'s channel (userId={})",
                audioBytes.length, username, userId);
        cb.onProceedCalm(userId, audioBytes);
    }

    private AIAnalysisResponse analyzeWithClaude(String username, String transcript) throws Exception {
        String systemPrompt = """
                You are a communication quality analyst for competitive Valorant Premier matches.
                Your job is to evaluate in-game voice callouts for tactical usefulness and team communication quality.

                ## Scoring Guide (score: 0–10)

                | Range | Meaning                                                                    |
                |-------|----------------------------------------------------------------------------|
                | 9–10  | Precise callout with location, count, ability state — instantly actionable |
                | 7–8   | Clear and timely, minor info missing (e.g. no ability state)               |
                | 5–6   | Understandable but vague or slightly late                                  |
                | 3–4   | Confusing, misleading, or critically delayed                               |
                | 1–2   | Incoherent, toxic, or completely irrelevant noise                          |
                | 0     | No usable content whatsoever                                               |

                ## What counts as a GOOD callout
                - Named map positions: "Two on B site, one posted elbow"
                - Ability tracking: "Sova drone coming mid", "Smokes on CT"
                - Numeric enemy counts with location: "Three rotating A short"
                - Health/economy reads: "They're eco — full rush likely"

                ## What counts as FRIENDLY / acceptable communication
                - Encouragement, banter, or morale talk: "nice shot", "gg", "let's go"
                - Strategic discussion during buy phase or post-round
                - Asking for information ("anyone see mid?")
                - Acknowledgements ("got it", "rotating")

                ## What counts as UNFRIENDLY / poor communication
                - Toxic remarks, blame, or insults directed at teammates
                - Screaming, panic calls with no actionable info
                - Repeated irrelevant noise during active rounds
                - Deliberately misleading calls
                - The leader Vakea is allowed to blame, to give unreconstructed calls if needed.

                ## Output Rules (STRICT)
                - Respond ONLY with a single raw JSON object — no markdown, no explanation, no extra keys
                - `isFriendly` must be `true` for all neutral/friendly comms regardless of call quality
                - `score` is 0 if isFriendly is true (score is irrelevant for friendly comms)
                - `feedback` must be 1–2 sentences, specific to this player's transcript

                {
                  "isFriendly": true | false,
                  "score": 0–10,
                  "feedback": "Specific feedback here."
                }
                """;

        String userPrompt = """
                Analyse this in-game voice transcript and return only the JSON response.

                Player: %s
                Transcript: "%s"
                """.formatted(username, transcript);

        String raw = vultrInferenceClient.complete(systemPrompt, userPrompt, 0L);
        return parseResponse(raw);
    }

    private AIAnalysisResponse parseResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            log.warn("[Analysis] Empty response from model");
            return null;
        }

        String json = raw.trim();
        Matcher fenceMatcher = JSON_FENCE_PATTERN.matcher(json);
        if (fenceMatcher.find()) {
            json = fenceMatcher.group(1);
        }

        try {
            JsonObject result = new Gson().fromJson(json, JsonObject.class);

            if (!result.has("isFriendly") || !result.has("score") || !result.has("feedback")) {
                log.warn("[Analysis] Model response missing required fields: {}", json);
                return null;
            }

            boolean isFriendly = result.get("isFriendly").getAsBoolean();
            int score = Math.max(0, Math.min(10, result.get("score").getAsInt()));
            String feedback = result.get("feedback").getAsString();

            return new AIAnalysisResponse(isFriendly, score, feedback);

        } catch (JsonSyntaxException | IllegalStateException ex) {
            log.warn("[Analysis] Failed to parse model response as JSON. Raw: {}", raw);
            return null;
        }
    }

    public record AIAnalysisResponse(
            boolean isFriendly,
            int score,
            String feedback
    ) {}
}
