package com.dragon.commands.moderation;

import com.dragon.entity.MemberSanctionSummaryEntity;
import com.dragon.integration.SlashCommand;
import com.dragon.service.moderation.ModerationStatsService;
import com.dragon.service.moderation.ModerationStatsService.ServerModerationStats;
import com.dragon.utils.Embed;
import com.dragon.utils.IconRegistry;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CommandModStats implements SlashCommand {

    private static final int LOOKBACK_DAYS = 30;

    private final ModerationStatsService statsService;
    private final Embed embed;

    @Override
    public String getName() { return "modstats"; }

    @Override
    public String getDescription() { return "View server-wide moderation statistics (last 30 days)."; }

    @Override
    public void configure(SlashCommandData data) {
        data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MANAGE_SERVER)) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Permission Denied",
                    "You need the **Manage Server** permission to view moderation statistics.").build()
            ).setEphemeral(true).queue();
            return;
        }

        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        ServerModerationStats stats = statsService.getStats(guildId, LOOKBACK_DAYS);

        var builder = embed.info(IconRegistry.ICON_BOOK,
                        "📊 Moderation Statistics — " + event.getGuild().getName(),
                        "Activity overview for the last **" + LOOKBACK_DAYS + " days**.")
                .addField("🔴 Active Sanctions", String.valueOf(stats.activeSanctions()), true)
                .addField("📅 Issued (30d)",     String.valueOf(stats.sanctionsInPeriod()), true)
                .addField("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "​", false)
                .addField("⚠️ Warns (30d)",      String.valueOf(stats.warnsInPeriod()),    true)
                .addField("🔇 Mutes (30d)",      String.valueOf(stats.mutesInPeriod()),    true)
                .addField("⏱️ Timeouts (30d)",   String.valueOf(stats.timeoutsInPeriod()), true)
                .addField("🔨 Bans (30d)",       String.valueOf(stats.bansInPeriod()),     true);

        if (!stats.topOffenders().isEmpty()) {
            builder.addField("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "​", false);
            builder.addField("🏆 Most Sanctioned Members", "Top " + stats.topOffenders().size() + " by lifetime total:", false);

            for (int i = 0; i < stats.topOffenders().size(); i++) {
                MemberSanctionSummaryEntity s = stats.topOffenders().get(i);
                int total = s.getTotalWarns() + s.getTotalMutes() + s.getTotalTimeouts() + s.getTotalBans();
                builder.addField(
                        (i + 1) + ". " + s.getUserTag(),
                        "⚠️ " + s.getTotalWarns()
                                + "  🔇 " + s.getTotalMutes()
                                + "  ⏱️ " + s.getTotalTimeouts()
                                + "  🔨 " + s.getTotalBans()
                                + "  *(total: " + total + ")*",
                        false
                );
            }
        }

        builder.setTimestamp(Instant.now());
        event.replyEmbeds(builder.build()).setEphemeral(true).queue();
    }
}
