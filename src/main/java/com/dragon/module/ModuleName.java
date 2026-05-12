package com.dragon.module;

import lombok.Getter;

/**
 * Enumeration of every feature module in the bot.
 *
 * Each constant maps to a {@code BotModule} row in the database, allowing
 * global enable/disable and maintenance states without redeployment.
 */
@Getter
public enum ModuleName {

    MODERATION("Moderation", "Warns, mutes, timeouts, bans and warn escalation."),
    TICKETS("Ticket System", "Per-guild support ticket creation, claiming and closing."),
    VOTING("Premier Voting", "Weekly premier match vote sessions and democratic resolution."),
    AGE_VERIFICATION("Age Verification", "Automated age screening inside interview tickets."),
    VOICE_EVALUATION("Voice Evaluation", "AI-powered communication analysis during Premier voice sessions."),
    AI_ASSISTANT("AI Assistant", "The server AI Assistant."),
    BUG_REPORT("Bug Reports", "In-server bug report submission via /bug-report.");

    public final String displayName;
    public final String description;

    ModuleName(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public static ModuleName fromString(String value) {
        for (ModuleName m : values()) {
            if (m.name().equalsIgnoreCase(value)) return m;
        }
        throw new IllegalArgumentException("Unknown module: " + value);
    }
}
