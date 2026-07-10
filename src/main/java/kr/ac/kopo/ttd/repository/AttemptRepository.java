package kr.ac.kopo.ttd.repository;

import kr.ac.kopo.ttd.domain.Attempt;
import kr.ac.kopo.ttd.domain.AttemptStatus;
import kr.ac.kopo.ttd.dto.LeaderboardRowRaw;
import kr.ac.kopo.ttd.dto.MyAttemptStatsResponse;
import kr.ac.kopo.ttd.dto.ScatterPointResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {
    Optional<Attempt> findByUserIdAndProblemIdAndStatus(Long userId, Long problemId, AttemptStatus status);
    long countByUserIdAndProblemId(Long userId, Long problemId);

    /** 결과 리포트의 응시 회차 계산용 — 해당 응시까지 포함한 누적 횟수. */
    int countByUserIdAndProblemIdAndIdLessThanEqual(Long userId, Long problemId, Long id);

    @Query("""
            select a from Attempt a
            join fetch a.problem
            where a.userId = :userId and a.status in :statuses
            order by coalesce(a.submittedAt, a.startedAt) desc
            """)
    List<Attempt> findMyAttempts(@Param("userId") Long userId,
                                 @Param("statuses") Collection<AttemptStatus> statuses);

    @Query("""
            select new kr.ac.kopo.ttd.dto.MyAttemptStatsResponse(
                count(a),
                avg(case when a.status = :graded then a.rubricScore end),
                avg(case when a.status = :graded then a.efficiencyScore end),
                max(case when a.status = :graded then a.rubricScore * 0.6 + a.efficiencyScore * 0.4 end),
                coalesce(sum(a.totalTokens), 0L),
                coalesce(100.0 * sum(case when a.status = :graded then 1 else 0 end) / nullif(count(a), 0L), 0.0)
            )
            from Attempt a
            where a.userId = :userId
            """)
    MyAttemptStatsResponse findMyStats(@Param("userId") Long userId,
                                       @Param("graded") AttemptStatus graded);

    @Query("""
            select new kr.ac.kopo.ttd.dto.ScatterPointResponse(a.rubricScore, a.efficiencyScore)
            from Attempt a
            where a.status = :graded
            """)
    List<ScatterPointResponse> findScatterData(@Param("graded") AttemptStatus graded);

    @Query("""
            select new kr.ac.kopo.ttd.dto.LeaderboardRowRaw(
                a.userId,
                a.problem.id,
                max(a.rubricScore),
                max(a.efficiencyScore),
                count(a),
                coalesce(sum(a.totalTokens), 0L)
            )
            from Attempt a
            where a.status = :graded
              and (:problemId is null or a.problem.id = :problemId)
            group by a.userId, a.problem.id
            """)
    List<LeaderboardRowRaw> findLeaderboardRaw(@Param("graded") AttemptStatus graded,
                                               @Param("problemId") Long problemId);
}