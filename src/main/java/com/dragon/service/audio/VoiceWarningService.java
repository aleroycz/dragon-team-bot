// VoiceWarningService.java
package com.dragon.service.audio;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceWarningService {

    private static final String BASE_URL       = "https://api.vultrinference.com/v1";
    private static final String MODELS_ENDPOINT = BASE_URL + "/audio/models";
    private static final String VOICES_ENDPOINT = BASE_URL + "/audio/voices";
    private static final String TTS_ENDPOINT    = BASE_URL + "/audio/speech";

    private static final String FALLBACK_MODEL = "tts-1";
    private static final String FALLBACK_VOICE = "onyx";

    @Value("${vultr.inference.api.key}")
    private String vultrApiKey;

    private final WebClient webClient = WebClient.builder().build();
    private final Gson gson = new Gson();

    private String resolvedModel;
    private String resolvedVoice;

    /**
     * Fetches the first available TTS model and voice from Vultr at startup.
     * Falls back to hardcoded defaults if either call fails so the app
     * still starts cleanly in degraded environments.
     */
    @PostConstruct
    void resolveModelAndVoice() {
        resolvedModel = fetchFirstModel();
        resolvedVoice = fetchFirstVoice();
        log.info("[TTS] Resolved model='{}' voice='{}'", resolvedModel, resolvedVoice);
    }

    private String fetchFirstModel() {
        try {
            String body = webClient.get()
                    .uri(MODELS_ENDPOINT)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + vultrApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonObject root = gson.fromJson(body, JsonObject.class);
            JsonArray speech = root.getAsJsonArray("speech");

            if (speech != null && !speech.isEmpty()) {
                String model = speech.get(0).getAsJsonObject().get("id").getAsString();
                log.debug("[TTS] Auto-selected model: {}", model);
                return model;
            }
        } catch (Exception ex) {
            log.warn("[TTS] Failed to fetch audio models, falling back to '{}': {}", FALLBACK_MODEL, ex.getMessage());
        }
        return FALLBACK_MODEL;
    }

    private String fetchFirstVoice() {
        try {
            String body = webClient.get()
                    .uri(VOICES_ENDPOINT)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + vultrApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonObject root = gson.fromJson(body, JsonObject.class);

            for (String provider : new String[]{"xtts", "bark"}) {
                JsonArray voices = root.getAsJsonArray(provider);
                if (voices != null && !voices.isEmpty()) {
                    String voice = voices.get(0).getAsString();
                    log.debug("[TTS] Auto-selected voice: {} (provider: {})", voice, provider);
                    return voice;
                }
            }
        } catch (Exception ex) {
            log.warn("[TTS] Failed to fetch audio voices, falling back to '{}': {}", FALLBACK_VOICE, ex.getMessage());
        }
        return FALLBACK_VOICE;
    }

    /**
     * Generates a calm TTS warning for the given player and returns raw audio bytes.
     * The caller is responsible for routing these into the voice channel.
     */
    public byte[] generateCalmWarning(String username) {
        String warningText = String.format(
                "Hey %s, please keep your voice calm. Your teammates need to stay focused. "
                        + "Clear and concise callouts will help the team far more than loud communication.",
                username
        );

        TtsRequest requestBody = new TtsRequest(resolvedModel, warningText, resolvedVoice);

        try {
            byte[] audioBytes = webClient.post()
                    .uri(TTS_ENDPOINT)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + vultrApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (audioBytes == null || audioBytes.length == 0) {
                log.warn("[TTS] Empty audio response for warning to {}", username);
                return new byte[0];
            }

            log.info("[TTS] Warning audio generated for {} ({} bytes)", username, audioBytes.length);
            return audioBytes;

        } catch (Exception ex) {
            log.error("[TTS] Failed to generate warning for {}: {}", username, ex.getMessage(), ex);
            return new byte[0];
        }
    }

    private record TtsRequest(String model, String input, String voice) {}
}