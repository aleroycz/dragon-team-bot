package com.dragon.repository;

import com.dragon.dto.ticket.TicketStatus;
import com.dragon.entity.TicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<TicketEntity, Long> {

    Optional<TicketEntity> findByChannelId(String channelId);

    List<TicketEntity> findByGuildIdAndStatus(String guildId, TicketStatus status);

    Optional<TicketEntity> findByCreatorIdAndGuildIdAndStatus(
            String creatorId, String guildId, TicketStatus status);

    long countByGuildIdAndStatus(String guildId, TicketStatus status);

    /**
     * Returns the next sequential ticket number for a guild.
     * Uses MAX + 1 so numbers are never reused even after deletion.
     */
    @Query("SELECT COALESCE(MAX(t.ticketNumber), 0) + 1 FROM TicketEntity t WHERE t.guildId = :guildId")
    int getNextTicketNumber(@Param("guildId") String guildId);
}
