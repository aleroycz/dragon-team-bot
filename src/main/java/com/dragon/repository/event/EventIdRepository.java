package com.dragon.repository.event;

import com.dragon.entity.event.EventIdEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface EventIdRepository extends JpaRepository<EventIdEntity, Long> {
    boolean existsByEventIdAndEventDate(
            String eventId,
            LocalDateTime eventDate
    );
}
