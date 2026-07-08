package kr.ac.kopo.ttd.repository;

import kr.ac.kopo.ttd.domain.CalibrationSample;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalibrationSampleRepository extends JpaRepository<CalibrationSample, Long> {

    List<CalibrationSample> findByProblemId(Long problemId);

    long countByProblemId(Long problemId);
}
