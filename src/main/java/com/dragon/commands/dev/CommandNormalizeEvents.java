package com.dragon.commands.dev;

import com.dragon.dto.event.EventStatus;
import com.dragon.entity.event.Event;
import com.dragon.integration.SlashCommand;
import com.dragon.repository.event.EventRepository;
import com.dragon.repository.event.VotingSessionRepository;
import com.dragon.repository.event.VotingSessionVoteRepository;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CommandNormalizeEvents implements SlashCommand {

    private final EventRepository eventRepository;
    private final VotingSessionVoteRepository votingSessionVoteRepository;
    private final VotingSessionRepository votingSessionRepository;

    @Override
    public String getName() {
        return "normalize";
    }

    @Override
    public String getDescription() {
        return "Normalizing all events to avoid anomalies.";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (Objects.requireNonNull(event.getMember()).hasPermission(Permission.ADMINISTRATOR)) {

            for (Event event1 : eventRepository.findAll()) {
                event1.setStatus(EventStatus.STANDBY);
                eventRepository.save(event1);
            }

            votingSessionVoteRepository.findAll().forEach(votingSessionVoteRepository::delete);
            votingSessionRepository.findAll().forEach(votingSessionRepository::delete);

            event.reply("All Discord events has been set to Standby.")
                    .queue();
        } else {
            event.reply("You do not have the permission to use this command.")
                    .setEphemeral(true).queue();
        }
    }
}
