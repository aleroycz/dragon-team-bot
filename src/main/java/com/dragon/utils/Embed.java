package com.dragon.utils;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class Embed {

    private final JDA jda;

    private User getSelfUser() {
        return jda.getSelfUser();
    }

    public EmbedBuilder simpleFieldedEmbed(String title, String description, Color color, List<MessageEmbed.Field> fields) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setTimestamp(Instant.now())
                .setFooter(getSelfUser().getGlobalName(), getSelfUser().getEffectiveAvatarUrl());

        fields.forEach(eb::addField);
        return eb;
    }

    public EmbedBuilder simpleAuthoredEmbed(User user, String title, String description, Color color) {
        return new EmbedBuilder()
                .setAuthor(user.getEffectiveName(), user.getEffectiveAvatarUrl(), user.getEffectiveAvatarUrl())
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setTimestamp(Instant.now())
                .setFooter(getSelfUser().getGlobalName(), getSelfUser().getEffectiveAvatarUrl());
    }

    public EmbedBuilder simpleAuthoredEmbed() {
        return new EmbedBuilder()
                .setColor(Color.decode("#2F3136"))
                .setTimestamp(Instant.now())
                .setFooter(getSelfUser().getGlobalName(), getSelfUser().getEffectiveAvatarUrl());
    }

    public EmbedBuilder simpleEmbed(String title, String description, Color color) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setTimestamp(Instant.now())
                .setFooter(getSelfUser().getGlobalName(), getSelfUser().getEffectiveAvatarUrl());
    }

    public EmbedBuilder info(String icon, String title, String description) {
        EmbedBuilder eb = simpleAuthoredEmbed();

        if (jda == null) {
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("JDA Not initialized.");
            embedBuilder.setDescription("Unable to create Embed.");
            embedBuilder.setColor(Color.red);
            return embedBuilder;
        }

        if (icon == null || icon.isBlank()) {
            eb.setTitle(title);
        } else {
            eb.setAuthor(title, "https://sentralyx.com", icon);
        }

        return eb
                .setDescription(description)
                .setColor(Color.decode("#2F3136"))
                .setFooter(getSelfUser().getGlobalName(), getSelfUser().getAvatarUrl());
    }

    public EmbedBuilder warn(String icon, String title, String description) {
        return info(icon, title, description)
                .setColor(Color.YELLOW);
    }

    public EmbedBuilder error(String icon, String title, String description) {
        return info(icon, title, description)
                .setColor(Color.RED);
    }

    public EmbedBuilder image(String title, String description, String url) {
        return info(null, title, description)
                .setImage(url)
                .setColor(Color.decode("#2F3136"));
    }
}