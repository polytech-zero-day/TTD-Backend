package kr.ac.kopo.ttd.repository;

import kr.ac.kopo.ttd.domain.AiModelSetting;
import kr.ac.kopo.ttd.domain.AiPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiModelSettingRepository extends JpaRepository<AiModelSetting, Long> {

    Optional<AiModelSetting> findByPurpose(AiPurpose purpose);
}
