package kr.ac.kopo.ttd.dto;

import kr.ac.kopo.ttd.domain.Difficulty;
import kr.ac.kopo.ttd.domain.Problem;
import kr.ac.kopo.ttd.domain.ProblemType;

public record ProblemSummaryResponse(
        Long id,
        String title,
        Difficulty difficulty,
        ProblemType type,
        int maxAttempts
) {

    public static ProblemSummaryResponse from(Problem problem) {
        return new ProblemSummaryResponse(
                problem.getId(),
                problem.getTitle(),
                problem.getDifficulty(),
                problem.getType(),
                problem.getMaxAttempts()
        );
    }
}
