package com.dragon.repository;

import com.dragon.entity.ModeratorNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModeratorNoteRepository extends JpaRepository<ModeratorNote, Long> {

    /** Returns all notes for a member in a guild, newest first. */
    List<ModeratorNote> findByTargetIdAndGuildIdOrderByCreatedAtDesc(String targetId, String guildId);

    /** Guild-scoped lookup so a note ID from guild A cannot be deleted in guild B. */
    Optional<ModeratorNote> findByIdAndGuildId(Long id, String guildId);

    long countByTargetIdAndGuildId(String targetId, String guildId);
}
