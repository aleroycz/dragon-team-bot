package com.dragon.dto.interview;

/**
 * Structured follow-up questions used by the AI to determine if a member is 18+.
 * These are phrased naturally to avoid feeling like an interrogation.
 */
public enum AgeVerificationQuestion {
    SCHOOL_OR_WORK(
            "Are you currently in school, university, or working full-time?"
    ),
    VALORANT_LAUNCH(
            "Valorant launched in June 2020 — were you already playing competitive FPS games before that?"
    ),
    FIRST_JOB(
            "Have you had any work experience yet, like a part-time job or internship?"
    ),
    DRIVING(
            "Do you drive, or are you working towards getting your licence?"
    ),
    GAME_HISTORY(
            "What other competitive games did you play before Valorant, and for how long?"
    ),
    LIVING_SITUATION(
            "Are you living with family, in student accommodation, or independently?"
    );

    public final String text;

    AgeVerificationQuestion(String text) {
        this.text = text;
    }
}
