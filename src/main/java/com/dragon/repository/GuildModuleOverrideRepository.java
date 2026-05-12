package com.dragon.repository;

import com.dragon.entity.GuildModuleOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GuildModuleOverrideRepository extends JpaRepository<GuildModuleOverride, Long> {
    Optional<GuildModuleOverride> findByGuildIdAndModuleName(String guildId, String moduleName);
    List<GuildModuleOverride> findByGuildId(String guildId);
}
