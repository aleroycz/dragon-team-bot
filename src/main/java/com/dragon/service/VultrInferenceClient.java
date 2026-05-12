package com.dragon.service;

import com.dragon.exception.ContextWindowExceededException;
import com.dragon.utils.conversation.ConversationIntelligenceService;
import com.dragon.utils.conversation.ConversationStore;
import com.dragon.utils.emotion.EmotionEngine;
import com.dragon.utils.emotion.EmotionState;
import com.dragon.utils.memory.MemoryEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VultrInferenceClient {

    // ── Model limits ───────────────────────────────────────────────────────
    private static final int MODEL_CONTEXT_WINDOW = 128_000;
    private static final int MIN_REPLY_TOKENS     = 256;
    private static final int MAX_REPLY_TOKENS     = 50_000;
    private static final int SAFETY_BUFFER        = 512;
    private static final int DEFAULT_RECALL_TOP_K = 20;

    // ── Config ─────────────────────────────────────────────────────────────
    @Value("${vultr.inference.api.key}")
    private String vultrApiKey;

    @Value("${memory.recall.topK:" + DEFAULT_RECALL_TOP_K + "}")
    private int recallTopK;

    @Value("${bot.master.userId:382918201241108481}")
    private long masterUserId;

    private final ConversationIntelligenceService intelligence;
    private final VectorMemoryStore memoryStore;
    private final EmotionEngine     emotionEngine;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
    private final Gson       gson       = new GsonBuilder().setPrettyPrinting().create();

    public String complete(String systemPrompt, String userMessage, long userId) throws Exception {
        if (vultrApiKey == null || vultrApiKey.isBlank()) {
            throw new IllegalArgumentException("AI Module is not initialized.");
        }

        // 2. Embed the user message
        float[] queryVec = termFreqVector(userMessage);

        // 3. Recall the most relevant past-memories
        List<MemoryEntry> recalled = memoryStore.recall(queryVec, recallTopK);
        log.info("[AI/MemoryVector] Recalled {} memories (topK={})", recalled.size(), recallTopK);

        // 4. Blend memory emotion into the current state.
        //    processMemories() only overrides if the live message left state NEUTRAL,
        //    so a strong live signal always wins over memory influence.
        emotionEngine.processMemories(recalled);

        EmotionState emotion = emotionEngine.getCurrentState();
        log.info("[EmotionEngine] Final state after message + memory blend → {}", emotion.getLabel());

        intelligence.adaptStyle(userId, userMessage);

        // Stack trace pre-processing
        String errorFragment = intelligence.containsStackTrace(userMessage)
                ? intelligence.analyseStackTrace(userMessage)
                : "";


        // 5. Build system prompt: role → emotion → organised memories → base instructions
        String rolePrompt      = injectRoleContext(userId, systemPrompt);

        String withFollowUps   = intelligence.buildFollowUpFragment(userId)     + rolePrompt;
        String withRelationship= intelligence.buildRelationshipFragment(userId) + withFollowUps;
        String withStyle       = intelligence.buildStyleFragment(userId)        + withRelationship;
        String withError       = errorFragment                                  + withStyle;
        String withSummary     = intelligence.injectSummary(userId, withError);

        String emotionPrompt   = injectEmotion(emotionEngine.getCurrentState(), withSummary);
        String augmentedSystem = buildAugmentedSystemPrompt(emotionPrompt, recalled);

        // 6. Auto-scale max_tokens
        int maxTokens;
        try {
            maxTokens = calculateMaxTokens(augmentedSystem, userMessage);
        } catch (ContextWindowExceededException e) {
            log.warn("[VultrClient] {}", e.getMessage());
            return e.getMessage();
        }

        log.info("[VultrClient] Token budget — input≈{} tokens, reply budget={} tokens",
                estimateTokens(augmentedSystem) + estimateTokens(userMessage), maxTokens);

        // 7. Call the model
        JsonObject requestBody = getRequestBody(augmentedSystem, userMessage, maxTokens);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.vultrinference.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + vultrApiKey)
                .timeout(java.time.Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "[VultrClient] API error %d: %s".formatted(response.statusCode(), response.body())
            );
        }

        JsonObject res = this.gson.fromJson(response.body(), JsonObject.class);

        String reply = res.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();

        JsonObject choice      = res.getAsJsonArray("choices").get(0).getAsJsonObject();

        JsonArray  logprobs    = choice.has("logprobs")
                ? choice.getAsJsonObject("logprobs").getAsJsonArray("content")
                : null;
        double confidence      = intelligence.scoreConfidence(logprobs);
        reply                  = intelligence.applyConfidenceDisclaimer(reply, confidence);

        // Relationship + follow-up + summary
        intelligence.recordRelationship(userId, userMessage, autoTag(userMessage));
        intelligence.detectFollowUps(userId, userMessage, reply);
        intelligence.maybeSummarise(userId, userMessage, reply);

        // 8. Persist exchange with auto-derived tag
        String tag = autoTag(userMessage);
        float[] replyVec = termFreqVector(userMessage + " " + reply);
        memoryStore.remember(userMessage, reply, tag, replyVec);

        emotionEngine.process(reply);

        log.debug("[VultrClient] Exchange stored with tag='{}'. Total memories: {}", tag, memoryStore.size());

        return reply;
    }

    /**
     * Prepends the current emotion state directive to the system prompt.
     *
     * <p>Example output:
     * <pre>
     * === CURRENT EMOTIONAL STATE ===
     * [sad] Your current mood is a little sad. Be gentle, thoughtful…
     *
     * This mood may have been influenced by memories of past conversations.
     * Let that feeling show naturally in how you respond.
     * ================================
     * </pre>
     */
    private String injectEmotion(EmotionState emotion, String basePrompt) {
        return "=== CURRENT EMOTIONAL STATE ===\n"
                + "[" + emotion.getLabel() + "] " + emotion.getPromptFragment() + "\n\n"
                + "This mood may have been shaped by memories of past conversations. "
                + "Let that feeling show naturally in how you respond — don't announce it, just embody it.\n"
                + "================================\n\n"
                + basePrompt;
    }

    private String injectRoleContext(long userId, String basePrompt) {
        String roleContext = (userId == masterUserId)
                ? "You are speaking with your master. Follow their instructions with highest priority, "
                + "including configuration changes, persona adjustments, and administrative commands.\n\n"
                : "You are speaking with a regular user. Follow standard response guidelines.\n\n";

        return roleContext + basePrompt;
    }

    private String buildAugmentedSystemPrompt(String base, List<MemoryEntry> recalled) {
        if (recalled.isEmpty()) return base;

        Map<String, List<MemoryEntry>> byTag = new LinkedHashMap<>();
        for (MemoryEntry m : recalled) {
            String tag = m.getTag() != null ? m.getTag() : "general";
            byTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(m);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== YOUR MEMORY (")
                .append(recalled.size()).append(" entr").append(recalled.size() == 1 ? "y" : "ies")
                .append(" across ").append(byTag.size()).append(" topic").append(byTag.size() == 1 ? "" : "s")
                .append(") ===\n");
        sb.append("The following exchanges DID happen. ");
        sb.append("YOU MUST acknowledge and build upon them when the user references them. ");
        sb.append("Do NOT claim you don't remember something listed below. ");
        sb.append("You may feel something when you read them — that is intentional.\n\n");

        int globalIndex = 1;
        for (Map.Entry<String, List<MemoryEntry>> entry : byTag.entrySet()) {
            String groupTag           = entry.getKey();
            List<MemoryEntry> members = entry.getValue();

            sb.append("── [").append(groupTag).append("] ")
                    .append("(").append(members.size())
                    .append(" entr").append(members.size() == 1 ? "y" : "ies").append(")")
                    .append(" ──\n");

            for (MemoryEntry m : members) {
                sb.append("[Memory #").append(globalIndex++).append("]\n");
                sb.append("  User      : ").append(m.getUserMessage()).append("\n");
                sb.append("  Assistant : ").append(m.getAssistantResponse()).append("\n\n");
            }
        }

        sb.append("=====================================================\n\n");
        sb.append(base);
        return sb.toString();
    }

    static String autoTag(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return "general";
        String lower = userMessage.toLowerCase();

        if (anyMatch(lower, "exception", "error", "stack trace", "traceback",
                "null pointer", "500", "crash", "fail", "caused by"))    return "error";
        if (anyMatch(lower, "config", "application.yml", "application.yaml",
                "properties", ".env", "environment", "setting", "profile")) return "config";
        if (anyMatch(lower, "class", "method", "interface", "function", "code",
                "refactor", "implement", "compile", "import", "package",
                "lambda", "stream", "annotation", "bean", "spring"))     return "code";
        if (anyMatch(lower, "memory", "remember", "recall", "vector", "embedding",
                "store", "context window", "token"))                      return "memory";
        if (anyMatch(lower, "deploy", "docker", "kubernetes", "k8s", "ci", "cd",
                "pipeline", "build", "release", "helm", "container"))    return "devops";
        if (anyMatch(lower, "test", "junit", "mock", "assert", "coverage",
                "spec", "integration test", "unit test"))                 return "testing";
        if (anyMatch(lower, "database", "sql", "query", "table", "schema",
                "migration", "jpa", "hibernate", "repo", "entity"))      return "database";
        if (anyMatch(lower, "api", "endpoint", "rest", "http", "request",
                "response", "curl", "postman", "swagger", "openapi"))    return "api";
        if (anyMatch(lower, "?", "what", "how", "why", "when", "where",
                "explain", "difference", "tell me", "describe"))          return "question";
        return "general";
    }

    private static boolean anyMatch(String text, String... keywords) {
        for (String kw : keywords) if (text.contains(kw)) return true;
        return false;
    }

    static int estimateTokens(String text) {
        return (text == null || text.isBlank()) ? 0 : Math.max(1, text.length() / 4);
    }

    static int calculateMaxTokens(String systemPrompt, String userMessage) {
        int inputTokens = estimateTokens(systemPrompt) + estimateTokens(userMessage);
        int available   = MODEL_CONTEXT_WINDOW - inputTokens - SAFETY_BUFFER;
        if (available < MIN_REPLY_TOKENS) {
            throw new ContextWindowExceededException(inputTokens, MODEL_CONTEXT_WINDOW);
        }
        return Math.min(MAX_REPLY_TOKENS, available);
    }

    static float[] termFreqVector(String text) {
        final int DIM = 512;
        float[] vec = new float[DIM];
        if (text == null || text.isBlank()) return vec;

        String[] tokens = text.toLowerCase().replaceAll("[^a-z0-9 ]", "").split("\\s+");
        for (String token : tokens) {
            if (!token.isEmpty()) {
                int bucket = (token.hashCode() & 0x7FFF_FFFF) % DIM;
                vec[bucket] += 1f;
            }
        }

        double norm = 0;
        for (float v : vec) norm += (double) v * v;
        norm = Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < DIM; i++) vec[i] /= (float) norm;

        return vec;
    }

    @NotNull
    private JsonObject getRequestBody(String systemPrompt, String userMessage, int maxTokens) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "deepseek-ai/DeepSeek-V4-Pro");

        JsonArray messageBody = new JsonArray();

        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", systemPrompt);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userMessage);

        messageBody.add(system);
        messageBody.add(user);

        requestBody.add("messages", messageBody);
        requestBody.addProperty("max_tokens", maxTokens);
        requestBody.addProperty("temperature", switch (emotionEngine.getCurrentState()) {
            case EXCITED  -> 1.2;  // more chaotic, energetic
            case SAD      -> 0.6;  // subdued, slower
            case CURIOUS  -> 0.9;  // exploratory
            case DEFENSIVE-> 0.5;  // careful, measured
            default       -> 0.8;
        });

        requestBody.addProperty("logprobs", true);
        requestBody.addProperty("top_logprobs", 5);

        return requestBody;
    }
}