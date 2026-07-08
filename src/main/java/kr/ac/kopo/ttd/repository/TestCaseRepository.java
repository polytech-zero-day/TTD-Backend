package kr.ac.kopo.ttd.repository;

import kr.ac.kopo.ttd.domain.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByProblemId(Long problemId);

    boolean existsByProblemId(Long problemId);
}
