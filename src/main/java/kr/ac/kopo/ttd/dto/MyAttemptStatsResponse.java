package kr.ac.kopo.ttd.dto;

public record MyAttemptStatsResponse(
        Long totalAttempts,
        Double avgQualityScore,
        Double avgEfficiencyScore,
        Double bestScore,
        Long totalTokens,
        Double completionRate
) {}
