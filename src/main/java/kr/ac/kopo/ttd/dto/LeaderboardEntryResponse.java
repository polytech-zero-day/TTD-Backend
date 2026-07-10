package kr.ac.kopo.ttd.dto;

public record LeaderboardEntryResponse(
        int rank,
        String name,
        int quality,
        int efficiency,
        double attempts,
        long tokens,
        int total,
        boolean isCurrentUser
) {}