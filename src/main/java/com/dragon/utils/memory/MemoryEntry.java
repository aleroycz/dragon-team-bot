package com.dragon.utils.memory;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A single memory entry stored in the vector memory file.
 * Each entry captures a conversation exchange and its embedding vector.
 */
@Setter
@Getter
public class MemoryEntry implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Unique ID for this memory. */
    private String id;

    /** The user's original message. */
    private String userMessage;

    /** The assistant's response. */
    private String assistantResponse;

    /** Optional tag/category for this memory (e.g. "preference", "fact", "correction"). */
    private String tag;

    /** Semantic embedding vector (float array). */
    private float[] embedding;

    /** When this memory was created (epoch seconds). */
    private long createdAt;

    public MemoryEntry() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now().getEpochSecond();
    }

    public MemoryEntry(String userMessage, String assistantResponse, String tag, float[] embedding) {
        this();
        this.userMessage = userMessage;
        this.assistantResponse = assistantResponse;
        this.tag = tag;
        this.embedding = embedding;
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    @Override
    public String toString() {
        return "MemoryEntry{id='%s', tag='%s', createdAt=%d, user='%s'}"
                .formatted(id, tag, createdAt, userMessage);
    }
}