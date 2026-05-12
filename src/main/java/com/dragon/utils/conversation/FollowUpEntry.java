package com.dragon.utils.conversation;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class FollowUpEntry {
    private String  topic;        // e.g. "deploy was failing"
    private String  context;      // surrounding detail
    private Instant detectedAt;
    private boolean raised;       // true once the bot has mentioned it proactively
}