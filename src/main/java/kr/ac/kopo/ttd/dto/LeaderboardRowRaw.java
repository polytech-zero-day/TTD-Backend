package kr.ac.kopo.ttd.dto;

public record LeaderboardRowRaw(
        Long userId,
        Long problemId,
        Integer bestRubric,
        Integer bestEfficiency,
        Long attemptCount,
        Long tokenSum
) {}
