package com.dragon.service.ticket;

import com.dragon.dto.ticket.TicketStatus;
import com.dragon.entity.TicketConfig;
import com.dragon.entity.TicketEntity;
import com.dragon.entity.TicketType;
import com.dragon.repository.TicketRepository;
import com.dragon.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Core lifecycle service for the ticket system.
 *
 * <h3>Channel creation strategy</h3>
 * Discord REST calls (category creation, channel creation, permission overwrites)
 * are performed inside a virtual thread via {@link Thread#ofVirtual()} using
 * blocking {@code .complete()} calls. This avoids deadlocking the JDA event thread
 * while still letting Spring manage the surrounding {@code @Transactional} context.
 *
 * <h3>Auto-category</h3>
 * If the guild config does not have a category ID for open tickets, one is created
 * automatically and saved back to the config. This means zero manual Discord setup
 * is required — the system is self-provisioning on first use.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    private static final String CATEGORY_OPEN_NAME   = "🎫 Open Tickets";
    private static final String CATEGORY_CLOSED_NAME = "🔒 Closed Tickets";

    private static final String LOGO_URL = "https://cdn.discordapp.com/attachments/1489008289126809650/1489008367551643658/logo.png?ex=69fcff1b&is=69fbad9b&hm=3488b0e24c9d0430efc37c42c991398ebefb0a7fc605e6acec5e21ed4b4b4020&";

    private static final int COLOR_OPEN    = 0x5CB85C; // green
    private static final int COLOR_CLOSE   = 0xD9534F; // red
    private static final int COLOR_CLAIM   = 0x5A78C8; // blue
    private static final int COLOR_BUG     = 0xF0AD4E; // amber

    private final TicketConfigService configService;
    private final TicketRepository ticketRepository;
    private final TicketTypeRepository typeRepository;

    // ── Open ──────────────────────────────────────────────────────────────────

    /**
     * Opens a ticket for a member and asynchronously creates the Discord channel.
     *
     * The DB record is persisted first (without a channelId) so the interaction
     * can respond immediately. The channel ID is back-filled after Discord confirms
     * the channel creation.
     *
     * @throws IllegalStateException if the member already has an open ticket, or if
     *                               the ticket system is disabled for the guild.
     */
    @Transactional
    public TicketEntity openTicket(Guild guild, Member creator, Long typeId) {
        TicketConfig config = configService.getOrCreate(guild.getId());
        if (!config.isEnabled()) throw new IllegalStateException("The ticket system is disabled for this server.");

        // Prevent duplicate open tickets per member
        ticketRepository.findByCreatorIdAndGuildIdAndStatus(creator.getId(), guild.getId(), TicketStatus.OPEN)
                .ifPresent(existing -> {
                    throw new IllegalStateException("You already have an open ticket — <#" + existing.getChannelId() + ">.");
                });

        TicketType type = typeRepository.findById(typeId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket type not found."));

        int number = ticketRepository.getNextTicketNumber(guild.getId());

        TicketEntity ticket = ticketRepository.save(TicketEntity.builder()
                .guildId(guild.getId())
                .creatorId(creator.getId())
                .creatorTag(creator.getUser().getAsTag())
                .typeId(type.getId())
                .typeName(type.getLabel())
                .status(TicketStatus.OPEN)
                .ticketNumber(number)
                .build());

        final TicketConfig finalConfig = config;
        final TicketType   finalType   = type;
        Thread.ofVirtual().start(() ->
                createChannel(guild, creator, ticket, finalType, finalConfig));

        log.info("[Tickets] Ticket #{} opened by {} in guild {}", number, creator.getUser().getAsTag(), guild.getId());
        return ticket;
    }

    /**
     * Opens a system ticket (bug report) with no bound Discord member.
     *
     * The reporter can see the channel but is identified only in the embed
     * content, not as a required permission target. This lets the ticket
     * exist even if the reporter later leaves the server.
     */
    @Transactional
    public TicketEntity openBugReport(Guild guild, Member reporter,
                                      String feature, String description) {
        TicketConfig config = configService.getOrCreate(guild.getId());
        if (!config.isEnabled()) throw new IllegalStateException("The ticket system is disabled for this server.");

        int number = ticketRepository.getNextTicketNumber(guild.getId());
        String subject = "Bug: " + (feature.length() > 100 ? feature.substring(0, 100) : feature);

        TicketEntity ticket = ticketRepository.save(TicketEntity.builder()
                .guildId(guild.getId())
                .creatorId(reporter.getId())
                .creatorTag(reporter.getUser().getAsTag())
                .typeName("Bug Report")
                .status(TicketStatus.OPEN)
                .ticketNumber(number)
                .subject(subject)
                .build());

        Thread.ofVirtual().start(() ->
                createBugChannel(guild, reporter, ticket, feature, description, config));

        log.info("[Tickets] Bug report #{} filed by {} in guild {}", number, reporter.getUser().getAsTag(), guild.getId());
        return ticket;
    }

    // ── Close ─────────────────────────────────────────────────────────────────

    /**
     * Marks a ticket as closed in the DB and either moves the channel to the closed
     * category or deletes it, depending on guild configuration.
     */
    @Transactional
    public void closeTicket(TicketEntity ticket, Member closedBy, Guild guild) {
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setClosedAt(Instant.now());
        ticket.setClosedById(closedBy.getId());
        ticket.setClosedByTag(closedBy.getUser().getAsTag());
        ticketRepository.save(ticket);

        Thread.ofVirtual().start(() -> archiveChannel(guild, ticket, closedBy));
        log.info("[Tickets] Ticket #{} closed by {}", ticket.getTicketNumber(), closedBy.getUser().getAsTag());
    }

    // ── Claim ─────────────────────────────────────────────────────────────────

    @Transactional
    public void claimTicket(TicketEntity ticket, Member staff) {
        ticket.setClaimedById(staff.getId());
        ticket.setClaimedByTag(staff.getUser().getAsTag());
        ticketRepository.save(ticket);
        log.info("[Tickets] Ticket #{} claimed by {}", ticket.getTicketNumber(), staff.getUser().getAsTag());
    }

    // ── Panel ─────────────────────────────────────────────────────────────────

    /**
     * Sends the ticket-panel embed to the specified channel.
     *
     * The panel shows one button per enabled ticket type. If a previous panel
     * message exists in the config, it is deleted before sending the new one.
     * The new message ID is stored in the config so future refreshes can clean up.
     */
    public void sendPanel(Guild guild, TextChannel channel, TicketConfig config,
                          List<TicketType> types) {
        // Delete old panel if present
        if (config.getPanelMessageId() != null) {
            try {
                channel.deleteMessageById(config.getPanelMessageId()).complete();
            } catch (Exception ignored) {
                // Message may already be deleted — not fatal
            }
        }

        Container container = buildPanelContainer(guild, types);

        channel.sendMessageComponents(container)
                .useComponentsV2()
                .queue(msg -> {
                    configService.setPanelChannel(guild.getId(), channel.getId(), msg.getId());
                    log.info("[Tickets] Panel sent to #{} in guild {}", channel.getName(), guild.getId());
                });
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    public Optional<TicketEntity> getByChannelId(String channelId) {
        return ticketRepository.findByChannelId(channelId);
    }

    public Optional<TicketEntity> getById(Long id) {
        return ticketRepository.findById(id);
    }

    // ── Private: Discord channel creation ────────────────────────────────────

    private void createChannel(Guild guild, Member creator, TicketEntity ticket,
                               TicketType type, TicketConfig config) {
        try {
            Category category = resolveOrCreateOpenCategory(guild, config);

            String channelName = "ticket-" + ticket.getTicketNumber();
            TextChannel channel = category.createTextChannel(channelName).complete();

            // Deny @everyone, grant creator and staff
            channel.upsertPermissionOverride(guild.getPublicRole())
                    .deny(Permission.VIEW_CHANNEL)
                    .complete();

            channel.upsertPermissionOverride(creator)
                    .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY,
                            Permission.MESSAGE_ATTACH_FILES)
                    .complete();

            applyStaffPermissions(guild, channel, config);

            // Back-fill channel ID
            ticket.setChannelId(channel.getId());
            ticketRepository.save(ticket);

            sendWelcomeEmbed(channel, creator, ticket, type, config);

        } catch (Exception e) {
            log.error("[Tickets] Failed to create channel for ticket #{}", ticket.getTicketNumber(), e);
        }
    }

    private void createBugChannel(Guild guild, Member reporter, TicketEntity ticket,
                                  String feature, String description, TicketConfig config) {
        try {
            Category category = resolveOrCreateOpenCategory(guild, config);

            String channelName = "bug-" + ticket.getTicketNumber();
            TextChannel channel = category.createTextChannel(channelName).complete();

            channel.upsertPermissionOverride(guild.getPublicRole())
                    .deny(Permission.VIEW_CHANNEL)
                    .complete();

            // Reporter can see the channel and upload files
            channel.upsertPermissionOverride(reporter)
                    .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY,
                            Permission.MESSAGE_ATTACH_FILES)
                    .complete();

            applyStaffPermissions(guild, channel, config);

            ticket.setChannelId(channel.getId());
            ticketRepository.save(ticket);

            sendBugReportEmbed(channel, reporter, ticket, feature, description);

        } catch (Exception e) {
            log.error("[Tickets] Failed to create bug channel for ticket #{}", ticket.getTicketNumber(), e);
        }
    }

    private void archiveChannel(Guild guild, TicketEntity ticket, Member closedBy) {
        try {
            if (ticket.getChannelId() == null) return;
            TextChannel channel = guild.getTextChannelById(ticket.getChannelId());
            if (channel == null) return;

            TicketConfig config = configService.getOrCreate(guild.getId());

            if (config.getCategoryClosedId() != null) {
                Category closedCat = guild.getCategoryById(config.getCategoryClosedId());
                if (closedCat == null) {
                    closedCat = guild.createCategory(CATEGORY_CLOSED_NAME).complete();
                    configService.setCategories(guild.getId(),
                            config.getCategoryOpenId(), closedCat.getId());
                }

                // Move to closed category and strip creator permissions
                channel.getManager().setParent(closedCat).complete();

                if (ticket.getCreatorId() != null) {
                    Member creator = guild.retrieveMemberById(ticket.getCreatorId()).complete();
                    if (creator != null) {
                        channel.upsertPermissionOverride(creator)
                                .deny(Permission.VIEW_CHANNEL)
                                .complete();
                    }
                }

                sendCloseEmbed(channel, closedBy, ticket);

            } else {
                // No closed category configured — delete after a short delay
                sendCloseEmbed(channel, closedBy, ticket);
                channel.delete().reason("Ticket #" + ticket.getTicketNumber() + " closed").queueAfter(
                        5, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error("[Tickets] Failed to archive channel for ticket #{}", ticket.getTicketNumber(), e);
        }
    }

    // ── Private: embeds ───────────────────────────────────────────────────────

    private Container buildPanelContainer(Guild guild, List<TicketType> types) {
        // One button per type — emoji + label
        List<Button> buttons = types.stream()
                .map(t -> Button.primary("ticket:open:" + t.getId(), t.getLabel()).withEmoji(Emoji.fromUnicode(t.getEmoji())))
                .toList();

        // Type catalogue shown in the body
        StringBuilder typeList = new StringBuilder();
        for (TicketType t : types) {
            typeList.append(t.getEmoji()).append(" **").append(t.getLabel())
                    .append("**\n-# ").append(t.getDescription()).append("\n\n");
        }

        return Container.of(
                Section.of(
                        Thumbnail.fromUrl(LOGO_URL),
                        TextDisplay.of(
                                "# 🎫 Support — " + guild.getName() + "\n" +
                                        "**Dragon Team**\n" +
                                        "-# SUPPORT DESK"
                        )
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# **HOW TO GET HELP**"),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("Select the type of help you need and click the button below. "
                        + "A private channel will be created for you where staff can assist."),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# **TICKET TYPES**"),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(typeList.toString().trim()),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(buttons),

                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("-# Please do not open multiple tickets for the same issue.")
        ).withAccentColor(COLOR_OPEN);
    }

    private void sendWelcomeEmbed(TextChannel channel, Member creator,
                                  TicketEntity ticket, TicketType type, TicketConfig config) {
        Container container = Container.of(
                Section.of(
                        Thumbnail.fromUrl(LOGO_URL),
                        TextDisplay.of(
                                "# " + type.getEmoji() + " " + type.getLabel() + "\n" +
                                        "**Ticket #" + ticket.getTicketNumber() + "**\n" +
                                        "-# SUPPORT DESK"
                        )
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# **HEY " + creator.getUser().getName().toUpperCase() + "** 👋"),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("Welcome to your support ticket! A staff member will be with you shortly.\n"
                        + "Please describe your issue in detail — the more context, the faster we can help."),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("📋 **Type**\n-# " + type.getLabel()),
                TextDisplay.of("🆔 **Ticket**\n-# #" + ticket.getTicketNumber()),
                TextDisplay.of("👤 **Opened by**\n-# " + creator.getUser().getAsTag()),
                TextDisplay.of("🕐 **Opened at**\n-# " + "<t:" + Instant.now().getEpochSecond() + ":F>"),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.danger("ticket:close:" + ticket.getId(), "🔒 Close Ticket"),
                        Button.secondary("ticket:claim:" + ticket.getId(), "👋 Claim")
                )
        ).withAccentColor(COLOR_OPEN);

        channel.sendMessageComponents(container)
                .useComponentsV2()
                .queue();
    }

    private void sendBugReportEmbed(TextChannel channel, Member reporter,
                                    TicketEntity ticket, String feature, String description) {
        Container container = Container.of(
                Section.of(
                        Thumbnail.fromUrl(LOGO_URL),
                        TextDisplay.of(
                                "# 🐛 Bug Report\n" +
                                        "**Ticket #" + ticket.getTicketNumber() + "**\n" +
                                        "-# BUG TRACKER"
                        )
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# **REPORT DETAILS**"),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("📦 **Feature / Component**\n" + feature),
                TextDisplay.of("📋 **Description & Steps to Reproduce**\n" + description),
                TextDisplay.of("👤 **Reported by**\n-# " + reporter.getUser().getAsTag()),
                TextDisplay.of("🕐 **Submitted at**\n-# <t:" + Instant.now().getEpochSecond() + ":F>"),

                Separator.createDivider(Separator.Spacing.LARGE),

                Section.of(
                        net.dv8tion.jda.api.components.thumbnail.Thumbnail.fromUrl(LOGO_URL),
                        TextDisplay.of("-# **📎 ATTACH FILES**"),
                        TextDisplay.of("Please upload any screenshots, error logs, or recordings "
                                + "that help reproduce the issue **directly in this channel**."),
                        TextDisplay.of("-# Supported: PNG, JPG, GIF, MP4, TXT, LOG — max 25 MB per file")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.danger("ticket:close:" + ticket.getId(), "🔒 Close"),
                        Button.secondary("ticket:claim:" + ticket.getId(), "👋 Claim")
                )
        ).withAccentColor(COLOR_BUG);

        channel.sendMessageComponents(container)
                .useComponentsV2()
                .queue();
    }

    private void sendCloseEmbed(TextChannel channel, Member closedBy, TicketEntity ticket) {
        Container container = Container.of(
                Section.of(
                        Thumbnail.fromUrl(LOGO_URL),
                        TextDisplay.of(
                                "# 🔒 Ticket Closed\n" +
                                        "**Ticket #" + ticket.getTicketNumber() + "**\n" +
                                        "-# SUPPORT DESK"
                        )
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("👮 **Closed by**\n-# " + closedBy.getUser().getAsTag()),
                TextDisplay.of("🕐 **Closed at**\n-# <t:" + Instant.now().getEpochSecond() + ":F>"),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# This ticket is now closed. If you need further help, open a new ticket.")
        ).withAccentColor(COLOR_CLOSE);

        channel.sendMessageComponents(container)
                .useComponentsV2()
                .queue();
    }

    // ── Private: helpers ──────────────────────────────────────────────────────

    /**
     * Returns the open-ticket category, auto-creating it if the config has no ID yet.
     * Always updates the config with the real category ID so subsequent tickets reuse it.
     */
    private Category resolveOrCreateOpenCategory(Guild guild, TicketConfig config) {
        if (config.getCategoryOpenId() != null) {
            Category existing = guild.getCategoryById(config.getCategoryOpenId());
            if (existing != null) return existing;
        }

        Category created = guild.createCategory(CATEGORY_OPEN_NAME).complete();
        configService.setCategories(guild.getId(), created.getId(), config.getCategoryClosedId());
        log.info("[Tickets] Auto-created open category '{}' in guild {}", CATEGORY_OPEN_NAME, guild.getId());
        return created;
    }

    private void applyStaffPermissions(Guild guild, TextChannel channel, TicketConfig config) {
        if (config.getStaffRoleId() == null) return;
        Role staffRole = guild.getRoleById(config.getStaffRoleId());
        if (staffRole == null) return;

        channel.upsertPermissionOverride(staffRole)
                .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY,
                        Permission.MESSAGE_ATTACH_FILES, Permission.MANAGE_CHANNEL,
                        Permission.MESSAGE_MANAGE)
                .complete();
    }
}
