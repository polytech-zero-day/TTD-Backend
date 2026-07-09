package kr.ac.kopo.ttd.dto;

import jakarta.validation.constraints.NotNull;

public record AttemptStartRequest(@NotNull Long problemId) {}