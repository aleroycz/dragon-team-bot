package com.dragon.repository;

import com.dragon.entity.MemberSanctionSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberSanctionSummaryRepository extends JpaRepository<MemberSanctionSummaryEntity, Long> {

    Optional<MemberSanctionSummaryEntity> findByUserIdAndGuildId(String userId, String guildId);
}
