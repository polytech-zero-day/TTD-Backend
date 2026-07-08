package kr.ac.kopo.ttd.repository;

import kr.ac.kopo.ttd.domain.ProblemSet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemSetRepository extends JpaRepository<ProblemSet, Long> {

    boolean existsByName(String name);
}
