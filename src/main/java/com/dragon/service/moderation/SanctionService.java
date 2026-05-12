/*
---------------------------------------------------------------------------------
File Name : SanctionService

Developer : vakea
Email     : vakea@fluffici.eu
Real Name : Alex Guy Yann Le Roy

Date Created  : 18/06/2024
Last Modified : 07/05/2026

---------------------------------------------------------------------------------
*/

package com.dragon.service.moderation;

import com.dragon.component.SanctionMapper;
import com.dragon.dto.moderation.SanctionRequest;
import com.dragon.dto.moderation.SanctionResponse;
import com.dragon.dto.moderation.SanctionStatus;
import com.dragon.dto.moderation.SanctionType;
import com.dragon.entity.MemberSanctionSummaryEntity;
import com.dragon.entity.SanctionEntity;
import com.dragon.repository.MemberSanctionSummaryRepository;
import com.dragon.repository.SanctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SanctionService {

    private final SanctionRepository sanctionRepository;
    private final MemberSanctionSummaryRepository summaryRepository;
    private final SanctionMapper sanctionMapper;
    private final JDA jda;

    private static final int COLOR_WARN    = 0xF5A623; // amber
    private static final int COLOR_MUTE    = 0xE67E22; // orange
    private static final int COLOR_BAN     = 0xE74C3C; // red
    private static final int COLOR_TIMEOUT = 0xF5A623; // amber
    private static final int COLOR_OK      = 0x2ECC71; // green
    private static final int COLOR_STAFF   = 0xE74C3C; // red

    private static final String LOGO_URL     = "https://cdn.discordapp.com/attachments/1489008289126809650/1489008367551643658/logo.png?ex=69fcff1b&is=69fbad9b&hm=3488b0e24c9d0430efc37c42c991398ebefb0a7fc605e6acec5e21ed4b4b4020&";
    private static final String THUMB_WARN   = "https://cdn.discordapp.com/attachments/1501768878051692624/1501774762349690910/1778121411679.png?ex=6a0097fc&is=69ff467c&hm=274f127d83679c990ebde19eb056f8a8debe1b757ebaf5922458e994d50302f6&";
    private static final String THUMB_MUTE   = "https://cdn.discordapp.com/attachments/1501768878051692624/1501774761368485958/1778121411679.png?ex=6a0097fc&is=69ff467c&hm=ce61684e054c3b8f3951cfb08732b17d7a392d77fd6b0b398434322be451f641&";
    private static final String THUMB_BAN    = "https://cdn.discordapp.com/attachments/1501768878051692624/1501774760894267483/1778121411679.png?ex=6a0097fc&is=69ff467c&hm=f5dbdf09d7dee9ba40eafbfcb99f556315f331e7ffa92591958753e0922f36a4&";
    private static final String THUMB_OK     = "https://cdn.discordapp.com/attachments/1501768878051692624/1501774761737453729/1778121411679.png?ex=6a0097fc&is=69ff467c&hm=6a106e9eea7361819e3b6f3f0c853e2a2874997aab5b9f1de593791def63f738&";

    @Value("${dragon.sanctions.warn2.mute-duration-seconds:86400}")
    private long warn2MuteDurationSeconds;

    @Value("${dragon.sanctions.warn.reset-after-seconds:2592000}")
    private long warnResetAfterSeconds;

    @Value("${discord.moderation-log.channel.id}")
    private String moderationLogChannelId;

    @Transactional
    public SanctionResponse warn(SanctionRequest request) {
        SanctionEntity sanction = persist(request);
        updateSummary(request, SanctionType.WARN);

        int activeWarns = getActiveWarnCount(request.targetUserId(), request.guildId());

        log.info("[Sanctions] WARN #{} issued — target: {}, moderator: {}, reason: {}",
                activeWarns, request.targetUserTag(), request.moderatorUserTag(), request.reason());

        Guild guild = resolveGuild(request.guildId());

        switch (activeWarns) {
            case 1 -> handleFirstWarn(guild, request, sanction.getId());
            case 2 -> handleSecondWarn(guild, request, sanction.getId());
            default -> handleThirdWarn(guild, request, sanction.getId());
        }

        return sanctionMapper.toResponse(sanction);
    }

    private void handleFirstWarn(Guild guild, SanctionRequest request, long sanctionId) {
        guild.retrieveMemberById(request.targetUserId()).queue(member -> {
            if (member == null) return;
            member.getUser().openPrivateChannel().queue(channel -> {
                Container container = Container.of(
                        Section.of(
                                Thumbnail.fromUrl(THUMB_WARN),
                                TextDisplay.of(
                                        "# ⚠️ Warning — Dragon Team\n" +
                                                "**" + guild.getName() + "**\n" +
                                                "-# WARNING 1 OF 3"
                                )
                        ),

                        Separator.createDivider(Separator.Spacing.LARGE),

                        TextDisplay.of("-# **DETAILS**"),
                        Separator.createDivider(Separator.Spacing.SMALL),

                        TextDisplay.of("📋 Reason\n-# " + request.reason()),
                        TextDisplay.of("👮 Issued By\n-# " + request.moderatorUserTag()),

                        Separator.createDivider(Separator.Spacing.LARGE),

                        TextDisplay.of("-# **WHAT HAPPENS NEXT**"),
                        Separator.createDivider(Separator.Spacing.SMALL),

                        TextDisplay.of("⚠️ 2nd Warning\n-# Automatic temporary mute"),
                        TextDisplay.of("🔨 3rd Warning\n-# Suspension or permanent removal"),

                        Separator.createDivider(Separator.Spacing.LARGE),

                        TextDisplay.of("-# Warnings may be reset over time based on continued good behaviour.")
                ).withAccentColor(COLOR_WARN);

                channel.sendMessageComponents(container)
                        .useComponentsV2()
                        .queue();
            });
        });
    }

    private void handleSecondWarn(Guild guild, SanctionRequest request, long sanctionId) {
        log.warn("[Sanctions] 2nd warning threshold reached — applying temporary mute to {}",
                request.targetUserTag());

        SanctionRequest muteRequest = new SanctionRequest(
                request.targetUserId(),
                request.targetUserTag(),
                request.moderatorUserId(),
                request.moderatorUserTag(),
                request.guildId(),
                SanctionType.MUTE,
                "Automatic mute — 2nd warning threshold reached. Original reason: " + request.reason(),
                warn2MuteDurationSeconds
        );
        mute(muteRequest);

        guild.retrieveMemberById(request.targetUserId()).queue(member -> {
            if (member == null) return;
            member.getUser().openPrivateChannel().queue(channel -> {
                Container container = Container.of(
                        Section.of(
                                Thumbnail.fromUrl(THUMB_MUTE),
                                TextDisplay.of(
                                        "# 🔇 2nd Warning — Dragon Team\n" +
                                                "**" + guild.getName() + "**\n" +
                                                "-# WARNING 2 OF 3"
                                )
                        ),

                        Separator.createDivider(Separator.Spacing.LARGE),

                        TextDisplay.of("-# **DETAILS**"),
                        Separator.createDivider(Separator.Spacing.SMALL),

                        TextDisplay.of("📋 Reason\n-# " + request.reason()),
                        TextDisplay.of("👮 Issued By\n-# " + request.moderatorUserTag()),
                        TextDisplay.of("⏱️ Mute Duration\n-# " + formatDuration(warn2MuteDurationSeconds)),

                        Separator.createDivider(Separator.Spacing.LARGE),

                        TextDisplay.of("-# **ACTION APPLIED**"),
                        Separator.createDivider(Separator.Spacing.SMALL),

                        TextDisplay.of("🔇 Temporary Mute\n-# You have been muted for " + formatDuration(warn2MuteDurationSeconds)),
                        TextDisplay.of("⚠️ Final Warning\n-# A 3rd warning will result in suspension or permanent removal"),

                        Separator.createDivider(Separator.Spacing.LARGE),

                        TextDisplay.of("-# Please reflect on your behaviour and return ready to contribute positively.")
                ).withAccentColor(COLOR_MUTE);

                channel.sendMessageComponents(container)
                        .useComponentsV2()
                        .queue();
            });
        });

        notifyStaffOfEscalation(guild, request, 2);
    }

    private void handleThirdWarn(Guild guild, SanctionRequest request, long sanctionId) {
        log.warn("[Sanctions] 3rd warning threshold reached — suspending {} from the team",
                request.targetUserTag());

        /* TODO: Requires staff review before applying automatically.
         * ban(new SanctionRequest(..., SanctionType.BAN, "3rd warning threshold", null));
         */

        guild.retrieveMemberById(request.targetUserId()).queue(member -> {
            if (member == null) return;
            member.getUser().openPrivateChannel().queue(channel -> {
                Container container = Container.of(
                        Section.of(
                                Thumbnail.fromUrl(THUMB_BAN),
                                TextDisplay.of(
                                        "# 🔨 3rd Warning — Dragon Team\n" +
                                                "**" + guild.getName() + "**\n" +
                                                "-# FINAL WARNING"
                                )
                        ),

                        Separator.createDivider(Separator.Spacing.LARGE),

                        TextDisplay.of("-# **DETAILS**"),
                        Separator.createDivider(Separator.Spacing.SMALL),

                        TextDisplay.of("📋 Reason\n-# " + request.reason()),
                        TextDisplay.of("👮 Issued By\n-# " + request.moderatorUserTag()),

                        Separator.createDivider(Separator.Spacing.LARGE),

                        TextDisplay.of("-# **CONSEQUENCE**"),
                        Separator.createDivider(Separator.Spacing.SMALL),

                        TextDisplay.of("🚫 Permanent Suspension\n-# You have been suspended from the team pending staff review"),

                        Separator.createDivider(Separator.Spacing.LARGE),

                        TextDisplay.of("-# Dragon Team maintains a zero-tolerance policy for repeated misconduct.\n" +
                                "-# If you believe this was issued in mistake, please contact a staff member directly.")
                ).withAccentColor(COLOR_BAN);

                channel.sendMessageComponents(container)
                        .useComponentsV2()
                        .queue();
            });
        });

        notifyStaffOfEscalation(guild, request, 3);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timeout
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public SanctionResponse timeout(SanctionRequest request) {
        if (request.durationSeconds() == null || request.durationSeconds() <= 0)
            throw new IllegalArgumentException("Timeout requires a positive duration in seconds.");
        if (request.durationSeconds() > 2419200)
            throw new IllegalArgumentException("Timeout duration cannot exceed 28 days (Discord limit).");

        SanctionEntity sanction = persist(request);
        updateSummary(request, SanctionType.TIMEOUT);

        Guild guild = resolveGuild(request.guildId());
        guild.retrieveMemberById(request.targetUserId()).queue(member -> {
            if (member == null) return;
            member.timeoutFor(request.durationSeconds(), java.util.concurrent.TimeUnit.SECONDS)
                    .reason(request.reason())
                    .queue();

            member.getUser().openPrivateChannel().queue(channel -> {
                Container container = Container.of(
                        Section.of(
                                Thumbnail.fromUrl(THUMB_WARN),
                                TextDisplay.of(
                                        "# ⏱️ Timeout — Dragon Team\n" +
                                                "**" + guild.getName() + "**\n" +
                                                "-# DURATION: " + formatDuration(request.durationSeconds()).toUpperCase()
                                )
                        ),

                        Separator.createDivider(Separator.Spacing.LARGE),

                        TextDisplay.of("-# **DETAILS**"),
                        Separator.createDivider(Separator.Spacing.SMALL),

                        TextDisplay.of("📋 Reason\n-# " + request.reason()),
                        TextDisplay.of("👮 Issued By\n-# " + request.moderatorUserTag()),
                        TextDisplay.of("⏱️ Duration\n-# " + formatDuration(request.durationSeconds())),

                        Separator.createDivider(Separator.Spacing.LARGE),

                        TextDisplay.of("-# Please use this time to reflect and come back ready to contribute positively.")
                ).withAccentColor(COLOR_TIMEOUT);

                channel.sendMessageComponents(container)
                        .useComponentsV2()
                        .queue();
            });
        });

        log.info("[Sanctions] TIMEOUT issued — target: {}, duration: {}s, moderator: {}, reason: {}",
                request.targetUserTag(), request.durationSeconds(),
                request.moderatorUserTag(), request.reason());

        return sanctionMapper.toResponse(sanction);
    }

    @Transactional
    public SanctionResponse mute(SanctionRequest request) {
        SanctionEntity sanction = persist(request);
        updateSummary(request, SanctionType.MUTE);

        Guild guild = resolveGuild(request.guildId());
        guild.retrieveMemberById(request.targetUserId()).queue(member -> {
            if (member == null) return;
            // TODO: inject muted role ID via @Value and assign it here

            member.getUser().openPrivateChannel().queue(channel -> {
                String durationLine = request.durationSeconds() != null
                        ? formatDuration(request.durationSeconds())
                        : "Permanent";

                Container container = Container.of(
                        Section.of(
                                Thumbnail.fromUrl(THUMB_MUTE),
                                TextDisplay.of(
                                        "# 🔇 Mute — Dragon Team\n" +
                                                "**" + guild.getName() + "**\n" +
                                                "-# DURATION: " + durationLine.toUpperCase()
                                )
                        ),

                        Separator.createDivider(Separator.Spacing.LARGE),

                        TextDisplay.of("-# **DETAILS**"),
                        Separator.createDivider(Separator.Spacing.SMALL),

                        TextDisplay.of("📋 Reason\n-# " + request.reason()),
                        TextDisplay.of("👮 Issued By\n-# " + request.moderatorUserTag()),
                        TextDisplay.of("⏱️ Duration\n-# " + durationLine),

                        Separator.createDivider(Separator.Spacing.LARGE),

                        TextDisplay.of("-# If you believe this was issued in mistake, please contact a staff member.")
                ).withAccentColor(COLOR_MUTE);

                channel.sendMessageComponents(container)
                        .useComponentsV2()
                        .queue();
            });
        });

        log.info("[Sanctions] MUTE issued — target: {}, moderator: {}, reason: {}",
                request.targetUserTag(), request.moderatorUserTag(), request.reason());

        return sanctionMapper.toResponse(sanction);
    }

    @Transactional
    public SanctionResponse ban(SanctionRequest request) {
        SanctionEntity sanction = persist(request);
        updateSummary(request, SanctionType.BAN);

        Guild guild = resolveGuild(request.guildId());
        guild.retrieveMemberById(request.targetUserId()).queue(member -> {
            if (member != null) {
                member.getUser().openPrivateChannel().queue(channel -> {
                    String durationLine = request.durationSeconds() != null
                            ? formatDuration(request.durationSeconds())
                            : "Permanent";

                    Container container = Container.of(
                            Section.of(
                                    Thumbnail.fromUrl(THUMB_BAN),
                                    TextDisplay.of(
                                            "# 🔨 Ban — Dragon Team\n" +
                                                    "**" + guild.getName() + "**\n" +
                                                    "-# DURATION: " + durationLine.toUpperCase()
                                    )
                            ),

                            Separator.createDivider(Separator.Spacing.LARGE),

                            TextDisplay.of("-# **DETAILS**"),
                            Separator.createDivider(Separator.Spacing.SMALL),

                            TextDisplay.of("📋 Reason\n-# " + request.reason()),
                            TextDisplay.of("👮 Issued By\n-# " + request.moderatorUserTag()),
                            TextDisplay.of("⏱️ Duration\n-# " + durationLine),

                            Separator.createDivider(Separator.Spacing.LARGE),

                            TextDisplay.of("-# **WHAT THIS MEANS**"),
                            Separator.createDivider(Separator.Spacing.SMALL),

                            TextDisplay.of("🚫 Server Access\n-# You have been removed from the server"),
                            TextDisplay.of("📋 Team Participation\n-# You are no longer eligible for team activities"),

                            Separator.createDivider(Separator.Spacing.LARGE),

                            TextDisplay.of("-# If you believe this was issued in mistake, please contact a staff member.")
                    ).withAccentColor(COLOR_BAN);

                    channel.sendMessageComponents(container)
                            .useComponentsV2()
                            .queue(msg ->
                                    guild.ban(UserSnowflake.fromId(request.targetUserId()), 0,
                                                    java.util.concurrent.TimeUnit.SECONDS)
                                            .reason(request.reason())
                                            .queue()
                            );
                });
            } else {
                guild.ban(UserSnowflake.fromId(request.targetUserId()), 0,
                                java.util.concurrent.TimeUnit.SECONDS)
                        .reason(request.reason())
                        .queue();
            }
        });

        log.info("[Sanctions] BAN issued — target: {}, moderator: {}, reason: {}",
                request.targetUserTag(), request.moderatorUserTag(), request.reason());

        return sanctionMapper.toResponse(sanction);
    }

    @Transactional
    public void revoke(Long sanctionId, String revokedByTag) {
        SanctionEntity sanction = sanctionRepository.findById(sanctionId)
                .orElseThrow(() -> new IllegalArgumentException("Sanction not found: " + sanctionId));

        sanction.setStatus(SanctionStatus.REVOKED);
        sanction.setRevokedAt(Instant.now());
        sanction.setRevokedBy(revokedByTag);
        sanctionRepository.save(sanction);

        if (sanction.getType() == SanctionType.WARN) {
            summaryRepository.findByUserIdAndGuildId(sanction.getTargetUserId(), sanction.getGuildId())
                    .ifPresent(summary -> {
                        summary.decrementActiveWarns();
                        summaryRepository.save(summary);
                    });
        }

        log.info("[Sanctions] Sanction {} revoked by {}", sanctionId, revokedByTag);
    }

    public List<SanctionResponse> getHistory(String userId, String guildId) {
        return sanctionRepository.findByTargetUserIdAndGuildId(userId, guildId)
                .stream()
                .map(sanctionMapper::toResponse)
                .toList();
    }

    public List<SanctionResponse> getActiveSanctions(String userId, String guildId) {
        return sanctionRepository.findByTargetUserIdAndGuildIdAndStatus(userId, guildId, SanctionStatus.ACTIVE)
                .stream()
                .map(sanctionMapper::toResponse)
                .toList();
    }

    public int getActiveWarnCount(String userId, String guildId) {
        return sanctionRepository.countByTargetUserIdAndGuildIdAndTypeAndStatus(
                userId, guildId, SanctionType.WARN, SanctionStatus.ACTIVE);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void processExpiredSanctions() {
        List<SanctionEntity> expired = sanctionRepository.findExpiredSanctions(Instant.now());
        if (expired.isEmpty()) return;

        log.info("[Sanctions] Processing {} expired sanctions", expired.size());

        for (SanctionEntity sanction : expired) {
            sanction.setStatus(SanctionStatus.EXPIRED);
            sanctionRepository.save(sanction);

            try {
                Guild guild = jda.getGuildById(sanction.getGuildId());
                if (guild == null) continue;

                switch (sanction.getType()) {
                    case BAN -> guild.unban(UserSnowflake.fromId(sanction.getTargetUserId()))
                            .reason("Temporary ban expired.")
                            .queue();
                    case WARN -> summaryRepository
                            .findByUserIdAndGuildId(sanction.getTargetUserId(), sanction.getGuildId())
                            .ifPresent(summary -> {
                                summary.decrementActiveWarns();
                                summaryRepository.save(summary);
                            });
                    default -> {}
                }

                log.info("[Sanctions] Sanction {} expired — type: {}, user: {}",
                        sanction.getId(), sanction.getType(), sanction.getTargetUserTag());

            } catch (Exception e) {
                log.error("[Sanctions] Failed to process expired sanction {}", sanction.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelay = 86_400_000)
    @Transactional
    public void processWarnResets() {
        Instant resetThreshold = Instant.now().minus(warnResetAfterSeconds, ChronoUnit.SECONDS);
        List<SanctionEntity> eligibleWarns = sanctionRepository.findEligibleWarnResets(resetThreshold);
        if (eligibleWarns.isEmpty()) return;

        log.info("[Sanctions] Resetting {} warn(s) due to good behaviour", eligibleWarns.size());

        for (SanctionEntity warn : eligibleWarns) {
            boolean hasRecentViolation = sanctionRepository
                    .findByTargetUserIdAndGuildIdAndStatus(
                            warn.getTargetUserId(), warn.getGuildId(), SanctionStatus.ACTIVE)
                    .stream()
                    .anyMatch(s -> s.getType() != SanctionType.WARN
                            || s.getCreatedAt().isAfter(warn.getCreatedAt()));

            if (hasRecentViolation) continue;

            warn.setStatus(SanctionStatus.EXPIRED);
            warn.setRevokedBy("SYSTEM — Good behaviour reset");
            warn.setRevokedAt(Instant.now());
            sanctionRepository.save(warn);

            summaryRepository.findByUserIdAndGuildId(warn.getTargetUserId(), warn.getGuildId())
                    .ifPresent(summary -> {
                        summary.decrementActiveWarns();
                        summaryRepository.save(summary);
                    });

            log.info("[Sanctions] Warn {} reset for user {} due to good behaviour",
                    warn.getId(), warn.getTargetUserTag());

            jda.retrieveUserById(warn.getTargetUserId()).queue(user -> {
                if (user == null) return;
                Guild guild = jda.getGuildById(warn.getGuildId());
                if (guild == null) return;

                user.openPrivateChannel().queue(channel -> {
                    Container container = Container.of(
                            Section.of(
                                    Thumbnail.fromUrl(THUMB_OK),
                                    TextDisplay.of(
                                            "# ✅ Warning Cleared — Dragon Team\n" +
                                                    "**" + guild.getName() + "**\n" +
                                                    "-# GOOD BEHAVIOUR RESET"
                                    )
                            ),

                            Separator.createDivider(Separator.Spacing.LARGE),

                            TextDisplay.of("-# **WHAT HAPPENED**"),
                            Separator.createDivider(Separator.Spacing.SMALL),

                            TextDisplay.of("🎉 Warning Cleared\n-# One of your warnings has been automatically cleared"),
                            TextDisplay.of("📅 Reason\n-# Continued good standing for the required period"),

                            Separator.createDivider(Separator.Spacing.LARGE),

                            TextDisplay.of("-# Keep it up and stay positive — we appreciate the effort!")
                    ).withAccentColor(COLOR_OK);

                    channel.sendMessageComponents(container)
                            .useComponentsV2()
                            .queue();
                });
            });
        }
    }

    private void notifyStaffOfEscalation(Guild guild, SanctionRequest request, int warnCount) {
        var channel = guild.getTextChannelById(moderationLogChannelId);
        if (channel == null) {
            log.warn("[Sanctions] Moderation log channel not found: {}", moderationLogChannelId);
            return;
        }

        String actionLine = warnCount == 2
                ? "🔇 Temporary Mute — " + formatDuration(warn2MuteDurationSeconds)
                : "🔨 Permanent Suspension — Pending staff review";

        Container container = Container.of(
                Section.of(
                        Thumbnail.fromUrl(LOGO_URL),
                        TextDisplay.of(
                                "# 🚨 Warn Escalation — Warning #" + warnCount + "\n" +
                                        "**Dragon Team Moderation**\n" +
                                        "-# AUTOMATIC ACTION APPLIED"
                        )
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# **MEMBER**"),
                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("👤 Tag\n-# " + request.targetUserTag()),
                TextDisplay.of("🆔 User ID\n-# " + request.targetUserId()),
                TextDisplay.of("⚠️ Active Warnings\n-# " + warnCount + " of 3"),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# **SANCTION**"),
                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("👮 Issued By\n-# " + request.moderatorUserTag()),
                TextDisplay.of("📋 Reason\n-# " + request.reason()),
                TextDisplay.of("🔧 Action Applied\n-# " + actionLine),
                TextDisplay.of("🕐 Timestamp\n-# " + Instant.now())
        ).withAccentColor(COLOR_STAFF);

        channel.sendMessageComponents(container)
                .useComponentsV2()
                .queue();
    }

    private static final int MAX_REASON_LENGTH = 1000;

    private SanctionEntity persist(SanctionRequest request) {
        Instant expiresAt = request.durationSeconds() != null
                ? Instant.now().plus(request.durationSeconds(), ChronoUnit.SECONDS)
                : null;

        String reason = request.reason();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Sanction reason cannot be empty.");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            reason = reason.substring(0, MAX_REASON_LENGTH - 3) + "...";
        }

        return sanctionRepository.save(SanctionEntity.builder()
                .guildId(request.guildId())
                .targetUserId(request.targetUserId())
                .targetUserTag(request.targetUserTag())
                .moderatorUserId(request.moderatorUserId())
                .moderatorUserTag(request.moderatorUserTag())
                .type(request.type())
                .reason(reason)
                .durationSeconds(request.durationSeconds())
                .expiresAt(expiresAt)
                .status(SanctionStatus.ACTIVE)
                .build());
    }

    private void updateSummary(SanctionRequest request, SanctionType type) {
        MemberSanctionSummaryEntity summary = summaryRepository
                .findByUserIdAndGuildId(request.targetUserId(), request.guildId())
                .orElseGet(() -> MemberSanctionSummaryEntity.builder()
                        .userId(request.targetUserId())
                        .guildId(request.guildId())
                        .userTag(request.targetUserTag())
                        .build()
                );
        summary.increment(type);
        summaryRepository.save(summary);
    }

    private Guild resolveGuild(String guildId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) throw new IllegalStateException("Guild not found: " + guildId);
        return guild;
    }

    private String formatDuration(long seconds) {
        if (seconds < 60)    return seconds + " second(s)";
        if (seconds < 3600)  return (seconds / 60) + " minute(s)";
        if (seconds < 86400) return (seconds / 3600) + " hour(s)";
        return (seconds / 86400) + " day(s)";
    }
}