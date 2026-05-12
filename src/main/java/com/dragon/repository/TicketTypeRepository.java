package com.dragon.repository;

import com.dragon.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

    List<TicketType> findByGuildIdAndEnabledTrueOrderBySortOrderAsc(String guildId);

    List<TicketType> findByGuildIdOrderBySortOrderAsc(String guildId);

    Optional<TicketType> findByIdAndGuildId(Long id, String guildId);

    long countByGuildId(String guildId);
}
