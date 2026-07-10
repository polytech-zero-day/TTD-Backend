package kr.ac.kopo.ttd.dto;

import kr.ac.kopo.ttd.domain.Attempt;
import kr.ac.kopo.ttd.domain.AttemptMessage;
import kr.ac.kopo.ttd.domain.Problem;
import kr.ac.kopo.ttd.domain.RubricCriterion;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 채점 결과 리포트 응답. 결과 화면(S-05)이 이 응답 하나로 그려지도록
 * 점수·항목별 루브릭·프롬프트 이력·메타를 전부 담는다.
 */
public record AttemptResultResponse(
        Long attemptId, String status,
        String problemTitle, String difficulty,
        int attemptOrdinal, int maxAttempts,
        LocalDateTime submittedAt,
        Integer rubricScore, Integer efficiencyScore, Integer finalScore, String feedback,
        List<RubricCriterion> criteria,
        List<ChatMessageResponse> messages,
        long totalTokens, long tokenBudget) {

    public static AttemptResultResponse of(Attempt a, List<AttemptMessage> messages, int attemptOrdinal) {
        Problem problem = a.getProblem();
        return new AttemptResultResponse(
                a.getId(), a.getStatus().name(),
                problem.getTitle(), problem.getDifficulty().name(),
                attemptOrdinal, problem.getMaxAttempts(),
                a.getSubmittedAt(),
                a.getRubricScore(), a.getEfficiencyScore(), a.getFinalScore(), a.getFeedback(),
                a.getRubricDetail() == null ? List.of() : a.getRubricDetail(),
                messages.stream().map(ChatMessageResponse::from).toList(),
                a.getTotalTokens(), problem.getTokenBudget());
    }
}
