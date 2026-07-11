package kr.ac.kopo.ttd.service;

import kr.ac.kopo.ttd.common.constant.ScoreWeights;
import kr.ac.kopo.ttd.domain.AttemptStatus;
import kr.ac.kopo.ttd.domain.User;
import kr.ac.kopo.ttd.dto.LeaderboardEntryResponse;
import kr.ac.kopo.ttd.dto.LeaderboardResponse;
import kr.ac.kopo.ttd.dto.LeaderboardRowRaw;
import kr.ac.kopo.ttd.dto.LeaderboardStatsResponse;
import kr.ac.kopo.ttd.repository.AttemptRepository;
import kr.ac.kopo.ttd.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaderboardService {

    private final AttemptRepository attemptRepository;
    private final UserRepository userRepository;

    public LeaderboardResponse getLeaderboard(Long problemId, Long meUserId, Integer limit) {
        List<LeaderboardRowRaw> raw = attemptRepository.findLeaderboardRaw(AttemptStatus.GRADED, problemId);
        boolean isProblemFilter = problemId != null;

        Map<Long, List<LeaderboardRowRaw>> byUser = raw.stream()
                .collect(Collectors.groupingBy(LeaderboardRowRaw::userId));

        List<UserAggregate> aggregates = new ArrayList<>();
        for (var entry : byUser.entrySet()) {
            Long uid = entry.getKey();
            List<LeaderboardRowRaw> rows = entry.getValue();

            double avgQuality = rows.stream().mapToInt(LeaderboardRowRaw::bestRubric).average().orElse(0);
            double avgEfficiency = rows.stream().mapToInt(LeaderboardRowRaw::bestEfficiency).average().orElse(0);
            long totalAttempts = rows.stream().mapToLong(LeaderboardRowRaw::attemptCount).sum();
            long totalTokens = rows.stream().mapToLong(LeaderboardRowRaw::tokenSum).sum();
            int distinctProblems = rows.size();

            double attempts = isProblemFilter ? totalAttempts : (double) totalAttempts / distinctProblems;
            double tokens = isProblemFilter ? totalTokens : (double) totalTokens / distinctProblems;
            double total = avgQuality * ScoreWeights.RUBRIC + avgEfficiency * ScoreWeights.EFFICIENCY;

            aggregates.add(new UserAggregate(uid, avgQuality, avgEfficiency, attempts, tokens, total));
        }

        aggregates.sort(Comparator.comparingDouble(UserAggregate::total).reversed());

        Set<Long> userIds = aggregates.stream()
                .map(UserAggregate::userId)
                .collect(Collectors.toSet());
        Map<Long, String> nickMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        int myIndex = -1;
        for (int i = 0; i < aggregates.size(); i++) {
            if (aggregates.get(i).userId().equals(meUserId)) {
                myIndex = i;
                break;
            }
        }
        Integer myRank = myIndex >= 0 ? myIndex + 1 : null;
        Integer myPercentile = myIndex >= 0
                ? (int) Math.ceil(100.0 * (myIndex + 1) / aggregates.size())
                : null;

        int topN = Math.min(10, aggregates.size());
        Double avgTopAttempts = null;
        Long avgTopTokens = null;
        if (topN > 0) {
            List<UserAggregate> top = aggregates.subList(0, topN);
            avgTopAttempts = round1(top.stream().mapToDouble(UserAggregate::attempts).average().orElse(0));
            avgTopTokens = Math.round(top.stream().mapToDouble(UserAggregate::tokens).average().orElse(0));
        }

        List<UserAggregate> ranked = aggregates;
        if (limit != null && limit > 0 && ranked.size() > limit) {
            ranked = ranked.subList(0, limit);
        }

        List<LeaderboardEntryResponse> entries = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            UserAggregate agg = ranked.get(i);
            entries.add(new LeaderboardEntryResponse(
                    i + 1,
                    nickMap.getOrDefault(agg.userId(), "Unknown"),
                    (int) Math.round(agg.quality()),
                    (int) Math.round(agg.efficiency()),
                    round1(agg.attempts()),
                    Math.round(agg.tokens()),
                    (int) Math.round(agg.total()),
                    agg.userId().equals(meUserId)
            ));
        }

        return new LeaderboardResponse(
                entries,
                new LeaderboardStatsResponse(avgTopAttempts, avgTopTokens, myRank, myPercentile)
        );
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private record UserAggregate(
            Long userId,
            double quality,
            double efficiency,
            double attempts,
            double tokens,
            double total
    ) {}
}
