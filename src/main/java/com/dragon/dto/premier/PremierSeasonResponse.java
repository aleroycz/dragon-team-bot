package com.dragon.dto.premier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Root response from GET /valorant/v1/premier/seasons/{region}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PremierSeasonResponse(
        int status,
        List<PremierSeason> data
) {

    private static final Instant NULL_EPOCH = Instant.parse("0001-01-01T00:00:00Z");
    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'UTC'").withZone(ZoneOffset.of("+01:00"));

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum EventType {
        LEAGUE,
        SCRIM,
        TOURNAMENT
    }

    public enum MapSelectionType {
        RANDOM,
        PICKBAN
    }

    public enum Conference {
        EU_EAST,
        EU_EAST_SUPER,
        EU_NORTH,
        EU_NORTH_SUPER,
        EU_DACH,
        EU_DACH_SUPER,
        EU_FRANCE,
        EU_FRANCE_SUPER,
        EU_IBIT,
        EU_IBIT_SUPER,
        EU_TURKEY,
        EU_TURKEY_SUPER,
        EU_MIDDLE_EAST,
        EU_MIDDLE_EAST_SUPER;

        /** Returns true if this is a Super division conference. */
        public boolean isSuper() {
            return name().endsWith("_SUPER");
        }

        /** Returns the base (non-super) conference for a Super conference, or itself if already base. */
        public Conference base() {
            if (!isSuper()) return this;
            return valueOf(name().replace("_SUPER", ""));
        }
    }

    // ── Root helpers ──────────────────────────────────────────────────────────

    /**
     * Finds the currently active season — the one that has started and not yet ended.
     * If multiple qualify, returns the one with the most recent start date.
     */
    public Optional<PremierSeason> findCurrentSeason() {
        Instant now = Instant.now();
        return data.stream()
                .filter(s -> !s.startsAtInstant().equals(NULL_EPOCH))
                .filter(s -> !s.endsAtInstant().equals(NULL_EPOCH))
                .filter(s -> s.startsAtInstant().isBefore(now))
                .filter(s -> s.endsAtInstant().isAfter(now))
                .filter(s -> s.scheduledEvents() != null && !s.scheduledEvents().isEmpty())
                .max(Comparator.comparing(PremierSeason::startsAtInstant));
    }

    public void toPrint() {
        String line  = "═".repeat(72);
        String dash  = "─".repeat(72);

        System.out.println("\n" + line);
        System.out.printf("  PREMIER SEASONS RESPONSE  (status: %d, seasons: %d)%n", status, data.size());
        System.out.println(line);

        for (int i = 0; i < data.size(); i++) {
            data.get(i).toPrint(i + 1, data.size());
        }

        findCurrentSeason().ifPresentOrElse(
                s -> {
                    System.out.println("\n" + line);
                    System.out.println("  ★  CURRENT ACTIVE SEASON");
                    System.out.println(line);
                    s.toPrint(data.indexOf(s) + 1, data.size());
                },
                () -> {
                    System.out.println("\n" + line);
                    System.out.println("  ✗  No active season found.");
                    System.out.println(line);
                }
        );
    }

    // ── Records ───────────────────────────────────────────────────────────────

    /**
     * A single Premier season (act).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PremierSeason(
            String id,

            @JsonProperty("championship_event_id")
            String championshipEventId,

            @JsonProperty("championship_points_required")
            int championshipPointsRequired,

            @JsonProperty("starts_at")
            String startsAt,

            @JsonProperty("ends_at")
            String endsAt,

            @JsonProperty("enrollment_starts_at")
            String enrollmentStartsAt,

            @JsonProperty("enrollment_ends_at")
            String enrollmentEndsAt,

            List<PremierEvent> events,

            @JsonProperty("scheduled_events")
            List<ScheduledEvent> scheduledEvents


    ) {
        public Instant startsAtInstant() { return Instant.parse(startsAt); }
        public Instant endsAtInstant()   { return Instant.parse(endsAt); }

        /** Whether this season is currently active. */
        public boolean isActive() {
            Instant now = Instant.now();
            return startsAtInstant().isBefore(now) && endsAtInstant().isAfter(now);
        }

        /**
         * Returns all scheduled events for a given conference, excluding null-epoch placeholders.
         */
        public List<ScheduledEvent> getScheduledEventsForConference(Conference conference) {
            if (scheduledEvents == null) return List.of();
            ZoneOffset gmt2 = ZoneOffset.of("+01:00");
            ZoneOffset systemOffset = ZoneOffset.systemDefault()
                    .getRules()
                    .getOffset(Instant.now());

            return scheduledEvents.stream()
                    .filter(se -> se.conference() == conference)
                    .filter(se -> !se.startsAtInstant().equals(NULL_EPOCH))
                    .map(se -> {
                        Instant normalized = se.startsAtInstant()
                                .atOffset(systemOffset)
                                .withOffsetSameLocal(gmt2)
                                .toInstant();
                        Instant normalizedEnd = se.endsAtInstant()
                                .atOffset(systemOffset)
                                .withOffsetSameLocal(gmt2)
                                .toInstant();
                        return new ScheduledEvent(se.eventId(), normalized.toString(), normalizedEnd.toString(), se.conference());
                    })
                    .collect(Collectors.toMap(
                            se -> se.startsAtInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString(),
                            se -> se,
                            (a, b) -> a.startsAtInstant().isBefore(b.startsAtInstant()) ? a : b,
                            LinkedHashMap::new
                    ))
                    .values()
                    .stream()
                    .toList();
        }

        /**
         * Returns all scheduled events for a conference that haven't ended yet.
         */
        public List<ScheduledEvent> getUpcomingScheduledEventsForConference(Conference conference) {
            Instant now = Instant.now();
            return getScheduledEventsForConference(conference).stream()
                    .filter(se -> se.startsAtInstant().isAfter(now))
                    .collect(Collectors.toMap(
                            se -> se.startsAtInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString(),
                            se -> se,
                            (a, b) -> a.startsAtInstant().isBefore(b.startsAtInstant()) ? a : b,
                            LinkedHashMap::new
                    ))
                    .values()
                    .stream()
                    .sorted(Comparator.comparing(ScheduledEvent::startsAtInstant))
                    .toList();
        }

        /**
         * Returns all logical events of a given type.
         */
        public List<PremierEvent> getEventsByType(EventType type) {
            if (events == null) return List.of();
            return events.stream()
                    .filter(e -> e.type() == type)
                    .toList();
        }

        /**
         * Looks up the logical event for a given event ID.
         */
        public Optional<PremierEvent> findEventById(String eventId) {
            if (events == null) return Optional.empty();
            return events.stream()
                    .filter(e -> e.id().equals(eventId))
                    .findFirst();
        }

        /**
         * Returns the championship (tournament) event, if present.
         */
        public Optional<PremierEvent> getChampionshipEvent() {
            return findEventById(championshipEventId);
        }

        /**
         * Returns all unique conferences that have at least one scheduled event.
         */
        public List<Conference> getActiveConferences() {
            if (scheduledEvents == null) return List.of();
            return scheduledEvents.stream()
                    .map(ScheduledEvent::conference)
                    .distinct()
                    .toList();
        }

        public void toPrint(int index, int total) {
            String dash = "─".repeat(72);
            String status = isActive() ? " [ACTIVE]" : "";

            System.out.println("\n" + dash);
            System.out.printf("  Season %d / %d%s%n", index, total, status);
            System.out.println(dash);
            System.out.printf("  ID              : %s%n", id);
            System.out.printf("  Period          : %s  →  %s%n",
                    DISPLAY_FMT.format(startsAtInstant()),
                    DISPLAY_FMT.format(endsAtInstant()));
            System.out.printf("  Championship    : %s (requires %d pts)%n",
                    championshipEventId, championshipPointsRequired);

            // Events breakdown
            if (events != null && !events.isEmpty()) {
                long leagues     = events.stream().filter(PremierEvent::isLeague).count();
                long scrims      = events.stream().filter(PremierEvent::isPractice).count();
                long tournaments = events.stream().filter(PremierEvent::isTournament).count();
                System.out.printf("  Events          : %d total  (%d league, %d scrim, %d tournament)%n",
                        events.size(), leagues, scrims, tournaments);

                // Print each event
                System.out.println();
                for (PremierEvent event : events) {
                    event.toPrint();
                }
            } else {
                System.out.println("  Events          : none");
            }

            // Scheduled events breakdown
            if (scheduledEvents != null && !scheduledEvents.isEmpty()) {
                long upcoming = scheduledEvents.stream().filter(ScheduledEvent::isUpcoming).count();
                long past     = scheduledEvents.stream().filter(ScheduledEvent::isPast).count();
                List<Conference> conferences = getActiveConferences();

                System.out.printf("%n  Scheduled Slots : %d total  (%d upcoming, %d past)%n",
                        scheduledEvents.size(), upcoming, past);
                System.out.printf("  Conferences     : %s%n",
                        conferences.isEmpty() ? "none" : conferences.stream()
                                .map(Conference::name)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("none"));

                // Print upcoming scheduled events grouped by day
                List<ScheduledEvent> upcomingList = scheduledEvents.stream()
                        .filter(ScheduledEvent::isUpcoming)
                        .sorted(Comparator.comparing(ScheduledEvent::startsAtInstant))
                        .toList();

                if (!upcomingList.isEmpty()) {
                    System.out.println();
                    System.out.println("  Upcoming Scheduled Events:");
                    String lastDay = null;
                    for (ScheduledEvent se : upcomingList) {
                        String day = se.startsAtInstant()
                                .atOffset(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString();
                        if (!day.equals(lastDay)) {
                            System.out.printf("    ┌─ %s%n", day);
                            lastDay = day;
                        }
                        se.toPrint("    │  ");
                    }
                    System.out.println("    └─ (end)");
                }
            } else {
                System.out.println("  Scheduled Slots : none");
            }
        }
    }

    /**
     * A logical event within a season (LEAGUE, SCRIM, TOURNAMENT).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PremierEvent(
            String id,
            EventType type,

            @JsonProperty("starts_at")
            String startsAt,

            @JsonProperty("ends_at")
            String endsAt,

            @JsonProperty("conference_schedules")
            List<Object> conferenceSchedules,

            @JsonProperty("map_selection")
            MapSelection mapSelection,

            @JsonProperty("points_required_to_participate")
            int pointsRequiredToParticipate
    ) {
        public boolean isPractice()    { return type == EventType.SCRIM; }
        public boolean isTournament()  { return type == EventType.TOURNAMENT; }
        public boolean isLeague()      { return type == EventType.LEAGUE; }

        /** Returns the first map in the selection, or empty if none. */
        public Optional<GameMap> firstMap() {
            if (mapSelection == null || mapSelection.maps() == null || mapSelection.maps().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(mapSelection.maps().getFirst());
        }

        /** Returns the first map name, or "TBD" if not set. */
        public String firstMapName() {
            return firstMap().map(GameMap::name).orElse("TBD");
        }

        public void toPrint() {
            String typeLabel = switch (type) {
                case LEAGUE     -> "[ LEAGUE     ]";
                case SCRIM      -> "[ SCRIM      ]";
                case TOURNAMENT -> "[ TOURNAMENT ]";
            };
            String selectionLabel = mapSelection != null
                    ? (mapSelection.isPickBan() ? "Pick/Ban" : "Random")
                    : "N/A";
            String mapsLabel = mapSelection != null && mapSelection.maps() != null
                    ? mapSelection.maps().stream()
                    .map(GameMap::name)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("none")
                    : "none";
            System.out.printf("  %s  %-22s  %-8s  [%s]%n",
                    typeLabel, id, selectionLabel, mapsLabel);
        }
    }

    /**
     * Map selection config for an event.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MapSelection(
            MapSelectionType type,
            List<GameMap> maps
    ) {
        public boolean isPickBan() { return type == MapSelectionType.PICKBAN; }
        public boolean isRandom()  { return type == MapSelectionType.RANDOM; }
    }

    /**
     * A single map entry.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GameMap(
            String name,
            String id
    ) {}

    /**
     * A concrete time-slot for an event in a specific conference.
     * The API returns many of these per logical event — one per conference per time window,
     * often with duplicates.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScheduledEvent(
            @JsonProperty("event_id")
            String eventId,

            @JsonProperty("starts_at")
            String startsAt,

            @JsonProperty("ends_at")
            String endsAt,

            Conference conference
    ) {
        public Instant startsAtInstant() { return Instant.parse(startsAt); }
        public Instant endsAtInstant()   { return Instant.parse(endsAt); }

        public boolean isNullEpoch()  { return startsAtInstant().equals(NULL_EPOCH); }
        public boolean isPast()       { return endsAtInstant().isBefore(Instant.now()); }
        public boolean isUpcoming()   { return !isPast() && !isNullEpoch(); }
        public boolean isSuperDivision() { return conference.isSuper(); }

        /** Deduplication key: same event on the same day. */
        public String dayKey() {
            return eventId + "|" + startsAtInstant().atOffset(java.time.ZoneOffset.UTC).toLocalDate();
        }

        /** Deduplication key: exact moment (eventId + epoch second). */
        public String exactKey() {
            return eventId + "|" + startsAtInstant().getEpochSecond();
        }

        public void toPrint(String indent) {
            System.out.printf("%s%s  →  %s   %-22s  %s%s%n",
                    indent,
                    DISPLAY_FMT.format(startsAtInstant()),
                    DISPLAY_FMT.format(endsAtInstant()),
                    conference.name(),
                    eventId,
                    isSuperDivision() ? "  ★" : "");
        }

        /** Prints with default indentation. */
        public void toPrint() {
            toPrint("  ");
        }
    }
}