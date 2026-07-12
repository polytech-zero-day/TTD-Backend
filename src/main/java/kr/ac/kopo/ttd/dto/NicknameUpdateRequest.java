package kr.ac.kopo.ttd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NicknameUpdateRequest(
        @NotBlank @Size(min = 1, max = 30) String nickname
) {
}
