package com.dragon.dto.interview;


import java.util.ArrayList;
import java.util.List;

public record InterviewState(
        String channelId,
        String memberId,
        String memberName,
        List<String> messageHistory,
        MutableState mutableState
) {
    public record MutableState(
            int followUpCount,
            boolean veriffTriggered,
            VerificationStage stage
    ) {}

    public enum VerificationStage {
        /** Waiting for initial interview answers */
        AWAITING_ANSWERS,
        /** AI is asking follow-up age-related questions */
        FOLLOW_UP,
        /** Veriff link has been sent */
        VERIFICATION_SENT,
        /** Member has passed age verification */
        PASSED,
        /** Member has failed age verification */
        FAILED
    }

    public static InterviewState create(String channelId, String memberId, String memberName) {
        return new InterviewState(
                channelId,
                memberId,
                memberName,
                new ArrayList<>(),
                new MutableState(0, false, VerificationStage.AWAITING_ANSWERS)
        );
    }

    public InterviewState withStage(VerificationStage stage) {
        return new InterviewState(
                channelId, memberId, memberName, messageHistory,
                new MutableState(mutableState.followUpCount(), mutableState.veriffTriggered(), stage)
        );
    }

    public InterviewState withFollowUpIncremented() {
        return new InterviewState(
                channelId, memberId, memberName, messageHistory,
                new MutableState(mutableState.followUpCount() + 1, mutableState.veriffTriggered(), mutableState.stage())
        );
    }

    public InterviewState withVeriffTriggered() {
        return new InterviewState(
                channelId, memberId, memberName, messageHistory,
                new MutableState(mutableState.followUpCount(), true, VerificationStage.VERIFICATION_SENT)
        );
    }

    public void appendMessage(String authorName, String content) {
        messageHistory.add(authorName + ": " + content);
    }

    public String buildHistoryText() {
        return String.join("\n", messageHistory);
    }
}
