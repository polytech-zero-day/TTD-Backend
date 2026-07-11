package kr.ac.kopo.ttd.dto;

import kr.ac.kopo.ttd.domain.Attempt;

public record AttemptUsageResponse(
        int messagesUsed, int messagesLimit, long tokensUsed, long tokensBaseline, boolean unlimited) {

    // unlimited=true(유료)면 프론트는 messagesLimit 대신 "무제한"으로 표시한다.
    public static AttemptUsageResponse of(Attempt attempt, int messageLimit, long tokenBaseline, boolean unlimited) {
        return new AttemptUsageResponse(
                attempt.getMessageCount(), messageLimit, attempt.getTotalTokens(), tokenBaseline, unlimited);
    }
}