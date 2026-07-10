package kr.ac.kopo.ttd.service;

import kr.ac.kopo.ttd.common.exception.BusinessException;
import kr.ac.kopo.ttd.common.exception.ErrorCode;
import kr.ac.kopo.ttd.domain.AiModelSetting;
import kr.ac.kopo.ttd.domain.AiPurpose;
import kr.ac.kopo.ttd.dto.AiModelSettingResponse;
import kr.ac.kopo.ttd.dto.AiModelSettingsResponse;
import kr.ac.kopo.ttd.repository.AiModelSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * 용도별 AI 모델 설정의 단일 창구. AiClient가 매 호출 시 modelFor()로 현재 모델을 조회하므로
 * 관리자가 변경하면 재시작 없이 즉시 반영된다. 미설정 용도는 yaml 기본 모델로 폴백한다.
 */
@Service
@Transactional(readOnly = true)
public class AiModelSettingService {

    private final AiModelSettingRepository repository;
    private final String defaultModel;
    private final List<String> availableModels;

    public AiModelSettingService(
            AiModelSettingRepository repository,
            @Value("${spring.ai.openai.chat.options.model}") String defaultModel,
            @Value("${app.ai.available-models}") String availableModelsCsv) {
        this.repository = repository;
        this.defaultModel = defaultModel;
        this.availableModels = Arrays.stream(availableModelsCsv.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    /** 해당 용도의 현재 모델(설정값 또는 기본값)을 반환한다. AiClient 호출 경로에서 쓴다. */
    public String modelFor(AiPurpose purpose) {
        return repository.findByPurpose(purpose)
                .map(AiModelSetting::getModel)
                .orElse(defaultModel);
    }

    /** 관리 화면용 — 전체 용도의 현재 설정과 선택 가능한 모델 목록. */
    public AiModelSettingsResponse getSettings() {
        List<AiModelSettingResponse> settings = Arrays.stream(AiPurpose.values())
                .map(this::toResponse)
                .toList();
        return new AiModelSettingsResponse(settings, availableModels);
    }

    @Transactional
    public AiModelSettingResponse updateModel(AiPurpose purpose, String model, Long adminUserId) {
        if (!availableModels.contains(model)) {
            throw new BusinessException(ErrorCode.INVALID_AI_MODEL);
        }
        AiModelSetting setting = repository.findByPurpose(purpose)
                .map(existing -> {
                    existing.changeModel(model, adminUserId);
                    return existing;
                })
                .orElseGet(() -> repository.save(AiModelSetting.builder()
                        .purpose(purpose).model(model).updatedBy(adminUserId).build()));
        return toResponse(setting);
    }

    private AiModelSettingResponse toResponse(AiPurpose purpose) {
        return repository.findByPurpose(purpose)
                .map(this::toResponse)
                .orElseGet(() -> new AiModelSettingResponse(
                        purpose, purpose.getLabel(), defaultModel, true, null, null));
    }

    private AiModelSettingResponse toResponse(AiModelSetting setting) {
        return new AiModelSettingResponse(
                setting.getPurpose(), setting.getPurpose().getLabel(),
                setting.getModel(), false, setting.getUpdatedBy(), setting.getUpdatedAt());
    }
}
