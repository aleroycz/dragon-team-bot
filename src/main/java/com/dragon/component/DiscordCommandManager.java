package com.dragon.component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import com.dragon.integration.SlashCommand;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordCommandManager {

    private final JDA jda;
    private final List<SlashCommand> commands;

    @PostConstruct
    public void initialize() {
        log.info("Starting Discord slash command registration... Found {} commands", commands.size());

        List<CommandData> globalCommands = new ArrayList<>();
        Map<Long, List<CommandData>> guildCommands = new HashMap<>();

        for (SlashCommand cmd : commands) {
            SlashCommandData data = Commands.slash(cmd.getName(), cmd.getDescription());
            data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_PERMISSIONS));

            cmd.configure(data);

            if (cmd.isGuildOnly()) {
                List<Guild> targetGuilds = cmd.getTargetGuilds(jda);
                if (targetGuilds.isEmpty()) {
                    log.warn("Guild-only command {} has no target guilds!", cmd.getName());
                    continue;
                }

                for (Guild guild : targetGuilds) {
                    guildCommands.computeIfAbsent(guild.getIdLong(), k -> new ArrayList<>()).add(data);
                }
            } else {
                globalCommands.add(data);
            }
        }

        if (!globalCommands.isEmpty()) {
            log.info("Registering {} global commands...", globalCommands.size());
            jda.updateCommands()
                    .addCommands(globalCommands)
                    .queue(
                            success -> log.info("Global commands registered/updated successfully"),
                            failure -> log.error("Failed to register global commands", failure)
                    );
        }

        guildCommands.forEach((guildId, cmdList) -> {
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                log.warn("Cannot find guild {} for command registration", guildId);
                return;
            }

            log.info("Registering {} commands for guild {} ({})",
                    cmdList.size(), guild.getName(), guildId);

            guild.updateCommands()
                    .addCommands(cmdList)
                    .queue(
                            success -> log.info("Guild {} commands updated", guild.getName()),
                            failure -> log.error("Failed to update commands for guild {}", guild.getName(), failure)
                    );
        });
    }
}
