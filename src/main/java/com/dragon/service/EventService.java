package com.dragon.service;

import com.dragon.dto.event.EventCreationRequest;
import com.dragon.dto.event.EventStatus;
import com.dragon.entity.event.Event;
import com.dragon.repository.event.EventRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.ScheduledEvent;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {
    private final JDA jda;
    private final EventRepository eventRepository;

    @Value("${discord.guild.id}")
    private String guildId;

    @Value("${discord.general.channel.id}")
    private String generalChannelId;

    private static final int COLOR_STARTED   = 0x57F287; // Green
    private static final int COLOR_CANCELLED = 0xED4245; // Red
    private static final int COLOR_ENDED     = 0xFEE75C; // Yellow
    private static final int COLOR_REMINDER  = 0xEB459E; // Pink/magenta — reminder

    private static final String ICON_STARTED   = "https://cdn.discordapp.com/attachments/1501768878051692624/1502047010939998318/1778186317715.png?ex=69fe49ca&is=69fcf84a&hm=10ca4f6c9f8f77f7b020580a6762edbbe3098e0f7799239f22341b6ed09480bf&";
    private static final String ICON_CANCELLED = "https://cdn.discordapp.com/attachments/1501768878051692624/1502047011585786076/1778186317715.png?ex=69fe49ca&is=69fcf84a&hm=6f44a263d286a6511e96e7333b11b2b4e738f016c8e6198bcd6b96d57cd5509f&";
    private static final String ICON_ENDED     = "https://cdn.discordapp.com/attachments/1501768878051692624/1502047011287863406/1778186317715.png?ex=69fe49ca&is=69fcf84a&hm=3676c7d98b8e7a8f8762affbc2b06d23b777795dddd21f59e8bd38b766ab1138&";
    private static final String ICON_REMINDER  = "https://cdn.discordapp.com/attachments/1501768878051692624/1502048750523388025/1778186732585.png?ex=69fe4b68&is=69fcf9e8&hm=a855ae680d3e51210bfdffcedaf3788927a2607357ef5aeaec205330673afbc8&";

    public void createEvent(EventCreationRequest creationRequest, EventCallback callback) {
        Event event = new Event();
        event.setGuildId(guildId);
        event.setEventName(creationRequest.eventName());
        event.setEventDescription(creationRequest.eventDescription());
        event.setChannelId(creationRequest.voiceChannel().getIdLong());
        event.setStartTime(creationRequest.startTime());
        event.setEndTime(creationRequest.endTime());
        event.setStatus(EventStatus.STANDBY);
        event.setVoted(false);
        event.setHasBeenReminded(false);

        eventRepository.save(event);
        callback.onSuccess(null, event);
    }

    @Transactional
    public boolean cancelEvent(Long eventId) {
        Event currentEvent = eventRepository.findById(eventId).orElseThrow();

        if (currentEvent.getStatus() == EventStatus.CANCELLED ||
                currentEvent.getStatus() == EventStatus.ENDED) {
            return false;
        }

        sendEventNotification(buildCancelledContainer(currentEvent), ICON_CANCELLED, "event_cancelled");

        currentEvent.setStatus(EventStatus.CANCELLED);
        return eventRepository.save(currentEvent).getStatus() == EventStatus.CANCELLED;
    }

    @Transactional
    public void startEvent(Long eventId, EventCallback callback) {
        Event currentEvent = eventRepository.findById(eventId).orElseThrow();

        if (currentEvent.getStatus() == EventStatus.STARTED ||
                currentEvent.getStatus() == EventStatus.ENDED) {
            callback.onError(currentEvent, "This event has already been active or ended.");
            return;
        }

        ScheduledEvent discordEvent = jda.getScheduledEventById(currentEvent.getDiscordEventId());
        if (discordEvent == null) {
            callback.onError(currentEvent, "This registered event is not available on the Discord server.");
            eventRepository.delete(currentEvent);
            return;
        }

        discordEvent.getManager()
                .setStatus(ScheduledEvent.Status.ACTIVE)
                .queue(
                        success -> handleEventStarted(discordEvent, currentEvent, callback),
                        error   -> callback.onError(currentEvent, error.getMessage())
                );
    }

    @Transactional
    public void endEvent(Long eventId, EventCallback callback) {
        Event currentEvent = eventRepository.findById(eventId).orElseThrow();

        ScheduledEvent discordEvent = jda.getScheduledEventById(currentEvent.getDiscordEventId());
        if (discordEvent == null) {
            callback.onError(currentEvent, "This registered event is not available on the Discord server.");
            eventRepository.delete(currentEvent);
            return;
        }

        discordEvent.getManager()
                .setStatus(ScheduledEvent.Status.COMPLETED)
                .queue(
                        success -> handleEventEnded(discordEvent, currentEvent, callback),
                        error   -> callback.onError(currentEvent, error.getMessage())
                );
    }

    public Event getCurrentActiveEvent() {
        return this.eventRepository.findAll()
                .stream()
                .filter(Event::isActive)
                .findFirst()
                .orElse(null);
    }

    public Event getEventById(Long eventId) {
        return eventRepository.findById(eventId).orElseThrow();
    }

    public Event getEventBySnowflake(ScheduledEvent discordEvent) {
        return eventRepository.findByDiscordEventId(discordEvent.getIdLong()).orElseThrow();
    }

    private void handleEventStarted(ScheduledEvent discordEvent, Event currentEvent, EventCallback callback) {
        currentEvent.setStatus(EventStatus.STARTED);
        eventRepository.save(currentEvent);
        sendEventNotification(buildStartedContainer(currentEvent), ICON_STARTED, "event_started");
        callback.onSuccess(discordEvent, currentEvent);
    }

    private void handleEventEnded(ScheduledEvent discordEvent, Event currentEvent, EventCallback callback) {
        currentEvent.setStatus(EventStatus.ENDED);
        eventRepository.save(currentEvent);
        sendEventNotification(buildEndedContainer(currentEvent), ICON_ENDED, "event_ended");
        callback.onSuccess(discordEvent, currentEvent);
    }

    private Container buildStartedContainer(Event event) {
        String startedAt = event.getStartTime() != null
                ? formatTime(OffsetDateTime.from(event.getStartTime()))
                : "Now";

        return Container.of(
                Section.of(
                        Thumbnail.fromUrl(ICON_STARTED),
                        TextDisplay.of(
                                "# ▶️  Event Started\n" +
                                        "**" + event.getEventName() + "**\n" +
                                        "-# STARTED: " + startedAt.toUpperCase()
                        )
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# **DETAILS**"),
                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("📋 Description\n-# " + event.getEventDescription()),
                TextDisplay.of("⏰ Start Time\n-# " + startedAt),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# Head over to the voice channel and join in!"),

                Separator.createDivider(Separator.Spacing.SMALL),

                ActionRow.of(
                        Button.secondary("event:info:" + event.getId(), "Event Info")
                )
        ).withAccentColor(COLOR_STARTED);
    }

    private Container buildCancelledContainer(Event event) {
        return Container.of(
                Section.of(
                        Thumbnail.fromUrl(ICON_CANCELLED),
                        TextDisplay.of(
                                "# ❌  Event Cancelled\n" +
                                        "**" + event.getEventName() + "**\n" +
                                        "-# STATUS: CANCELLED"
                        )
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# **DETAILS**"),
                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("📋 Event\n-# " + event.getEventName()),
                TextDisplay.of("🗓️ Was Scheduled For\n-# " +
                        (event.getStartTime() != null
                                ? formatTime(OffsetDateTime.from(event.getStartTime()))
                                : "Unknown")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# If you have questions, please contact a staff member."),

                Separator.createDivider(Separator.Spacing.SMALL),

                ActionRow.of(
                        Button.secondary("event:history:" + event.getId(), "View Past Events")
                )
        ).withAccentColor(COLOR_CANCELLED);
    }

    private Container buildEndedContainer(Event event) {
        String endedAt = event.getEndTime() != null
                ? formatTime(OffsetDateTime.from(event.getEndTime()))
                : "Now";

        return Container.of(
                Section.of(
                        Thumbnail.fromUrl(ICON_ENDED),
                        TextDisplay.of(
                                "# 🏁  Event Ended\n" +
                                        "**" + event.getEventName() + "**\n" +
                                        "-# ENDED: " + endedAt.toUpperCase()
                        )
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# **RECAP**"),
                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("📋 Event\n-# " + event.getEventName()),
                TextDisplay.of("⏰ Ended At\n-# " + endedAt),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# Thanks to everyone who joined — see you next time!"),

                Separator.createDivider(Separator.Spacing.SMALL),

                ActionRow.of(
                        Button.secondary("event:history:" + event.getId(), "View History")
                )
        ).withAccentColor(COLOR_ENDED);
    }


    // TODO: Rework to list all upcoming event in a single message
    @Scheduled(fixedRate = 60_000)
    public void sendDayBeforeReminders() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime windowStart = now.plusHours(23);
        LocalDateTime windowEnd   = now.plusHours(25);

        List<Event> upcoming = eventRepository
                .findByStatusAndStartTimeBetween(EventStatus.SCHEDULED, windowStart, windowEnd);

        for (Event event : upcoming) {
            if (event.getHasBeenReminded()) continue;

            log.info("Sending 24h reminder for event id={} name={}", event.getId(), event.getEventName());
            sendEventNotification(buildReminderContainer(event), ICON_REMINDER, "event_reminder");

            event.setHasBeenReminded(true);
            eventRepository.save(event);
        }
    }

    @Scheduled(fixedRate = 60_000) // every minute
    public void autoStartEvents() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        List<Event> toStart = eventRepository
                .findByStatusAndStartTimeBefore(EventStatus.SCHEDULED, now);

        for (Event event : toStart) {
            log.info("Auto-starting event id={} name={}", event.getId(), event.getEventName());
            this.startEvent(event.getId(), new EventService.EventCallback() {
                @Override
                public void onSuccess(net.dv8tion.jda.api.entities.ScheduledEvent discordEvent, Event currentEvent) {
                    log.info("Auto-started event id={}", currentEvent.getId());
                }

                @Override
                public void onError(Event currentEvent, String errorMessage) {
                    log.error("Failed to auto-start event id={}: {}", currentEvent.getId(), errorMessage);
                }
            });
        }
    }

    @Scheduled(fixedRate = 60_000) // every minute
    public void autoEndEvents() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        List<Event> toEnd = eventRepository
                .findByStatusAndEndTimeBefore(EventStatus.STARTED, now);

        for (Event event : toEnd) {
            log.info("Auto-ending event id={} name={}", event.getId(), event.getEventName());
            this.endEvent(event.getId(), new EventService.EventCallback() {
                @Override
                public void onSuccess(net.dv8tion.jda.api.entities.ScheduledEvent discordEvent, Event currentEvent) {
                    log.info("Auto-ended event id={}", currentEvent.getId());
                }

                @Override
                public void onError(Event currentEvent, String errorMessage) {
                    log.error("Failed to auto-end event id={}: {}", currentEvent.getId(), errorMessage);
                }
            });
        }
    }

    /**
     * Sends a Components V2 Container with a PNG icon attached.
     * The PNG must be placed in src/main/resources/icons/ and referenced
     * via "attachment://<filename>" inside the Container's Thumbnail.
     */
    private void sendEventNotification(Container container, String iconClasspath, String fileAlias) {
        TextChannel channel = getGeneralChannel();
        if (channel == null) {
            log.warn("General channel ({}) not found — skipping notification.", generalChannelId);
            return;
        }

        MessageCreateBuilder message = new MessageCreateBuilder()
                .useComponentsV2()
                .addComponents(container);

        channel.sendMessage(message.build()).queue(
                msg -> log.debug("Event notification sent (messageId={})", msg.getId()),
                err -> log.error("Failed to send event notification: {}", err.getMessage())
        );
    }

    private Container buildReminderContainer(Event event) {
        String startAt = formatTime(event.getStartTime());
        String timeLeft = computeTimeLeft(event.getStartTime());

        return Container.of(
                Section.of(
                        Thumbnail.fromUrl(ICON_REMINDER),
                        TextDisplay.of(
                                "# 🔔  Event Reminder\n" +
                                        "**" + event.getEventName() + "**\n" +
                                        "-# STARTING IN: " + timeLeft.toUpperCase()
                        )
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# **DETAILS**"),
                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("📋 Description\n-# " + event.getEventDescription()),
                TextDisplay.of("🗓️ Start Time\n-# " + startAt),
                TextDisplay.of("⏳ Time Remaining\n-# " + timeLeft),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# Make sure you're ready — the event begins tomorrow!"),

                Separator.createDivider(Separator.Spacing.SMALL),

                ActionRow.of(
                        Button.secondary("event:info:" + event.getId(), "Event Info")
                )
        ).withAccentColor(COLOR_REMINDER);
    }

    private TextChannel getGeneralChannel() {
        return jda.getTextChannelById(generalChannelId);
    }

    private static String formatTime(OffsetDateTime time) {
        return time.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm z"));
    }


    private static String formatTime(LocalDateTime time) {
        return time.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) + " UTC";
    }

    private static String computeTimeLeft(LocalDateTime startTime) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        long minutes = java.time.temporal.ChronoUnit.MINUTES.between(now, startTime);
        if (minutes <= 0) return "Imminent";
        long hours = minutes / 60;
        long mins  = minutes % 60;
        if (hours >= 24) {
            long days = hours / 24;
            long remHours = hours % 24;
            return remHours > 0
                    ? days + "d " + remHours + "h"
                    : days + " day" + (days != 1 ? "s" : "");
        }
        return hours > 0
                ? hours + "h " + (mins > 0 ? mins + "m" : "")
                : mins + "m";
    }

    public interface EventCallback {
        void onSuccess(ScheduledEvent discordEvent, Event currentEvent);
        void onError(Event currentEvent, String errorMessage);
    }
}