package kr.ac.kopo.ttd.repository;

import kr.ac.kopo.ttd.domain.AttemptMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttemptMessageRepository extends JpaRepository<AttemptMessage, Long> {
    List<AttemptMessage> findByAttemptIdOrderByIdAsc(Long attemptId);
}