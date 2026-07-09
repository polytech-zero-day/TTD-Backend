package kr.ac.kopo.ttd.dto;

import jakarta.validation.constraints.NotBlank;

public record AttemptMessageRequest(@NotBlank String content) {}