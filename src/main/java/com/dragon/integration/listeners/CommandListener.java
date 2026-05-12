package com.dragon.integration.listeners;

import io.sentry.Sentry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import com.dragon.integration.DiscordEventListener;
import com.dragon.integration.SlashCommand;
import com.dragon.utils.Embed;
import com.dragon.utils.IconRegistry;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.dragon.commands.dev.CommandSetRules.BUTTON_ACCEPT_RULES;

@Component
public class CommandListener extends ListenerAdapter implements DiscordEventListener {

    private final Map<String, SlashCommand> commandMap;

    public CommandListener(List<SlashCommand> commands) {
        this.commandMap = commands.stream()
                .collect(Collectors.toMap(SlashCommand::getName, Function.identity()));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        SlashCommand cmd = commandMap.get(event.getName());
        if (cmd != null) {
            try {
                cmd.execute(event);
            } catch (Exception e) {
                Sentry.logger().error("Command execution failed: {}", event.getName(), e);
                if (!event.isAcknowledged()) {
                    event.reply("Internal error occurred").setEphemeral(true).queue();
                }
            }
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().equals(BUTTON_ACCEPT_RULES)) return;

        Member member = event.getMember();
        Guild guild = event.getGuild();
        if (member == null || guild == null) return;

        boolean alreadyAccepted = member.getRoles().stream()
                .anyMatch(role -> role.getName().startsWith("✅ Rules Accepted"));
        if (alreadyAccepted) {
            event.reply("You have already accepted the rules!")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String today = java.time.LocalDate.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        String roleName = "✅ Rules Accepted — " + today;

        Embed embed = new Embed(event.getJDA());

        guild.createRole()
                .setName(roleName)
                .setPermissions(0L)
                .setMentionable(false)
                .setHoisted(false)
                .queue(
                        role -> guild.addRoleToMember(member, role).queue(
                                success -> event.replyEmbeds(
                                        embed.info(
                                                IconRegistry.ICON_CHECKS,
                                                "Rules Accepted — Welcome to Dragon Team!",
                                                """
                                                Thank you, **%s**! 🐉
                                                
                                                By accepting our rules, you've taken the first step towards \
                                                being a great member of **Dragon Team**.
                                                
                                                Please remember that these rules are here to ensure the best \
                                                experience for everyone. If you ever have any questions, \
                                                don't hesitate to reach out to a staff member.
                                                
                                                We're glad to have you with us. Let's get it! 🔥
                                                """.formatted(member.getEffectiveName())
                                        ).build()
                                ).setEphemeral(true).queue(),
                                error -> {
                                    error.printStackTrace();
                                    event.reply("Something went wrong while assigning your role. Please contact a staff member.")
                                            .setEphemeral(true)
                                            .queue();
                                }
                        ),
                        error -> {
                            error.printStackTrace();
                            event.reply("Something went wrong while creating your role. Please contact a staff member.")
                                    .setEphemeral(true)
                                    .queue();
                        }
                );
    }
}
