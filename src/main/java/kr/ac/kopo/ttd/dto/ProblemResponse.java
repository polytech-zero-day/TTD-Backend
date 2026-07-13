package kr.ac.kopo.ttd.dto;

import kr.ac.kopo.ttd.domain.Difficulty;
import kr.ac.kopo.ttd.domain.Problem;
import kr.ac.kopo.ttd.domain.ProblemType;
import kr.ac.kopo.ttd.domain.SourceType;

import java.util.List;

/**
 * 응시자 상세(S-03)용 응답. 스켈레톤형 문제만 {@code skeletonCode}에 값이 있고 그 외에는 null이다.
 */
public record ProblemResponse(
        Long id,
        String title,
        Difficulty difficulty,
        ProblemType type,
        int maxAttempts,
        SourceType sourceType,
        String description,
        List<String> requirements,
        List<String> constraints,
        String skeletonCode
) {

    public ProblemResponse {
        requirements = requirements == null ? null : List.copyOf(requirements);
        constraints = constraints == null ? null : List.copyOf(constraints);
    }

    public static ProblemResponse from(Problem problem) {
        return new ProblemResponse(
                problem.getId(),
                problem.getTitle(),
                problem.getDifficulty(),
                problem.getType(),
                problem.getMaxAttempts(),
                problem.getSourceType(),
                problem.getDescription(),
                problem.getRequirements(),
                problem.getConstraints(),
                problem.getSkeletonCode()
        );
    }
}
