package kr.ac.kopo.ttd.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import kr.ac.kopo.ttd.common.ApiResponse;
import kr.ac.kopo.ttd.dto.ProblemResponse;
import kr.ac.kopo.ttd.dto.ProblemSummaryResponse;
import kr.ac.kopo.ttd.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 응시자용 문제 조회 API. status=active 문제만 노출한다.
 */
@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
@SecurityRequirement(name = "basicAuth")
public class ProblemController {

    private final ProblemService problemService;

    @GetMapping
    public ApiResponse<List<ProblemSummaryResponse>> getProblems() {
        return ApiResponse.success(problemService.getActiveProblems());
    }

    @GetMapping("/{id}")
    public ApiResponse<ProblemResponse> getProblem(@PathVariable Long id) {
        return ApiResponse.success(problemService.getActiveProblem(id));
    }
}
