package kr.ac.kopo.ttd.dto;

import jakarta.validation.constraints.NotBlank;

public record AiModelUpdateRequest(@NotBlank String model) {
}
