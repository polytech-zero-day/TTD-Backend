package kr.ac.kopo.ttd.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import kr.ac.kopo.ttd.common.ApiResponse;
import kr.ac.kopo.ttd.dto.CalibrationRunResponse;
import kr.ac.kopo.ttd.grading.CalibrationRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 캘리브레이션 일치율 측정(관리자 전용). 샘플 30건 × LLM 채점이라 수 분이 걸릴 수 있고
 * 실제 비용이 발생하므로 POST로 명시적으로만 실행한다. 밴드/오차 파라미터로 재실험 가능.
 */
@RestController
@RequestMapping("/api/admin/calibration")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AdminCalibrationController {

    private final CalibrationRunner calibrationRunner;

    @PostMapping("/run")
    public ApiResponse<CalibrationRunResponse> run(
            @RequestParam(defaultValue = "70") int highMin,
            @RequestParam(defaultValue = "40") int midMin,
            @RequestParam(defaultValue = "15") int tolerance) {
        return ApiResponse.success(calibrationRunner.run(highMin, midMin, tolerance));
    }
}
