package kr.ac.kopo.ttd.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import kr.ac.kopo.ttd.common.ApiResponse;
import kr.ac.kopo.ttd.dto.AdminProblemResponse;
import kr.ac.kopo.ttd.dto.ProblemCreateRequest;
import kr.ac.kopo.ttd.dto.ProblemStatusUpdateRequest;
import kr.ac.kopo.ttd.dto.ProblemUpdateRequest;
import kr.ac.kopo.ttd.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자용 문제 관리 API. 전체 조회 및 생성/수정/삭제/상태 전환을 담당한다.
 */
@RestController
@RequestMapping("/api/admin/problems")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "basicAuth")
public class AdminProblemController {

    private final ProblemService problemService;

    @GetMapping
    public ApiResponse<List<AdminProblemResponse>> getProblems() {
        return ApiResponse.success(problemService.getAllProblems());
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminProblemResponse> getProblem(@PathVariable Long id) {
        return ApiResponse.success(problemService.getProblem(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminProblemResponse>> createProblem(@Valid @RequestBody ProblemCreateRequest request) {
        AdminProblemResponse response = problemService.createProblem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminProblemResponse> updateProblem(@PathVariable Long id, @Valid @RequestBody ProblemUpdateRequest request) {
        return ApiResponse.success(problemService.updateProblem(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProblem(@PathVariable Long id) {
        problemService.deleteProblem(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminProblemResponse> changeStatus(@PathVariable Long id, @Valid @RequestBody ProblemStatusUpdateRequest request) {
        return ApiResponse.success(problemService.changeStatus(id, request));
    }
}
