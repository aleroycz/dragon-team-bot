/*
---------------------------------------------------------------------------------
File Name : CommandSetRules

Developer : vakea
Email     : vakea@fluffici.eu
Real Name : Alex Guy Yann Le Roy

Date Created  : 18/06/2024
Last Modified : 07/05/2026

---------------------------------------------------------------------------------
*/

package com.dragon.commands.dev;

import com.dragon.integration.SlashCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Posts the Dragon Team rules as a rich Components V2 message.
 *
 * Each category has a hosted header banner (upload the generated PNGs to a CDN/attachment
 * and replace the IMG_* constants below with the real URLs).
 */
@Component
public class CommandSetRules extends ListenerAdapter implements SlashCommand {

    public static final String BUTTON_ACCEPT_RULES = "accept-rules";

    private static final String LOGO_URL         = "https://cdn.discordapp.com/attachments/1489008289126809650/1489008367551643658/logo.png?ex=69fcff1b&is=69fbad9b&hm=3488b0e24c9d0430efc37c42c991398ebefb0a7fc605e6acec5e21ed4b4b4020&";
    private static final String IMG_GENERAL      = "https://media.discordapp.net/attachments/1501768878051692624/1501768891968258158/1778120011114.png?ex=69fd46c5&is=69fbf545&hm=bd211d757960bc14fa50b5534862b2ef80c16e484ce5741c0bd449382bcf61c3&animated=true";
    private static final String IMG_DISCORD      = "https://media.discordapp.net/attachments/1501768878051692624/1501768894443028531/1778120011114.png?ex=69fd46c5&is=69fbf545&hm=0b567691a33ee1ddc8b024969533d65ece7d04e756eaebe00ac863b03d7af948&animated=true";
    private static final String IMG_VALORANT     = "https://media.discordapp.net/attachments/1501768878051692624/1501768894077997056/1778120011114.png?ex=69fd46c5&is=69fbf545&hm=73d6630b2bdb6b87893466b7bcb28cad2202789f9cbfe5f543cbd57fb5bb0dbd&animated=true";
    private static final String IMG_WARNINGS     = "https://media.discordapp.net/attachments/1501768878051692624/1501768893834723411/1778120011114.png?ex=69fd46c5&is=69fbf545&hm=d17ccd6790080a4b944a7a29500def40d6b2eb9a7bf06dbded716f8b5381055b&animated=true";
    private static final String IMG_BANS         = "https://media.discordapp.net/attachments/1501768878051692624/1501768893495115776/1778120011114.png?ex=69fd46c5&is=69fbf545&hm=5613e7a3a3785eb621c625d3e3f0174cf9efc4576be9176fc6fd49cf53396ce5&animated=true";
    private static final String IMG_CHEATING     = "https://media.discordapp.net/attachments/1501768878051692624/1501768893138468884/1778120011114.png?ex=69fd46c5&is=69fbf545&hm=fc9b4eddcf0ef36388b3f62416e9db335e39a7d4e5e979fdfe80bdcf3a5db231&animated=true";
    private static final String IMG_COMMS        = "https://media.discordapp.net/attachments/1501768878051692624/1501768892849324142/1778120011114.png?ex=69fd46c5&is=69fbf545&hm=ffd3657f91703c3e05fe3d58b5b77b1518570ea7d2cfc29d45a3165e6248db71&animated=true";
    private static final String IMG_SANCTIONS    = "https://media.discordapp.net/attachments/1501768878051692624/1501768892559654963/1778120011114.png?ex=69fd46c5&is=69fbf545&hm=beb38ef37a5dc887d1519223953f895799d970d88c5135eb57c8e3b386daf9f9&animated=true";
    private static final String IMG_FINAL        = "https://media.discordapp.net/attachments/1501768878051692624/1501768892295676114/1778120011114.png?ex=69fd46c5&is=69fbf545&hm=bcae5c975c51a5b3b666a7d1826de65c56351ae5986a54e41201bc5dc6de8f99&animated=true";

    @Override
    public String getName() { return "set-rules"; }

    @Override
    public String getDescription() { return "Display the rules in the channel"; }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ You don't have permission to use this command.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TextChannel channel = (TextChannel) event.getChannel();
        sendRulesPartOne(channel);   // Rules 1–5  (~19 components inside container)
        sendRulesPartTwo(channel);   // Rules 6–9 + accept button  (~18 components inside container)

        event.reply("✅ Rules have been posted successfully.")
                .setEphemeral(true)
                .queue();
    }

    // Part 1: Hero + General + Discord + Competitive + Warnings + Bans
    // Component count: 1 Section + (5× MediaGallery + Separator.SMALL + TextDisplay + Separator.LARGE) = 1 + 15 = 16 + Container = 17
    private void sendRulesPartOne(TextChannel channel) {
        Container container = Container.of(

                // ── Hero header ──────────────────────────────────────────────────────
                Section.of(
                        Button.link("https://sentralyx.com/", "🌐 Website"),
                        TextDisplay.of("# 🐉 Dragon Team — Rules & Code of Conduct"),
                        TextDisplay.of(
                                "Welcome to **Dragon Team**. By joining this server and team you agree " +
                                        "to abide by **all** of the rules listed below.\n" +
                                        "These rules exist to ensure **fairness**, **respect**, and the best " +
                                        "possible experience for everyone."
                        )
                ).withAccessory(Thumbnail.fromUrl(LOGO_URL)),

                Separator.createDivider(Separator.Spacing.LARGE),

                MediaGallery.of(MediaGalleryItem.fromUrl(IMG_GENERAL)),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(
                        "• Treat all teammates, staff, and opponents with **respect**\n" +
                                "• No harassment, hate speech, discrimination, or toxicity of any kind\n" +
                                "• Constructive criticism is always welcome — pure toxicity is not\n" +
                                "• Casual banter is fine — keep it **out of in-game chat**\n" +
                                "• Represent the team **professionally** at all times"
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                MediaGallery.of(MediaGalleryItem.fromUrl(IMG_DISCORD)),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(
                        "• Follow [Discord Terms of Service](https://discord.com/terms) & Community Guidelines at all times\n" +
                                "• No spam or NSFW content outside of designated channels\n" +
                                "• Absolutely **no** illegal content, IRL material, or malicious links\n" +
                                "• Use channels for their intended purpose — keep things organised"
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                MediaGallery.of(MediaGalleryItem.fromUrl(IMG_VALORANT)),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(
                        "• Follow [Riot Games Terms of Service](https://www.riotgames.com/en/terms-of-service) at all times\n" +
                                "• **NO** cheating, exploiting, or use of unfair third-party tools — ever\n" +
                                "• No smurfing or account sharing during any team activity\n" +
                                "• Always play to win unless the team has mutually agreed otherwise"
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                MediaGallery.of(MediaGalleryItem.fromUrl(IMG_WARNINGS)),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(
                        "*Applies to minor or first-time offences — e.g. mild toxicity, minor rule violations*\n\n" +
                                "`1st Warning` → Verbal or written warning\n" +
                                "`2nd Warning` → Temporary restriction *(mute / bench)*\n" +
                                "`3rd Warning` → Suspension or removal from the team\n\n" +
                                "> Warnings may reset over time based on continued good behaviour."
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                MediaGallery.of(MediaGalleryItem.fromUrl(IMG_BANS)),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(
                        "Serious violations result in **immediate action** — no prior warning required.\n\n" +
                                "**Zero Tolerance for:**\n" +
                                "• Cheating of any kind\n" +
                                "• Harassment, threats, or hate speech\n" +
                                "• Leaking confidential team information\n" +
                                "• Repeated toxic behaviour despite warnings\n\n" +
                                "**Possible actions:**\n" +
                                "• Temporary or permanent suspension\n" +
                                "• Removal from the Discord server\n" +
                                "• Report to game developers where applicable"
                )
        );

        channel.sendMessageComponents(container)
                .useComponentsV2()
                .queue();
    }

    // Part 2: Cheating + Comms + Sanctions + Final + Accept button
    // Component count: 4× (MediaGallery + Separator.SMALL + TextDisplay + Separator.LARGE) + ActionRow = 17 + Container = 18
    private void sendRulesPartTwo(TextChannel channel) {
        Container container = Container.of(
                MediaGallery.of(MediaGalleryItem.fromUrl(IMG_CHEATING)),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(
                        "**ANY form of cheating = INSTANT PERMANENT BAN. No exceptions, no appeals.**\n\n" +
                                "This includes: aimbots, wallhacks, scripts, competitive bug exploiting, boosting, or account sharing.\n\n" +
                                "**Consequences:**\n" +
                                "• Immediate removal from the team and Discord\n" +
                                "• Reported directly to Riot Games\n" +
                                "• Permanently blacklisted from all future participation"
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                MediaGallery.of(MediaGalleryItem.fromUrl(IMG_COMMS)),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(
                        "All **voice and in-game chat** must remain in **English** at all times. " +
                                "Not everyone shares the same language and we want every member to feel included.\n\n" +
                                "Keep all in-game communication **professional and respectful:**\n" +
                                "• No obscene language or content\n" +
                                "• No belittling or disrespecting teammates\n\n" +
                                "> If you ever feel uncomfortable about a topic being discussed, please speak up. " +
                                "We value your wellbeing above all else."
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                MediaGallery.of(MediaGalleryItem.fromUrl(IMG_SANCTIONS)),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(
                        "If we become aware that you have previously received sanctions from a game developer, " +
                                "your participation in certain team events may be restricted depending on severity " +
                                "*(e.g. Premier, ranked matches, official scrims, etc.)*.\n\n" +
                                "> This addendum was reviewed and validated by **Vakea** and **Starfloof** following previous discrepancies."
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                MediaGallery.of(MediaGalleryItem.fromUrl(IMG_FINAL)),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(
                        "• These rules are in place to ensure **fairness, respect, and performance** for all members\n" +
                                "• Being part of **Dragon Team** means you agree to **all of the above**\n" +
                                "• Rules may evolve over time — all changes will be communicated in advance\n\n" +
                                "-# Last updated: 02/04/2026 · 🐉 Dragon Team"
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.success(BUTTON_ACCEPT_RULES, "I have read and accept the rules.")
                                .withEmoji(Emoji.fromCustom("checks", 1315406365438644315L, false))
                )
        );

        channel.sendMessageComponents(container)
                .useComponentsV2()
                .queue();
    }
}