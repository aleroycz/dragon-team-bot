package com.dragon.integration;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

public interface SlashCommand {

    String getName();

    String getDescription();

    default boolean isGuildOnly() {
        return false;
    }

    default List<Guild> getTargetGuilds(JDA jda) {
        return List.of();
    }

    void execute(SlashCommandInteractionEvent event);

    default void configure(SlashCommandData data) { }
}
