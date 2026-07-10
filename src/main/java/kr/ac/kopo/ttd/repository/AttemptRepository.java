package kr.ac.kopo.ttd.repository;

import kr.ac.kopo.ttd.domain.Attempt;
import kr.ac.kopo.ttd.domain.AttemptStatus;
import kr.ac.kopo.ttd.dto.MyAttemptStatsResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {
    Optional<Attempt> findByUserIdAndProblemIdAndStatus(Long userId, Long problemId, AttemptStatus status);
    long countByUserIdAndProblemId(Long userId, Long problemId);

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
}