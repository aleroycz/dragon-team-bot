package com.dragon.service.moderation;

import com.dragon.entity.ModeratorNote;
import com.dragon.repository.ModeratorNoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Manages private moderator notes attached to guild members.
 *
 * Notes are internal records visible only to staff — they are not sanctions
 * and carry no enforcement weight. Their purpose is to preserve moderation
 * context across staff shifts: e.g. "suspected alt account", "verbal warning
 * given off-record", "member flagged by another server".
 *
 * A hard ceiling of {@value MAX_NOTES_PER_MEMBER} notes per member per guild
 * prevents unbounded growth and keeps the lookup embed within Discord's field limit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModeratorNoteService {

    /** Maximum note body length — matches the DB column length. */
    private static final int MAX_NOTE_LENGTH      = 1000;

    /** Per-member, per-guild ceiling to keep embeds within Discord's 25-field limit. */
    private static final int MAX_NOTES_PER_MEMBER = 20;

    private final ModeratorNoteRepository noteRepository;

    /**
     * Creates a private note on a member's record.
     *
     * Content is silently truncated to {@value MAX_NOTE_LENGTH} characters if
     * it exceeds the limit, mirroring how sanction reasons are handled.
     *
     * @param targetId  Discord snowflake of the member being annotated.
     * @param targetTag Display tag at the time of writing — stored for audit purposes
     *                  in case the member later changes their username.
     * @param guildId   Guild in which the note applies.
     * @param authorId  Discord snowflake of the moderator writing the note.
     * @param authorTag Display tag of the moderator at the time of writing.
     * @param content   Note body text.
     * @return The persisted {@link ModeratorNote}.
     * @throws IllegalArgumentException if content is blank.
     * @throws IllegalStateException    if the member already has the maximum number of notes.
     */
    @Transactional
    public ModeratorNote addNote(String targetId, String targetTag,
                                 String guildId,
                                 String authorId, String authorTag,
                                 String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Note content cannot be empty.");
        }
        if (content.length() > MAX_NOTE_LENGTH) {
            content = content.substring(0, MAX_NOTE_LENGTH - 3) + "...";
        }

        long existing = noteRepository.countByTargetIdAndGuildId(targetId, guildId);
        if (existing >= MAX_NOTES_PER_MEMBER) {
            throw new IllegalStateException(
                    "Member already has the maximum of " + MAX_NOTES_PER_MEMBER
                            + " notes in this guild. Delete an old one before adding a new one."
            );
        }

        ModeratorNote note = ModeratorNote.builder()
                .targetId(targetId)
                .targetTag(targetTag)
                .guildId(guildId)
                .authorId(authorId)
                .authorTag(authorTag)
                .content(content)
                .build();

        ModeratorNote saved = noteRepository.save(note);
        log.info("[Notes] Note #{} added — target: {}, author: {}", saved.getId(), targetTag, authorTag);
        return saved;
    }

    /**
     * Returns all notes for a member in a guild, ordered newest-first.
     */
    public List<ModeratorNote> getNotes(String targetId, String guildId) {
        return noteRepository.findByTargetIdAndGuildIdOrderByCreatedAtDesc(targetId, guildId);
    }

    /**
     * Deletes a note by ID, scoped to the guild.
     *
     * The guild scope prevents a moderator in guild A from deleting notes written
     * in guild B even if they somehow obtain a note ID (e.g. via guessing).
     *
     * @return {@code true} if the note existed and was deleted; {@code false} if not found.
     */
    @Transactional
    public boolean deleteNote(Long noteId, String guildId) {
        Optional<ModeratorNote> note = noteRepository.findByIdAndGuildId(noteId, guildId);
        if (note.isEmpty()) return false;
        noteRepository.delete(note.get());
        log.info("[Notes] Note #{} deleted from guild {}", noteId, guildId);
        return true;
    }

    /** Returns the number of notes on record for a member in a guild. */
    public long countNotes(String targetId, String guildId) {
        return noteRepository.countByTargetIdAndGuildId(targetId, guildId);
    }
}
