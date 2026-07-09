package kr.ac.kopo.ttd.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import kr.ac.kopo.ttd.common.ApiResponse;
import kr.ac.kopo.ttd.dto.*;
import kr.ac.kopo.ttd.service.AttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attempts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AttemptController {

    private final AttemptService attemptService;

    @PostMapping
    public ResponseEntity<ApiResponse<AttemptSnapshotResponse>> start(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AttemptStartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(attemptService.start(userId, request)));
    }

    @GetMapping("/current")
    public ApiResponse<AttemptSnapshotResponse> getCurrent(
            @AuthenticationPrincipal Long userId, @RequestParam Long problemId) {
        return ApiResponse.success(attemptService.getCurrent(userId, problemId));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<AttemptMessageResponse> sendMessage(
            @AuthenticationPrincipal Long userId, @PathVariable Long id,
            @Valid @RequestBody AttemptMessageRequest request) {
        return ApiResponse.success(attemptService.sendMessage(userId, id, request));
    }

    @PutMapping("/{id}/draft")
    public ResponseEntity<Void> updateDraft(
            @AuthenticationPrincipal Long userId, @PathVariable Long id,
            @RequestBody DraftUpdateRequest request) {
        attemptService.updateDraft(userId, id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<AttemptResultResponse> submit(
            @AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.success(attemptService.submit(userId, id));
    }

    @GetMapping("/{id}/result")
    public ApiResponse<AttemptResultResponse> getResult(
            @AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.success(attemptService.getResult(userId, id));
    }
}