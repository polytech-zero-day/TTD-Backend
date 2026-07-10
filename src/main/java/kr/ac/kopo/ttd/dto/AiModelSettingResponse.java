package kr.ac.kopo.ttd.dto;

import kr.ac.kopo.ttd.domain.AiPurpose;

import java.time.LocalDateTime;

/**
 * 용도별 현재 모델 설정. model은 DB 설정값 또는 미설정 시 기본 모델(effective)이며,
 * fromDefault=true면 아직 관리자가 지정하지 않아 yaml 기본값을 쓰는 상태임을 뜻한다.
 */
public record AiModelSettingResponse(
        AiPurpose purpose,
        String label,
        String model,
        boolean fromDefault,
        Long updatedBy,
        LocalDateTime updatedAt) {
}
