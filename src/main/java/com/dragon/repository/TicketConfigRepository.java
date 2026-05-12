package com.dragon.repository;

import com.dragon.entity.TicketConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TicketConfigRepository extends JpaRepository<TicketConfig, Long> {

    Optional<TicketConfig> findByGuildId(String guildId);
}
