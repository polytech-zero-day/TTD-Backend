package kr.ac.kopo.ttd.dto;

import kr.ac.kopo.ttd.domain.Difficulty;
import kr.ac.kopo.ttd.domain.Problem;
import kr.ac.kopo.ttd.domain.ProblemStatus;
import kr.ac.kopo.ttd.domain.ProblemType;
import kr.ac.kopo.ttd.domain.SourceType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자용 응답. 응시자 상세 필드에 더해 상태·생성/수정 시각을 포함한다.
 */
public record AdminProblemResponse(
        Long id,
        String title,
        Difficulty difficulty,
        ProblemType type,
        int maxAttempts,
        SourceType sourceType,
        String description,
        List<String> requirements,
        List<String> constraints,
        String skeletonCode,
        ProblemStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public AdminProblemResponse {
        requirements = requirements == null ? null : List.copyOf(requirements);
        constraints = constraints == null ? null : List.copyOf(constraints);
    }

    public static AdminProblemResponse from(Problem problem) {
        return new AdminProblemResponse(
                problem.getId(),
                problem.getTitle(),
                problem.getDifficulty(),
                problem.getType(),
                problem.getMaxAttempts(),
                problem.getSourceType(),
                problem.getDescription(),
                problem.getRequirements(),
                problem.getConstraints(),
                problem.getSkeletonCode(),
                problem.getStatus(),
                problem.getCreatedAt(),
                problem.getUpdatedAt()
        );
    }
}
