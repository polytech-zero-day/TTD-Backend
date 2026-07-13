package kr.ac.kopo.ttd.dto;

import java.util.List;

public record LeaderboardResponse(
        List<LeaderboardEntryResponse> rows,
        LeaderboardStatsResponse stats
) {

    public LeaderboardResponse {
        rows = rows == null ? null : List.copyOf(rows);
    }
}
