package kr.ac.kopo.ttd.controller;

import kr.ac.kopo.ttd.common.ApiResponse;
import kr.ac.kopo.ttd.dto.LeaderboardResponse;
import kr.ac.kopo.ttd.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping
    public ApiResponse<LeaderboardResponse> getLeaderboard(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long problemId,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(leaderboardService.getLeaderboard(problemId, userId, limit));
    }
}
