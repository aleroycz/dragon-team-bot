package com.dragon.repository.event;

import com.dragon.dto.event.EventStatus;
import com.dragon.entity.event.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {


    Optional<Event> findByDiscordEventId(Long discordEventId);

    // For reminders: SCHEDULED events starting within a time window
    List<Event> findByStatusAndStartTimeBetween(
            EventStatus status,
            LocalDateTime windowStart,
            LocalDateTime windowEnd
    );

    // For auto-start: SCHEDULED events whose startTime has passed
    List<Event> findByStatusAndStartTimeBefore(
            EventStatus status,
            LocalDateTime time
    );

    // For auto-end: STARTED events whose endTime has passed
    List<Event> findByStatusAndEndTimeBefore(
            EventStatus status,
            LocalDateTime time
    );

    boolean existsByEventNameAndStartTime(String eventName, LocalDateTime startTime);
}
