package kr.ac.kopo.ttd.dto;

public record LeaderboardStatsResponse(
        Double avgTopAttempts,
        Long avgTopTokens,
        Integer myRank,
        Integer myPercentile
) {}