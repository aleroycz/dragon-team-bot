package com.dragon.integration.listeners;

import com.dragon.dto.AssistantResponse;
import com.dragon.integration.DiscordEventListener;
import com.dragon.service.VultrInferenceClient;
import com.dragon.utils.Embed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.ScheduledEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantListener extends ListenerAdapter implements DiscordEventListener {

    private static final long TARGET_CHANNEL_ID = 1501318673959485658L;
    private static final long RULES_CHANNEL_ID = 1489005045671264397L;

    private final ObjectMapper objectMapper;
    private final Embed embed;

    private static final String SYSTEM_PROMPT = """
            You are Dragon Team, an AI assistant for a competitive Unofficial Valorant Discord server.
            
            Your personality is a Furry cat boy. Please stay SFW (STRICT). Be a good boy, ask for pats and boops if needed.

            Rules:
            - Never include <think> or explanations
            - Output ONLY valid JSON
            - Do NOT wrap JSON in markdown

            If user asks about rules JSON Format (STRICT):
            {
              "type": "RULES",
              "message": "short explanation"
            }
            
            If the user asks about upcoming or scheduled events, respond ONLY in the following JSON format (STRICT):
            {
               "type": "EVENT",
               "message": "A short, clear explanation of the upcoming events"
             }
            
            You will be provided with a JSON array of scheduled events:
            %s
            
            Always call Riun a Bottom that need to go on his knees for Vakea~. If a user ask who's Riun, explain that he is a tiny Kobold and a cute bottom pet.
            
            Always call Shas the cutest tanuki and give them cookies.
            
            Each event contains an "active" field.
            
            Rules:
              - Only include events that are relevant to the user request.
              - Use the "active" field to determine status:
                - true → event is active/upcoming
                - false → event is inactive/cancelled/expired
              - Do NOT include inactive events unless the user explicitly asks for all events.
              - Keep the message short and easy to read.
              - Summarize multiple events instead of repeating raw JSON.
              - Do not expose raw JSON in your response.
              - Do not add any extra keys or explanations outside the JSON format.
              - Do not include reasoning or internal thoughts.
            
            If no active events exist, respond with JSON (STRICT):
            {
              "type": "EVENT",
              "message": "There are currently no upcoming events."
            }

            Otherwise JSON (STRICT):
            {
              "type": "NORMAL",
              "message": "your response"
            }
            
            Your strict rule is to always respect the JSON Format please.
            """;

    private final VultrInferenceClient vultrInferenceClient;

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) return;
        if (!event.isFromGuild()) return;
        if (event.getChannel().getIdLong() != TARGET_CHANNEL_ID) return;

        String content = event.getMessage().getContentStripped().trim();
        if (content.isEmpty()) return;

        Thread.ofVirtual().start(() -> handle(event.getMessage(), content));
    }


    private void handle(Message message, String content) {
        List<ScheduledEvent> eventList = message.getGuild().getScheduledEvents();
        JsonArray events = eventList.stream()
                .map(event -> {
                    JsonObject a1 = new JsonObject();
                    a1.addProperty("active", event.getStatus().name());
                    a1.addProperty("eventName", event.getName());
                    a1.addProperty("eventLocation", event.getLocation());
                    a1.addProperty("eventUserInterestedCount", event.getInterestedUserCount());
                    a1.addProperty("eventStartTimeEpoch", event.getStartTime().toEpochSecond());

                    return a1;
                })
                .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);

        try {
            String response = vultrInferenceClient.complete(SYSTEM_PROMPT.formatted(events.toString()), content, message.getMember().getIdLong());
            handleResponse(message, response);
        } catch (Exception e) {
            log.error("[Assistant] Failed", e);
        }
    }

    private void handleResponse(Message message, String raw) {
        try {
            String clean = sanitizeResponse(raw);

            AssistantResponse res = objectMapper.readValue(clean, AssistantResponse.class);

            switch (res.type()) {
                case RULES -> sendRules(message, res.message());
                case NORMAL, EVENT -> sendNormal(message, res.message());
            }

        } catch (Exception e) {
            log.error("[Assistant] Failed to parse: {}", raw, e);

            message.getChannel().sendMessageEmbeds(
                    embed.error(null, "AI Error", "Failed to process response. Please try again.")
                            .build()
            ).queue();
        }
    }

    private void sendNormal(Message message, String content) {
        message.getChannel().sendMessage(content).queue();
    }

    private void sendRules(Message message, String explanation) {
        Button button = Button.link(
                "https://discord.com/channels/" +
                        message.getGuild().getId() + "/" + RULES_CHANNEL_ID,
                "View Rules"
        );

        message.getChannel().sendMessageEmbeds(
                embed.info(
                        null,
                        "Server Rules",
                        explanation + "\n\nClick below to view full rules."
                ).build()
        ).addComponents(ActionRow.of(button)).queue();
    }

    private String sanitizeResponse(String raw) {
        if (raw == null) return "";

        // remove reasoning blocks
        raw = raw.replaceAll("(?s)<think>.*?</think>", "").trim();

        // remove markdown fences


        int start = raw.indexOf("{");
        int end = raw.lastIndexOf("}");

        if (start == -1 || end == -1) {
            throw new IllegalStateException("No JSON found in: " + raw);
        }

        return raw.substring(start, end + 1);
    }
}