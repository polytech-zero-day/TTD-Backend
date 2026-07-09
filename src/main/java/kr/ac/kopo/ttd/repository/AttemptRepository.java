package kr.ac.kopo.ttd.repository;

import kr.ac.kopo.ttd.domain.Attempt;
import kr.ac.kopo.ttd.domain.AttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {
    Optional<Attempt> findByUserIdAndProblemIdAndStatus(Long userId, Long problemId, AttemptStatus status);
    long countByUserIdAndProblemId(Long userId, Long problemId);
}