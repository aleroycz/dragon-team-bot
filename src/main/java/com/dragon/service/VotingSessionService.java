package com.dragon.service;

import com.dragon.dto.event.EventStatus;
import com.dragon.dto.vote.VotingSessionStatus;
import com.dragon.entity.event.Event;
import com.dragon.entity.event.vote.VotingSession;
import com.dragon.entity.event.vote.VotingSessionVote;
import com.dragon.repository.event.EventRepository;
import com.dragon.repository.event.VotingSessionRepository;
import com.dragon.repository.event.VotingSessionVoteRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VotingSessionService {

    @Value("${discord.player.role.id:YOUR_PLAYER_ROLE_ID}")
    private String playerRoleId;

    private static final long LEADER_ID  = 382918201241108481L;
    private static final long CAPTAIN_ID = 481473412439736320L;

    private static final int    COLOR_OPEN   = 0x5865F2;
    private static final int    COLOR_CLOSED = 0x99AAB5;
    private static final String ICON_VOTE    = "https://cdn.discordapp.com/attachments/1501768878051692624/1502048750523388025/1778186732585.png?ex=69fe4b68&is=69fcf9e8&hm=a855ae680d3e51210bfdffcedaf3788927a2607357ef5aeaec205330673afbc8&";

    @Value("${discord.general.channel.id}")
    private String generalChannelId;

    @Value("${discord.premier.voice.channel.id}")
    private String premierVoiceChannelId;

    private final JDA                         jda;
    private final VotingSessionRepository     votingSessionRepository;
    private final VotingSessionVoteRepository votingSessionVoteRepository;
    private final EventRepository             eventRepository;

    @Transactional
    public void openSessionForWeekIfAbsent(List<Event> weekEvents) {
        if (weekEvents.size() < 4) {
            log.warn("Cannot open voting session — fewer than 4 Premier events found for the week (found={}).",
                    weekEvents.size());
            return;
        }

        List<Event> sorted = weekEvents.stream()
                .sorted(Comparator.comparing(Event::getStartTime))
                .limit(4)
                .toList();

        LocalDateTime anchor = sorted.getFirst().getStartTime();
        int isoYear = anchor.get(IsoFields.WEEK_BASED_YEAR);
        int isoWeek = anchor.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        if (votingSessionRepository.findByIsoYearAndIsoWeek(isoYear, isoWeek).isPresent()) {
            log.debug("Voting session for {}-W{} already exists — skipping.", isoYear, isoWeek);
            return;
        }

        Instant closesAt = sorted.getFirst().getStartTime()
                .minusHours(24)
                .toInstant(ZoneOffset.UTC);

        VotingSession session = VotingSession.builder()
                .isoYear(isoYear)
                .isoWeek(isoWeek)
                .eventIds(sorted.stream().map(Event::getId).collect(Collectors.toList()))
                .status(VotingSessionStatus.OPEN)
                .closesAt(closesAt)
                .build();

        votingSessionRepository.save(session);

        postVoteMessage(session, sorted);

        log.info("Opened voting session id={} for {}-W{} with {} events.",
                session.getId(), isoYear, isoWeek, sorted.size());
    }

    @Transactional
    public VoteResult castVote(Long sessionId, Member member, List<Long> chosenEventIds) {
        Role role = this.jda.getRoleById(playerRoleId);
        if (!member.getRoles().contains(role)) {
            return VoteResult.DENIED;
        }

        VotingSession session = votingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (session.getStatus() != VotingSessionStatus.OPEN) {
            return VoteResult.SESSION_CLOSED;
        }

        if (chosenEventIds.size() != 2) {
            return VoteResult.INVALID;
        }

        if (chosenEventIds.get(0).equals(chosenEventIds.get(1))) {
            return VoteResult.INVALID;
        }

        boolean allValid = new HashSet<>(session.getEventIds()).containsAll(chosenEventIds);
        if (!allValid) {
            return VoteResult.INVALID;
        }

        boolean alreadyVoted = votingSessionVoteRepository
                .findBySessionIdAndMemberId(sessionId, member.getId())
                .isPresent();

        if (alreadyVoted) {
            return VoteResult.ALREADY_VOTED;
        }

        VotingSessionVote vote = VotingSessionVote.builder()
                .session(session)
                .memberId(member.getId())
                .chosenEventIds(new ArrayList<>(chosenEventIds))
                .build();

        votingSessionVoteRepository.save(vote);

        session.getVotes().add(vote);
        refreshVoteMessage(session);

        return VoteResult.ACCEPTED;
    }

    @Transactional
    public List<Event> getMemberVotedEvents(Long sessionId, String memberId) {
        VotingSession session = votingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        VotingSessionVote vote = votingSessionVoteRepository
                .findBySessionIdAndMemberId(session.getId(), memberId)
                .orElseThrow(() -> new IllegalStateException("Member " + memberId + " has not voted in session " + sessionId));

        return eventRepository.findAllById(vote.getChosenEventIds());
    }

    @Transactional
    public void resolveSession(VotingSession session) {
        if (session.getStatus() != VotingSessionStatus.CLOSED) {
            log.warn("Attempted to resolve session id={} but status is {} — skipping.",
                    session.getId(), session.getStatus());
            return;
        }

        Map<Long, Long> tally = session.getVotes().stream()
                .flatMap(v -> v.getChosenEventIds().stream())
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));

        Set<Long> winnerIds = tally.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        Set<Long> loserIds = session.getEventIds().stream()
                .filter(id -> !winnerIds.contains(id))
                .collect(Collectors.toSet());

        List<Event> winners = eventRepository.findAllById(winnerIds);
        List<Event> losers  = eventRepository.findAllById(loserIds);

        for (Event event : winners) {
            event.setStatus(EventStatus.SCHEDULED);

            var guild = this.jda.getGuildById(event.getGuildId());
            if (guild == null) {
                log.error("[VotingSession] Guild {} not found — cannot create scheduled event for '{}'",
                        event.getGuildId(), event.getEventName());
                continue;
            }
            var voiceChannel = this.jda.getGuildChannelById(premierVoiceChannelId);
            if (voiceChannel == null) {
                log.error("[VotingSession] Premier voice channel {} not found — cannot create scheduled event for '{}'",
                        premierVoiceChannelId, event.getEventName());
                continue;
            }

            guild.createScheduledEvent(
                    event.getEventName(), voiceChannel,
                    event.getStartTime()
                            .toLocalDate()
                            .atTime(18, 0)
                            .atZone(ZoneId.of("Europe/Prague"))
                            .toOffsetDateTime()
            ).reason("Voting succeeded.").queue();

            log.info("Session id={} — promoted event id={} name='{}' to SCHEDULED.",
                    session.getId(), event.getId(), event.getEventName());
        }

        for (Event event : losers) {
            event.setStatus(EventStatus.CANCELLED);
            log.info("Session id={} — cancelled event id={} name='{}'.",
                    session.getId(), event.getId(), event.getEventName());
        }

        eventRepository.saveAll(winners);
        eventRepository.saveAll(losers);

        log.info("Session id={} resolved — winners={}, losers={}",
                session.getId(), winnerIds, loserIds);
    }

    private void openNextWeekSession() {
        LocalDateTime anchor = LocalDateTime.now(ZoneOffset.UTC).plusWeeks(1);

        int nextYear = anchor.get(IsoFields.WEEK_BASED_YEAR);
        int nextWeek = anchor.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        if (votingSessionRepository.findByIsoYearAndIsoWeek(nextYear, nextWeek).isPresent()) {
            log.debug("Next week voting session {}-W{} already exists — skipping.", nextYear, nextWeek);
            return;
        }

        List<Event> nextWeekEvents = eventRepository.findAll().stream()
                .filter(e -> e.getStatus() == EventStatus.STANDBY || e.getStatus() == EventStatus.SCHEDULED)
                .filter(e -> e.getStartTime().get(IsoFields.WEEK_BASED_YEAR) == nextYear
                        && e.getStartTime().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == nextWeek)
                .toList();

        if (nextWeekEvents.size() < 4) {
            log.warn("Next week ({}-W{}) also has fewer than 4 Premier events (found={}) — no vote opened.",
                    nextYear, nextWeek, nextWeekEvents.size());
            return;
        }

        log.info("Opening next week voting session for {}-W{} with {} events.",
                nextYear, nextWeek, nextWeekEvents.size());

        openSessionForWeekIfAbsent(nextWeekEvents);
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void autoCloseSessions() {
        List<VotingSession> expired = votingSessionRepository
                .findByStatusAndClosesAtBefore(VotingSessionStatus.OPEN, Instant.now());

        for (VotingSession session : expired) {
            session.setStatus(VotingSessionStatus.CLOSED);
            votingSessionRepository.save(session);
            refreshVoteMessage(session);
            resolveSession(session);
            lockRosterAndPostSchedule(session);

            log.info("Auto-closed voting session id={} ({}-W{})",
                    session.getId(), session.getIsoYear(), session.getIsoWeek());
        }
    }

    @Transactional
    public void forceCloseCurrentSession() {
        VotingSession session = votingSessionRepository
                .findByStatus(VotingSessionStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("No open voting session found to close."));

        session.setStatus(VotingSessionStatus.CLOSED);
        session.setClosesAt(Instant.now());
        votingSessionRepository.save(session);

        refreshVoteMessage(session);
        resolveSession(session);
        lockRosterAndPostSchedule(session);

        log.info("[VotingSession] Force-closed session id={} ({}-W{}) by manual action.",
                session.getId(), session.getIsoYear(), session.getIsoWeek());
    }

    @Transactional
    public void lockRosterAndPostSchedule(VotingSession session) {
        if (session.getStatus() != VotingSessionStatus.CLOSED) {
            log.warn("[Roster] Cannot lock roster — session id={} is not closed.", session.getId());
            return;
        }

        TextChannel channel = jda.getTextChannelById(generalChannelId);
        if (channel == null) {
            log.error("[Roster] General channel not found — cannot post schedule.");
            return;
        }

        Map<Long, Long> tally = session.getVotes().stream()
                .flatMap(v -> v.getChosenEventIds().stream())
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));

        List<Event> winningEvents = tally.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(2)
                .map(entry -> eventRepository.findById(entry.getKey()).orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Event::getStartTime))
                .toList();

        if (winningEvents.isEmpty()) {
            log.warn("[Roster] No winning events found for session id={}", session.getId());
            return;
        }

        Set<Long> winningEventIds = winningEvents.stream()
                .map(Event::getId)
                .collect(Collectors.toSet());

        Map<Long, List<String>> rosterPerEvent = new HashMap<>();
        for (Event event : winningEvents) {
            rosterPerEvent.put(event.getId(), new ArrayList<>());
        }

        for (VotingSessionVote vote : session.getVotes()) {
            for (Long chosenId : vote.getChosenEventIds()) {
                if (winningEventIds.contains(chosenId)) {
                    rosterPerEvent.get(chosenId).add(vote.getMemberId());
                }
            }
        }

        for (Event event : winningEvents) {
            List<String> roster = rosterPerEvent.getOrDefault(event.getId(), new ArrayList<>());
            postRosterMessage(channel, session, event, roster);
        }

        log.info("[Roster] Roster locked and schedule posted for session id={} — {} winning event(s).",
                session.getId(), winningEvents.size());
    }

    private void postRosterMessage(TextChannel channel, VotingSession session, Event event, List<String> voterIds) {
        StringBuilder rosterLines = new StringBuilder();
        StringBuilder benchLines  = new StringBuilder();

        rosterLines.append("👑 <@").append(LEADER_ID).append("> — **Leader** *(Always present)*\n");
        rosterLines.append("🎖️ <@").append(CAPTAIN_ID).append("> — **Captain** *(Always present)*\n");

        int remainingSlots = 3;
        int slot = 1;

        for (String memberId : voterIds) {
            long id = Long.parseLong(memberId);
            if (id == LEADER_ID || id == CAPTAIN_ID) continue;

            if (remainingSlots > 0) {
                rosterLines.append("🎮 <@").append(memberId).append("> — Player ").append(slot++).append("\n");
                remainingSlots--;
            } else {
                benchLines.append("🪑 <@").append(memberId).append(">\n");
            }
        }

        while (remainingSlots > 0) {
            rosterLines.append("🎮 ❓ — Player ").append(slot++).append(" — *Missing player to be assigned*\n");
            remainingSlots--;
        }

        String rosterText = rosterLines.isEmpty()
                ? "*No additional players voted for this match.*"
                : rosterLines.toString();

        String benchText = benchLines.isEmpty()
                ? "*No players on the bench.*"
                : benchLines.toString();

        Container container = Container.of(
                Section.of(
                        Thumbnail.fromUrl(ICON_VOTE),
                        TextDisplay.of(
                                "# 📋  Match Roster — Week " + session.getIsoWeek() + "\n" +
                                        "**" + event.getEventName() + "**\n" +
                                        "-# 🗓️ " + formatTime(event.getStartTime())
                        )
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("""
                    The votes are in and the roster for this match has been locked. \
                    Here's who's playing — make sure you're ready on time! 🔥
                    """),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### 🏟️ Confirmed Lineup *(%d / 5)*".formatted(voterIds.size())),
                TextDisplay.of(rosterText),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### 🪑 Bench"),
                TextDisplay.of(benchText),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of(
                        "-# ⏰ Match starts: <t:" +
                                event.getStartTime().toInstant(ZoneOffset.UTC).getEpochSecond() + ":F> " +
                                "(<t:" + event.getStartTime().toInstant(ZoneOffset.UTC).getEpochSecond() + ":R>)\n" +
                                "-# 📍 Join the **Premier** voice channel before the match starts.\n" +
                                "-# If you can't make it, contact the captain as soon as possible."
                )
        ).withAccentColor(COLOR_OPEN);

        channel.sendMessage(
                new MessageCreateBuilder()
                        .useComponentsV2()
                        .addComponents(container)
                        .build()
        ).queue(
                msg -> log.info("[Roster] Schedule posted for event id={} name='{}'",
                        event.getId(), event.getEventName()),
                err -> log.error("[Roster] Failed to post schedule for event id={}: {}",
                        event.getId(), err.getMessage())
        );
    }

    private void postVoteMessage(VotingSession session, List<Event> events) {
        TextChannel channel = jda.getTextChannelById(generalChannelId);
        if (channel == null) {
            log.error("General channel ({}) not found — cannot post vote message.", generalChannelId);
            return;
        }

        Container container = buildVoteContainer(session, events, VotingSessionStatus.OPEN);

        MessageCreateBuilder message = new MessageCreateBuilder()
                .useComponentsV2()
                .addComponents(container);

        channel.sendMessage(message.build()).queue(
                msg -> {
                    session.setDiscordMessageId(msg.getIdLong());
                    votingSessionRepository.save(session);
                    log.debug("Vote message posted (messageId={})", msg.getId());
                },
                err -> log.error("Failed to post vote message: {}", err.getMessage())
        );
    }

    public void refreshVoteMessage(VotingSession session) {
        if (session.getDiscordMessageId() == null) return;

        TextChannel channel = jda.getTextChannelById(generalChannelId);
        if (channel == null) return;

        List<Event> events = eventRepository.findAllById(session.getEventIds());
        if (events.size() != session.getEventIds().size()) return;

        events.sort(Comparator.comparing(Event::getStartTime));

        Container container = buildVoteContainer(session, events, session.getStatus());

        MessageEditBuilder edit = new MessageEditBuilder()
                .useComponentsV2()
                .setComponents(container);

        channel.editMessageById(session.getDiscordMessageId(), edit.build()).queue(
                msg -> log.debug("Vote message refreshed (messageId={})", msg.getId()),
                err -> log.error("Failed to refresh vote message: {}", err.getMessage())
        );
    }

    private Container buildVoteContainer(VotingSession session, List<Event> events, VotingSessionStatus status) {
        boolean open      = status == VotingSessionStatus.OPEN;
        int     color     = open ? COLOR_OPEN : COLOR_CLOSED;
        int     total     = session.getVotes().size();

        Map<Long, Long> tally = session.getVotes().stream()
                .flatMap(v -> v.getChosenEventIds().stream())
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));

        Set<Long> winners = resolveWinners(tally, !open);

        List<ContainerChildComponent> components = new ArrayList<>();

        components.add(Section.of(
                Thumbnail.fromUrl(ICON_VOTE),
                TextDisplay.of(
                        "# 🗳️  Weekly Match Vote\n" +
                                "**Week " + session.getIsoWeek() + " — " + session.getIsoYear() + "**\n" +
                                "-# STATUS: " + status.name()
                )
        ));

        components.add(Separator.createDivider(Separator.Spacing.LARGE));
        components.add(TextDisplay.of("-# **PICK YOUR 2 MATCHES FOR THE WEEK**"));
        components.add(Separator.createDivider(Separator.Spacing.SMALL));

        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            long  votes  = tally.getOrDefault(event.getId(), 0L);
            char  label  = (char) ('A' + i);

            String bar    = buildProgressBar(votes, total);
            String trophy = (!open && winners.contains(event.getId())) ? " 🏆" : "";

            components.add(TextDisplay.of(
                    "**Match " + label + trophy + " — " + event.getEventName() + "**\n" +
                            "-# 🗓️ " + formatTime(event.getStartTime()) + "\n" +
                            "-# " + bar + "  " + votes + " vote(s)"
            ));

            if (i < events.size() - 1) {
                components.add(Separator.createDivider(Separator.Spacing.SMALL));
            }
        }

        components.add(Separator.createDivider(Separator.Spacing.LARGE));

        String footerLine = open
                ? "-# Select **2 matches** you want to play this week. Votes are locked once submitted.\n" +
                "-# ⏳ Voting closes: <t:" + session.getClosesAt().getEpochSecond() + ":R>"
                : "-# 🔒 Voting is closed. The two most-voted matches are highlighted above.";

        components.add(TextDisplay.of(footerLine));

        if (open) {
            components.add(Separator.createDivider(Separator.Spacing.SMALL));

            List<Button> buttons = new ArrayList<>();
            for (int i = 0; i < events.size(); i++) {
                char label = (char) ('A' + i);
                buttons.add(Button.primary(
                        "vote:select:" + session.getId() + ":" + events.get(i).getId(),
                        "Match " + label
                ));
            }
            components.add(ActionRow.of(buttons));
        }

        return Container.of(components).withAccentColor(color);
    }

    private Set<Long> resolveWinners(Map<Long, Long> tally, boolean closed) {
        if (!closed || tally.isEmpty()) return Set.of();

        return tally.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static String buildProgressBar(long votes, int total) {
        int filled = total == 0 ? 0 : (int) Math.round((double) votes / total * 10);
        return "▓".repeat(filled) + "░".repeat(10 - filled);
    }

    private static String formatTime(LocalDateTime time) {
        return time.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) + " UTC";
    }

    public enum VoteResult {
        ACCEPTED,
        ALREADY_VOTED,
        SESSION_CLOSED,
        INVALID,
        DENIED
    }
}