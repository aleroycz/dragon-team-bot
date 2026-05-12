package com.dragon.integration.listeners;

import com.dragon.entity.TicketEntity;
import com.dragon.integration.DiscordEventListener;
import com.dragon.module.ModuleName;
import com.dragon.service.ModuleService;
import com.dragon.service.ticket.TicketService;
import com.dragon.utils.Embed;
import com.dragon.utils.IconRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Handles all button interactions originating from the ticket system.
 *
 * <h3>Button ID format</h3>
 * <pre>
 *   ticket:open:&lt;typeId&gt;   — open a ticket of the given type
 *   ticket:close:&lt;ticketId&gt; — close the current ticket
 *   ticket:claim:&lt;ticketId&gt; — staff claims the ticket
 * </pre>
 *
 * All interactions are acknowledged immediately with {@code deferReply} and resolved
 * asynchronously via virtual threads to avoid blocking the JDA event thread during
 * Discord REST calls (channel creation, permission overwrites, etc.).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketInteractionListener extends ListenerAdapter implements DiscordEventListener {

    private static final String PREFIX_OPEN  = "ticket:open:";
    private static final String PREFIX_CLOSE = "ticket:close:";
    private static final String PREFIX_CLAIM = "ticket:claim:";

    private final TicketService ticketService;
    private final ModuleService moduleService;
    private final Embed embed;

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("ticket:")) return;

        Guild  guild  = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) return;

        if (!moduleService.isEnabled(ModuleName.TICKETS, guild.getId())) {
            event.reply("❌ The ticket system is not enabled on this server.")
                    .setEphemeral(true).queue();
            return;
        }

        if (moduleService.isInMaintenance(ModuleName.TICKETS)) {
            event.reply("🔧 The ticket system is currently under maintenance. Please try again later.")
                    .setEphemeral(true).queue();
            return;
        }

        if (id.startsWith(PREFIX_OPEN)) {
            handleOpen(event, guild, member, id.substring(PREFIX_OPEN.length()));
        } else if (id.startsWith(PREFIX_CLOSE)) {
            handleClose(event, guild, member, id.substring(PREFIX_CLOSE.length()));
        } else if (id.startsWith(PREFIX_CLAIM)) {
            handleClaim(event, guild, member, id.substring(PREFIX_CLAIM.length()));
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handleOpen(ButtonInteractionEvent event, Guild guild, Member member, String rawTypeId) {
        long typeId;
        try {
            typeId = Long.parseLong(rawTypeId);
        } catch (NumberFormatException e) {
            log.warn("[Tickets] Malformed type ID in button: {}", rawTypeId);
            event.reply("⚠️ Invalid ticket type. Please contact a staff member.")
                    .setEphemeral(true).queue();
            return;
        }

        // Defer — channel creation may take a second
        event.deferReply(true).queue();

        final long finalTypeId = typeId;
        Thread.ofVirtual().start(() -> {
            try {
                TicketEntity ticket = ticketService.openTicket(guild, member, finalTypeId);

                // Poll until the async channel creation fills in the channel ID (max 5 s)
                for (int i = 0; i < 10 && ticket.getChannelId() == null; i++) {
                    Thread.sleep(500);
                    ticket = ticketService.getById(ticket.getId()).orElse(ticket);
                }

                String channelRef = ticket.getChannelId() != null
                        ? "<#" + ticket.getChannelId() + ">"
                        : "being prepared — check your channel list in a moment";

                event.getHook().editOriginalEmbeds(
                        embed.info(IconRegistry.ICON_CHECKS, "🎫 Ticket Opened",
                                        "Your ticket has been created: " + channelRef)
                                .build()
                ).queue();

            } catch (IllegalStateException e) {
                event.getHook().editOriginalEmbeds(
                        embed.error(IconRegistry.ICON_ALERT, "Cannot Open Ticket", e.getMessage()).build()
                ).queue();
            } catch (Exception e) {
                log.error("[Tickets] Error opening ticket for {}", member.getUser().getAsTag(), e);
                event.getHook().editOriginalEmbeds(
                        embed.error(IconRegistry.ICON_ALERT, "Error",
                                "Something went wrong while opening your ticket. Please try again.").build()
                ).queue();
            }
        });
    }

    private void handleClose(ButtonInteractionEvent event, Guild guild, Member member, String rawId) {
        long ticketId;
        try {
            ticketId = Long.parseLong(rawId);
        } catch (NumberFormatException e) {
            log.warn("[Tickets] Malformed ticket ID in close button: {}", rawId);
            event.reply("⚠️ Invalid ticket reference.").setEphemeral(true).queue();
            return;
        }

        // Only the creator or staff may close a ticket
        TicketEntity ticket = ticketService.getByChannelId(event.getChannel().getId()).orElse(null);
        if (ticket == null) {
            event.reply("⚠️ This channel is not a recognised ticket.").setEphemeral(true).queue();
            return;
        }

        boolean isCreator = member.getId().equals(ticket.getCreatorId());
        boolean isStaff   = member.hasPermission(Permission.MODERATE_MEMBERS);

        if (!isCreator && !isStaff) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Not Allowed",
                    "Only the ticket creator or a staff member can close this ticket.").build()
            ).setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        final TicketEntity finalTicket = ticket;
        Thread.ofVirtual().start(() -> {
            try {
                ticketService.closeTicket(finalTicket, member, guild);
                event.getHook().editOriginalEmbeds(
                        embed.info(IconRegistry.ICON_CHECKS, "🔒 Ticket Closed",
                                "Ticket **#" + finalTicket.getTicketNumber() + "** has been closed.").build()
                ).queue();
            } catch (Exception e) {
                log.error("[Tickets] Error closing ticket #{}", finalTicket.getTicketNumber(), e);
                event.getHook().editOriginalEmbeds(
                        embed.error(IconRegistry.ICON_ALERT, "Error",
                                "Failed to close the ticket. Please try again.").build()
                ).queue();
            }
        });
    }

    private void handleClaim(ButtonInteractionEvent event, Guild guild, Member member, String rawId) {
        if (!member.hasPermission(Permission.MODERATE_MEMBERS)) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Not Allowed",
                    "Only staff members can claim tickets.").build()
            ).setEphemeral(true).queue();
            return;
        }

        TicketEntity ticket = ticketService.getByChannelId(event.getChannel().getId()).orElse(null);
        if (ticket == null) {
            event.reply("⚠️ This channel is not a recognised ticket.").setEphemeral(true).queue();
            return;
        }

        if (ticket.getClaimedById() != null) {
            event.reply("👋 This ticket was already claimed by <@" + ticket.getClaimedById() + ">.")
                    .setEphemeral(true).queue();
            return;
        }

        ticketService.claimTicket(ticket, member);

        event.replyEmbeds(
                embed.info(IconRegistry.ICON_CHECKS, "👋 Ticket Claimed",
                                "**" + member.getUser().getAsTag() + "** has claimed ticket **#"
                                        + ticket.getTicketNumber() + "**.")
                        .build()
        ).queue(); // Visible to everyone in the channel — not ephemeral
    }
}
