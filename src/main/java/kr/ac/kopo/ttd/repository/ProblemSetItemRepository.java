package kr.ac.kopo.ttd.repository;

import kr.ac.kopo.ttd.domain.ProblemSetItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemSetItemRepository extends JpaRepository<ProblemSetItem, Long> {

    List<ProblemSetItem> findByProblemSetIdOrderByDisplayOrderAsc(Long problemSetId);
}
