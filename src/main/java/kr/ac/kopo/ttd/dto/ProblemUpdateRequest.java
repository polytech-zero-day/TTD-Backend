package kr.ac.kopo.ttd.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.ac.kopo.ttd.domain.Difficulty;
import kr.ac.kopo.ttd.domain.ProblemType;
import kr.ac.kopo.ttd.domain.SourceType;

import java.util.List;

public record ProblemUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull Difficulty difficulty,
        @NotNull ProblemType type,
        @NotNull SourceType sourceType,
        @NotBlank String description,
        @NotEmpty List<@NotBlank String> requirements,
        @NotEmpty List<@NotBlank String> constraints,
        String skeletonCode,
        @NotNull @Min(1) Integer maxAttempts
) {
}
