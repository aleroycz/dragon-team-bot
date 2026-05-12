package com.dragon.service.premier;

import com.dragon.dto.event.EventStatus;
import com.dragon.dto.premier.PremierSeasonResponse;
import com.dragon.dto.event.EventCreationRequest;
import com.dragon.entity.event.Event;
import com.dragon.repository.event.EventRepository;
import com.dragon.service.EventService;
import com.dragon.service.VotingSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.ScheduledEvent;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PremierScheduleService {

    @Value("${henrikdev.api.key}")
    private String apiKey;

    @Value("${valorant.region:eu}")
    private String region;

    @Value("${discord.guild.id}")
    private String guildId;

    @Value("${discord.premier.voice.channel.id}")
    private String premierVoiceChannelId;

    @Value("${valorant.conference:EU_DACH}")
    private String conference;

    private final RestTemplate restTemplate;
    private final EventService eventService;
    private final EventRepository eventRepository;
    private final VotingSessionService votingSessionService;
    private final JDA jda;

    private static final String BASE_URL = "https://api.henrikdev.xyz";

    @Scheduled(fixedDelay = 60_000, initialDelay = 1L)
    public void syncWeekVoting() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int nowYear = now.get(IsoFields.WEEK_BASED_YEAR);
        int nowWeek = now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        List<Event> weekEvents = eventRepository.findAll().stream()
                .filter(e -> e.getStatus() == EventStatus.STANDBY)
                .filter(e -> e.getStartTime().get(IsoFields.WEEK_BASED_YEAR) == nowYear
                        && e.getStartTime().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == nowWeek)
                .toList();

        if (weekEvents.size() >= 4) {
            votingSessionService.openSessionForWeekIfAbsent(weekEvents);
            return;
        }

        log.warn("Current week {}-W{} has fewer than 4 Premier events (found={}) — checking next week.",
                nowYear, nowWeek, weekEvents.size());

        LocalDateTime nextWeekAnchor = now.plusWeeks(1);
        int nextYear = nextWeekAnchor.get(IsoFields.WEEK_BASED_YEAR);
        int nextWeek = nextWeekAnchor.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        List<Event> nextWeekEvents = eventRepository.findAll().stream()
                .filter(e -> e.getStatus() == EventStatus.STANDBY)
                .filter(e -> e.getStartTime().get(IsoFields.WEEK_BASED_YEAR) == nextYear
                        && e.getStartTime().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == nextWeek)
                .toList();

        if (nextWeekEvents.size() >= 4) {
            votingSessionService.openSessionForWeekIfAbsent(nextWeekEvents);
            return;
        }

        log.warn("Next week {}-W{} also has fewer than 4 Premier events (found={}) — no vote opened.",
                nextYear, nextWeek, nextWeekEvents.size());
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 1L)
    public void syncPremierEvents() {
        PremierSeasonResponse body = fetchSeasonResponse();
        if (body == null) return;

        PremierSeasonResponse.PremierSeason season = body.findCurrentSeason().orElse(null);
        if (season == null) {
            log.warn("No active premier season found — nothing to sync.");
            return;
        }

        log.info("Using premier season: id={} ends_at={}", season.id(), season.endsAt());

        PremierSeasonResponse.Conference conf =
                PremierSeasonResponse.Conference.valueOf(conference);

        List<PremierSeasonResponse.ScheduledEvent> schedule =
                season.getUpcomingScheduledEventsForConference(conf);

        if (schedule.isEmpty()) {
            log.warn("Premier schedule returned no entries — nothing to sync.");
            return;
        }

        VoiceChannel voiceChannel = Objects.requireNonNull(jda.getGuildById(guildId))
                .getVoiceChannelById(premierVoiceChannelId);

        if (voiceChannel == null) {
            log.error("Premier voice channel not found (id={}). Aborting sync.", premierVoiceChannelId);
            return;
        }

        Set<String> existingKeys = eventRepository.findAll().stream()
                .map(e -> e.getEventName() + "|" + e.getStartTime()
                        .toInstant(ZoneOffset.UTC).getEpochSecond())
                .collect(Collectors.toSet());

        int created = 0, skipped = 0;

        for (PremierSeasonResponse.ScheduledEvent se : schedule) {
            PremierSeasonResponse.PremierEvent meta =
                    season.findEventById(se.eventId()).orElse(null);

            boolean isPractice   = meta != null && meta.isPractice();
            boolean isTournament = meta != null && meta.isTournament();
            String  mapName      = meta != null ? meta.firstMapName() : "TBD";

            LocalDateTime startLdt = LocalDateTime.ofInstant(se.startsAtInstant(), ZoneOffset.UTC);
            LocalDateTime endLdt   = LocalDateTime.ofInstant(se.endsAtInstant(),   ZoneOffset.UTC);

            String eventName = toEventName(isPractice, isTournament, mapName);
            String key = eventName + "|" + se.startsAtInstant().getEpochSecond();

            if (!existingKeys.add(key)) {
                skipped++;
                continue;
            }

            EventCreationRequest request = new EventCreationRequest(
                    eventName,
                    toEventDescription(isPractice, isTournament, mapName),
                    voiceChannel,
                    startLdt,
                    endLdt
            );

            eventService.createEvent(request, new EventService.EventCallback() {
                @Override
                public void onSuccess(ScheduledEvent discordEvent, Event currentEvent) {
                    log.info("Created premier event: {} (id={})",
                            currentEvent.getEventName(), currentEvent.getId());
                }

                @Override
                public void onError(Event currentEvent, String errorMessage) {
                    log.error("Failed to create premier event {}: {}",
                            currentEvent.getEventName(), errorMessage);
                }
            });

            created++;
        }

        log.info("Premier sync complete — created={}, skipped={}", created, skipped);
    }

    private PremierSeasonResponse fetchSeasonResponse() {
        String url = BASE_URL + "/valorant/v1/premier/seasons/" + region;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", apiKey);

        ResponseEntity<PremierSeasonResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                PremierSeasonResponse.class
        );

        PremierSeasonResponse body = response.getBody();
        if (body == null) {
            log.error("Henrik API returned an empty or malformed response.");
        }
        return body;
    }

    private static String toEventName(boolean practice, boolean tournament, String map) {
        if (tournament) return "Premier Playoffs — " + map;
        if (practice)   return "Premier Practice — " + map;
        return                 "Premier Match — "    + map;
    }

    private static String toEventDescription(boolean practice, boolean tournament, String map) {
        if (tournament) return "Premier playoff match on " + map +
                ". Pick/ban phase active — coordinate your map choices in advance.";
        if (practice)   return "Premier practice session on " + map +
                ". Use this time to warm up and coordinate with your team.";
        return                 "Premier match on " + map +
                ". Queue opens on time — make sure your full roster is ready.";
    }
}