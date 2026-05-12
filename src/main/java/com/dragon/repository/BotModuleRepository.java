package com.dragon.repository;

import com.dragon.entity.BotModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BotModuleRepository extends JpaRepository<BotModule, Long> {
    Optional<BotModule> findByName(String name);
}
