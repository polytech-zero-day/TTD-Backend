package kr.ac.kopo.ttd.dto;

import kr.ac.kopo.ttd.domain.Attempt;

public record AttemptUsageResponse(
        int messagesUsed, int messagesLimit, long tokensUsed, long tokensBaseline) {

    public static AttemptUsageResponse of(Attempt attempt, int messageLimit, long tokenBaseline) {
        return new AttemptUsageResponse(
                attempt.getMessageCount(), messageLimit, attempt.getTotalTokens(), tokenBaseline);
    }
}