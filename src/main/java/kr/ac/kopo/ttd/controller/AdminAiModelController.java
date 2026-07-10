package kr.ac.kopo.ttd.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import kr.ac.kopo.ttd.common.ApiResponse;
import kr.ac.kopo.ttd.domain.AiPurpose;
import kr.ac.kopo.ttd.dto.AiModelSettingResponse;
import kr.ac.kopo.ttd.dto.AiModelSettingsResponse;
import kr.ac.kopo.ttd.dto.AiModelUpdateRequest;
import kr.ac.kopo.ttd.service.AiModelSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 용도별 AI 모델 설정(관리자 전용). PUT은 재시작 없이 즉시 적용되며, 이후 캘리브레이션을
 * 재실행해 채점 품질 회귀를 확인하는 운영 흐름을 지원한다.
 */
@RestController
@RequestMapping("/api/admin/settings/ai-models")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AdminAiModelController {

    private final AiModelSettingService aiModelSettingService;

    @GetMapping
    public ApiResponse<AiModelSettingsResponse> getSettings() {
        return ApiResponse.success(aiModelSettingService.getSettings());
    }

    @PutMapping("/{purpose}")
    public ApiResponse<AiModelSettingResponse> updateModel(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable AiPurpose purpose,
            @Valid @RequestBody AiModelUpdateRequest request) {
        return ApiResponse.success(
                aiModelSettingService.updateModel(purpose, request.model(), adminUserId));
    }
}
