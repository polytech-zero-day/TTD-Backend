package kr.ac.kopo.ttd.dto;

import jakarta.validation.constraints.NotNull;
import kr.ac.kopo.ttd.domain.ProblemStatus;

public record ProblemStatusUpdateRequest(
        @NotNull ProblemStatus status
) {
}
