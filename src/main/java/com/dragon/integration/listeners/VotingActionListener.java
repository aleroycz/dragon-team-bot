package com.dragon.integration.listeners;

import com.dragon.integration.DiscordEventListener;
import com.dragon.service.VotingSessionService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class VotingActionListener extends ListenerAdapter implements DiscordEventListener {
    private final Map<String, Set<Long>> pendingSelections = new ConcurrentHashMap<>();
    private final VotingSessionService votingSessionService;

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getComponentId().startsWith("vote:select:")) {
            String[] parts    = event.getComponentId().split(":");
            long     sessionId = Long.parseLong(parts[2]);
            long     eventId   = Long.parseLong(parts[3]);
            String   memberId  = Objects.requireNonNull(event.getMember()).getId();
            String   pendingKey = memberId + ":" + sessionId;

            Set<Long> selections = pendingSelections.computeIfAbsent(
                    pendingKey, _ -> new LinkedHashSet<>());

            if (selections.contains(eventId)) {
                event.reply("⚠️ You already selected that match.").setEphemeral(true).queue();
                return;
            }

            selections.add(eventId);

            if (selections.size() < 2) {
                event.reply("✅ First match selected — now pick your second.").setEphemeral(true).queue();
                return;
            }

            pendingSelections.remove(pendingKey);

            VotingSessionService.VoteResult result = votingSessionService.castVote(
                    sessionId, event.getMember(), new ArrayList<>(selections));

            String reply = switch (result) {
                case ACCEPTED      -> "✅ Your vote has been locked in.";
                case ALREADY_VOTED -> "🔒 You have already voted in this session.";
                case SESSION_CLOSED -> "🔒 This voting session is no longer open.";
                case INVALID       -> "⚠️ Invalid selection — please try again.";
                case DENIED       -> "⚠️ Only players can vote.";
            };

            event.reply(reply).setEphemeral(true).queue();
        }
    }
}
