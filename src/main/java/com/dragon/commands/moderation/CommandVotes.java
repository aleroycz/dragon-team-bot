// ─────────────────────────────────────────────────────────────────────────────
// CommandVotes.java
// ─────────────────────────────────────────────────────────────────────────────
package com.dragon.commands.moderation;

import com.dragon.entity.event.Event;
import com.dragon.entity.event.vote.VotingSession;
import com.dragon.entity.event.vote.VotingSessionVote;
import com.dragon.integration.SlashCommand;
import com.dragon.repository.event.VotingSessionRepository;
import com.dragon.repository.event.VotingSessionVoteRepository;
import com.dragon.dto.vote.VotingSessionStatus;
import com.dragon.repository.event.EventRepository;
import com.dragon.service.VotingSessionService;
import com.dragon.utils.Embed;
import com.dragon.utils.IconRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommandVotes implements SlashCommand {

    private final VotingSessionService        votingSessionService;
    private final VotingSessionRepository     votingSessionRepository;
    private final VotingSessionVoteRepository votingSessionVoteRepository;
    private final EventRepository             eventRepository;
    private final Embed                       embed;

    @Override
    public String getName() { return "votes"; }

    @Override
    public String getDescription() { return "View or remove votes from the current voting session."; }

    @Override
    public void configure(SlashCommandData data) {
        data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .addSubcommands(
                        new SubcommandData(
                                "list", "View all votes for the current session."
                        ),
                        new SubcommandData(
                                "remove", "Remove a specific member's vote from the current session."
                        ).addOption(OptionType.USER, "member", "The member whose vote to remove.", true)
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.ADMINISTRATOR)) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Permission Denied",
                    "You do not have permission to manage votes.").build()
            ).setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        log.info("[Vote/Dispatcher] {}", subcommand);

        switch (subcommand) {
            case "list"   -> handleList(event);
            case "remove" -> handleRemove(event);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // List
    // ─────────────────────────────────────────────────────────────────────────

    private void handleList(SlashCommandInteractionEvent event) {
        log.info("[Votes/List] handleList invoked by {}", event.getUser().getAsTag());

        log.info("[Votes/List] Looking for open voting session...");
        VotingSession session = votingSessionRepository.findByStatusWithVotes(VotingSessionStatus.OPEN)
                .orElse(null);

        if (session == null) {
            log.warn("[Votes/List] No open voting session found.");
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "No Active Session",
                    "There is no open voting session at the moment.").build()
            ).setEphemeral(true).queue();
            return;
        }

        log.info("[Votes/List] Found session id={} week={}/{}", session.getId(), session.getIsoWeek(), session.getIsoYear());

        List<VotingSessionVote> votes = session.getVotes();
        log.info("[Votes/List] Vote count: {}", votes.size());

        if (votes.isEmpty()) {
            log.info("[Votes/List] No votes cast yet — returning empty state.");
            event.replyEmbeds(embed.info(IconRegistry.ICON_BOOK, "🗳️ Current Votes",
                    "No votes have been cast yet for this session.").build()
            ).setEphemeral(true).queue();
            return;
        }

        log.info("[Votes/List] Building tally from {} votes...", votes.size());
        Map<Long, Long> tally = votes.stream()
                .flatMap(v -> v.getChosenEventIds().stream())
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));
        log.info("[Votes/List] Tally built: {}", tally);

        log.info("[Votes/List] Fetching event names for IDs: {}", session.getEventIds());
        Map<Long, String> eventNames = eventRepository.findAllById(session.getEventIds())
                .stream()
                .collect(Collectors.toMap(Event::getId, Event::getEventName));
        log.info("[Votes/List] Event names resolved: {}", eventNames);

        var builder = embed.info(
                IconRegistry.ICON_BOOK,
                "🗳️ Votes — Week " + session.getIsoWeek() + " / " + session.getIsoYear(),
                "**" + votes.size() + "** member(s) have voted so far.\n\u200B"
        );

        for (Map.Entry<Long, Long> entry : tally.entrySet()) {
            String name = eventNames.getOrDefault(entry.getKey(), "Unknown Event");
            log.debug("[Votes/List] Adding tally field — event: {}, votes: {}", name, entry.getValue());
            builder.addField("📅 " + name, entry.getValue() + " vote(s)", true);
        }

        builder.addField("\u200B", "\u200B", false);
        builder.addField("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "\u200B", false);

        log.info("[Votes/List] Processing {} individual voter entries...", votes.size());
        for (VotingSessionVote vote : votes) {
            log.info("[Votes/List] Processing vote for member: {}", vote.getMemberId());
            try {
                log.info("[Votes/List] Calling getMemberVotedEvents for session={} member={}",
                        session.getId(), vote.getMemberId());

                List<Event> memberVotedEvents = votingSessionService
                        .getMemberVotedEvents(session.getId(), vote.getMemberId());

                log.info("[Votes/List] getMemberVotedEvents returned {} event(s) for member {}",
                        memberVotedEvents.size(), vote.getMemberId());

                String chosen = memberVotedEvents.stream()
                        .map(Event::getEventName)
                        .collect(Collectors.joining(", "));

                log.info("[Votes/List] Member {} voted for: {}", vote.getMemberId(), chosen);

                builder.addField(
                        "<@" + vote.getMemberId() + ">",
                        "Voted for: **" + chosen + "**",
                        false
                );

            } catch (IllegalStateException e) {
                log.warn("[Votes/List] Could not retrieve voted events for member {}: {}",
                        vote.getMemberId(), e.getMessage());
            } catch (Exception e) {
                log.error("[Votes/List] Unexpected error processing member {}: {}",
                        vote.getMemberId(), e.getMessage(), e);
            }
        }

        log.info("[Votes/List] All voters processed — setting timestamp and sending embed...");
        builder.setTimestamp(Instant.now());

        log.info("[Votes/List] Sending embed via hook...");
        event.replyEmbeds(builder.build()).setEphemeral(true).queue(
                success -> log.info("[Votes/List] Embed sent successfully."),
                error   -> log.error("[Votes/List] Failed to send embed: {}", error.getMessage(), error)
        );

        log.info("[Votes/List] handleList completed.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Remove
    // ─────────────────────────────────────────────────────────────────────────

    private void handleRemove(SlashCommandInteractionEvent event) {
        VotingSession session = votingSessionRepository.findByStatus(VotingSessionStatus.OPEN)
                .orElse(null);

        if (session == null) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "No Active Session",
                    "There is no open voting session to remove votes from.").build()
            ).setEphemeral(true).queue();
            return;
        }

        net.dv8tion.jda.api.entities.Member target =
                Objects.requireNonNull(event.getOption("member")).getAsMember();

        if (target == null) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Member Not Found",
                    "Could not resolve the target member.").build()
            ).setEphemeral(true).queue();
            return;
        }

        VotingSessionVote vote = votingSessionVoteRepository
                .findBySessionIdAndMemberId(session.getId(), target.getId())
                .orElse(null);

        if (vote == null) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "No Vote Found",
                    target.getAsMention() + " has not cast a vote in the current session.").build()
            ).setEphemeral(true).queue();
            return;
        }

        Map<Long, String> eventNames = eventRepository.findAllById(vote.getChosenEventIds())
                .stream()
                .collect(Collectors.toMap(Event::getId, Event::getEventName));

        String removedVotes = vote.getChosenEventIds().stream()
                .map(id -> eventNames.getOrDefault(id, "Unknown"))
                .collect(Collectors.joining(", "));

        votingSessionVoteRepository.delete(vote);
        session.getVotes().remove(vote);
        votingSessionRepository.save(session);

        votingSessionService.refreshVoteMessage(session);

        log.info("[Votes] Vote removed for member {} in session id={} by {}",
                target.getId(), session.getId(), Objects.requireNonNull(event.getMember()).getUser().getAsTag());

        event.replyEmbeds(
                embed.warn(
                                IconRegistry.ICON_ALERT,
                                "🗑️ Vote Removed",
                                """
                                The vote cast by %s has been successfully removed from the current session.
                                
                                **Removed votes:** %s
                                
                                *The member may vote again if they wish.*
                                """.formatted(target.getAsMention(), removedVotes)
                        )
                        .addField("Session",    "Week " + session.getIsoWeek() + " / " + session.getIsoYear(), true)
                        .addField("Removed By", event.getMember().getAsMention(),                               true)
                        .setTimestamp(Instant.now())
                        .build()
        ).setEphemeral(true).queue();
    }
}