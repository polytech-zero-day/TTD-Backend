package kr.ac.kopo.ttd.repository;

import kr.ac.kopo.ttd.domain.Problem;
import kr.ac.kopo.ttd.domain.ProblemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    List<Problem> findAllByStatus(ProblemStatus status);

    Optional<Problem> findByIdAndStatus(Long id, ProblemStatus status);

    boolean existsByTitle(String title);

    Optional<Problem> findByTitle(String title);
}
