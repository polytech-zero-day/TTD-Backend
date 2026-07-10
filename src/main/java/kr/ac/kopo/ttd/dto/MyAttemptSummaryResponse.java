package kr.ac.kopo.ttd.dto;

import kr.ac.kopo.ttd.domain.Attempt;
import kr.ac.kopo.ttd.domain.Difficulty;

import java.time.LocalDateTime;

public record MyAttemptSummaryResponse(
        Long attemptId,
        Long problemId,
        String problemTitle,
        Difficulty difficulty,
        String status,
        Integer rubricScore,
        Integer efficiencyScore,
        LocalDateTime submittedAt
) {
    public static MyAttemptSummaryResponse from(Attempt a) {
        return new MyAttemptSummaryResponse(
                a.getId(),
                a.getProblem().getId(),
                a.getProblem().getTitle(),
                a.getProblem().getDifficulty(),
                a.getStatus().name(),
                a.getRubricScore(),
                a.getEfficiencyScore(),
                a.getSubmittedAt()
        );
    }
}
