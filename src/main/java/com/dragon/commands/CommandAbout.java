package com.dragon.commands;

import com.dragon.integration.SlashCommand;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommandAbout implements SlashCommand {

    public static final String LOGO_URL     = "https://cdn.discordapp.com/attachments/1489008289126809650/1489008367551643658/logo.png?ex=69fcff1b&is=69fbad9b&hm=3488b0e24c9d0430efc37c42c991398ebefb0a7fc605e6acec5e21ed4b4b4020&";
    private static final String GITHUB_URL   = "https://github.com/aleroycz/dragon-team-bot";
    private static final String LICENSE_URL  = "https://raw.githubusercontent.com/aleroycz/dragon-team-bot/refs/heads/main/LICENSE";
    private static final String CIPRON_URL   = "https://cipron.eu";

    @Value("${discord.bot.version}")
    private String version;

    @Override
    public String getName() {
        return "about";
    }

    @Override
    public String getDescription() {
        return "Seek information about the bot.";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Container container = Container.of(

                Section.of(
                        Button.link(GITHUB_URL, "Source Code"),
                        TextDisplay.of("# 🐉 Dragon Bot"),
                        TextDisplay.of("A powerful, modular Discord bot built with **JDA** & **Spring Boot**.")
                ).withAccessory(Thumbnail.fromUrl(LOGO_URL)),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of(
                        "**Version** — `v" + version + "`\n" +
                                "**Author** — Vakea\n" +
                                "**JDA** — `" + JDAInfo.VERSION + "-" + JDAInfo.COMMIT_HASH + "`"
                ),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of(
                        "**License** — Cipron Proprietary License (CPL) v1.1\n" +
                                "-# © 2026 Cipron s.r.o. · All rights reserved. " +
                                "Unauthorized use, reproduction or distribution is strictly prohibited."
                ),

                Separator.createDivider(Separator.Spacing.SMALL),

                ActionRow.of(
                        Button.link(LICENSE_URL,  "📄 View License"),
                        Button.link(CIPRON_URL,   "🌐 cipron.eu")
                )
        );

        event.replyComponents(container)
                .useComponentsV2()
                .queue();
    }
}