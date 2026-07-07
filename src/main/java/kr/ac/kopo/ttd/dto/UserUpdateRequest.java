package kr.ac.kopo.ttd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.ac.kopo.ttd.domain.UserRole;

public record UserUpdateRequest(
        @NotBlank @Size(max = 30) String nickname,
        @NotNull UserRole role
) {
}
