package kr.ac.kopo.ttd.dto;

import java.util.List;

/**
 * AI 모델 설정 화면용 응답. 용도별 현재 설정(settings)과 선택 가능한 모델 목록(availableModels)을
 * 함께 내려, 프론트가 별도 호출 없이 셀렉트를 구성할 수 있게 한다.
 */
public record AiModelSettingsResponse(
        List<AiModelSettingResponse> settings,
        List<String> availableModels) {
}
