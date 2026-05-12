package com.dragon.integration.listeners;

import com.dragon.dto.moderation.ModerationLogEntry;
import com.dragon.dto.moderation.ModerationResult;
import com.dragon.dto.moderation.SanctionRequest;
import com.dragon.dto.moderation.SanctionType;
import com.dragon.integration.DiscordEventListener;
import com.dragon.service.VultrInferenceClient;
import com.dragon.service.moderation.SanctionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ModerationListener extends ListenerAdapter implements DiscordEventListener {
    private final java.util.List<Long> exclusionsList = java.util.List.of(
            1501318673959485658L,
            1500976642313687051L,
            1489006120432566485L,
            1489005140093436067L
    );

    private static final String RULES = """
            1. General Behavior: Treat all teammates, staff, and opponents with respect. No harassment, hate speech, discrimination, or toxicity of any kind. Constructive criticism is welcome, pure toxicity is not. Casual banter is fine but keep it out of in-game chat. Represent the team professionally at all times.
            2. Discord Rules: Follow Discord Terms of Service. No spam or NSFW outside designated channels. No illegal content, IRL material, or malicious links. Use channels for their intended purpose.
            3. Competitive Integrity: Follow Riot Games ToS. No cheating, exploiting, or unfair third-party tools. No smurfing or account sharing. Always play to win unless mutually agreed otherwise.
            4. Communication: All voice and in-game chat must remain in English. No obscene language. No belittling or disrespecting teammates.
            5. Zero Tolerance: Cheating, harassment, threats, hate speech, leaking team information, and repeated toxic behaviour result in immediate permanent action.
            """;

    private static final String SYSTEM_PROMPT = """
            You are a moderation assistant for a competitive Valorant Discord server called Dragon Team.
            Your job is to analyse messages and determine if they violate the server rules.
            
            The rules are as follows:
            """ + RULES + """
            
            You must respond ONLY with a valid JSON object in this exact format, no other text:
            {
              "violates": true or false,
              "severity": "NONE" | "LOW" | "MEDIUM" | "HIGH" | "CRITICAL",
              "rule": "The specific rule that was violated, or null if none",
              "reason": "A brief explanation of why this violates the rules, or null if none",
              "action": "WARN" | "MUTE" | "BAN" | "NONE"
            }
            
            Severity guide:
            - none: No violation
            - low: Mild toxicity, minor rule breach (action: warn)
            - medium: Clear rule violation, disrespectful behaviour (action: warn or mute)
            - high: Serious violation, harassment, hate speech (action: mute or warn)
            - critical: Cheating, threats, zero tolerance violations (action: warn)
            
            Be fair and context-aware. Casual banter between members is acceptable.
            Only flag genuine violations. Do not over-moderate. Do not micro-moderate. If you are unsure please use the severity NONE.
            """;

    private final VultrInferenceClient vultrInferenceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SanctionService sanctionService;

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) return;

        if (exclusionsList.contains(event.getChannel().getIdLong())) return; // Filters out the channel without ai-assisted moderation.

        if (!event.isFromGuild()) return;

        String content = event.getMessage().getContentStripped().trim();
        if (content.isEmpty()) return;

        objectMapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false
        );

        Thread.ofVirtual().start(() -> moderate(event.getMessage(), content));
    }

    private void moderate(Message message, String content) {
        try {
            String response = vultrInferenceClient.complete(SYSTEM_PROMPT, content, message.getMember().getIdLong());
            handleModerationResult(message, response);
        } catch (Exception e) {
            log.error("[Moderation] Failed to moderate message", e);
        }
    }

    private void handleModerationResult(Message message, String json) {
        try {
            ModerationResult result = objectMapper.readValue(sanitizeResponse(json), ModerationResult.class);

            saveModerationResult(message, message.getContentStripped().trim(), result);

            if (!result.violates()) return;

            log.warn("[Moderation] Violation detected — severity: {}, action: {}, rule: {}, reason: {}",
                    result.severity(), result.action(), result.rule(), result.reason());

            switch (result.severity()) {
                case MEDIUM, HIGH, CRITICAL -> notifyStaff(message, result.severity().name(), result.rule(), result.reason());
                default -> {}
            }

            switch (result.action()) {
                case WARN -> sanctionService.warn(new SanctionRequest(
                        message.getAuthor().getId(),
                        message.getAuthor().getAsTag(),
                        message.getJDA().getSelfUser().getId(),
                        message.getJDA().getSelfUser().getAsTag(),
                        message.getGuildId(),
                        SanctionType.WARN,
                        result.reason(),
                        24000L
                ));
                case MUTE -> sanctionService.mute(new SanctionRequest(
                        message.getAuthor().getId(),
                        message.getAuthor().getAsTag(),
                        message.getJDA().getSelfUser().getId(),
                        message.getJDA().getSelfUser().getAsTag(),
                        message.getGuildId(),
                        SanctionType.MUTE,
                        result.reason(),
                        24000L
                ));
                case BAN -> {
                    //TODO: Nothing for now as we do not trust the AI fully yet. As we should :)
                }
            }
        } catch (Exception e) {
            log.error("[Moderation] Failed to parse moderation JSON: {}", json, e);
        }
    }

    private void notifyStaff(Message message, String severity, String rule, String reason) {
        log.warn("[Moderation] Staff notification — User: {}, Severity: {}, Rule: {}, Reason: {}",
                message.getAuthor().getAsTag(), severity, rule, reason);

        EmbedBuilder moderationMessage = new EmbedBuilder()
                .setTitle("Rules violation detected.")
                .setColor(getSeverityColor(severity))
                .addField("User",
                        message.getAuthor().getAsTag() + " (`" + message.getAuthor().getId() + "`)",
                        false)
                .addField("Message",
                        truncate(message.getContentDisplay()),
                        false)
                .addField("Severity",
                        severity.toUpperCase(),
                        true)
                .addField("Rule Violated",
                        rule != null ? rule : "Unknown",
                        true)
                .addField("Reason",
                        reason != null ? reason : "No reason provided",
                        false)
                .addField("Channel",
                        message.getChannel().getName() + " (`" + message.getChannel().getId() + "`)",
                        true)
                .addField("Timestamp",
                        java.time.Instant.now().toString(),
                        true)
                .setFooter("Dragon Team Moderation System : This using AI. Please take the information with caution. No automatic action has been issued.")
                .setAuthor(
                        message.getAuthor().getEffectiveName(),
                        null,
                        message.getAuthor().getEffectiveAvatarUrl()
                );

        message.getGuild().getTextChannelById(1489009000379977769L).sendMessageEmbeds(moderationMessage.build())
                .queue();

    }

    private void saveModerationResult(Message message, String content, ModerationResult result) {
        try {
            String userId = message.getAuthor().getId();

            Path userDir = Path.of("data", "moderation", userId);
            Files.createDirectories(userDir);

            String fileName = UUID.randomUUID() + ".json";
            Path filePath = userDir.resolve(fileName);

            ModerationLogEntry entry = new ModerationLogEntry(
                    message.getId(),
                    userId,
                    message.getAuthor().getAsTag(),
                    message.getChannel().getId(),
                    message.getChannel().getName(),
                    (result.violates() ? content : "Redacted due to privacy reasons."), // Redact messages except if a potential violation is found.
                    result
            );

            String json = objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(entry);

            Files.writeString(filePath, json);

        } catch (IOException e) {
            log.error("[Moderation] Failed to save moderation result", e);
        }
    }

    private String sanitizeResponse(String raw) {
        if (raw == null) return "";

        // remove reasoning blocks
        raw = raw.replaceAll("(?s)<think>.*?</think>", "").trim();

        // remove markdown fences
        raw = raw.replace("```json", "");
        raw = raw.replace("```", "");

        int start = raw.indexOf("{");
        int end = raw.lastIndexOf("}");

        if (start == -1 || end == -1) {
            throw new IllegalStateException("No JSON found in: " + raw);
        }

        return raw.substring(start, end + 1);
    }

    private Color getSeverityColor(String severity) {
        return switch (severity.toUpperCase()) {
            case "LOW" -> Color.YELLOW;
            case "MEDIUM" -> new Color(255, 165, 0); // orange
            case "HIGH" -> Color.RED;
            case "CRITICAL" -> new Color(139, 0, 0); // dark red
            default -> Color.GRAY;
        };
    }

    private String truncate(String text) {
        if (text == null) return "null";
        return text.length() > 800 ? text.substring(0, 800) + "..." : text;
    }
}
