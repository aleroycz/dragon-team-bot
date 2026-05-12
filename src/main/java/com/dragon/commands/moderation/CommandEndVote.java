package com.dragon.commands.moderation;

import com.dragon.integration.SlashCommand;
import com.dragon.service.VotingSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommandEndVote implements SlashCommand {

    private final VotingSessionService votingSessionService;

    @Override
    public String getName() {
        return "end-vote";
    }

    @Override
    public String getDescription() {
        return "Ending the current voting session.";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (Objects.requireNonNull(event.getMember()).hasPermission(Permission.ADMINISTRATOR)) {
            this.votingSessionService.forceCloseCurrentSession();

            event.reply("You closed the current voting session.").setEphemeral(true)
                    .queue();
        } else {
            event.reply("You do not have the permission to run this command.")
                    .setEphemeral(true)
                    .queue();
        }
    }
}
