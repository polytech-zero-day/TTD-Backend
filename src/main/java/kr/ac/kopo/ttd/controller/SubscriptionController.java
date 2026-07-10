package kr.ac.kopo.ttd.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import kr.ac.kopo.ttd.common.ApiResponse;
import kr.ac.kopo.ttd.dto.SubscriptionResponse;
import kr.ac.kopo.ttd.dto.SubscriptionSubscribeRequest;
import kr.ac.kopo.ttd.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자용 월간 구독 API. 빌링키는 프론트에서 PortOne SDK로 발급받아 넘겨준다.
 */
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionResponse>> subscribe(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SubscriptionSubscribeRequest request) {
        SubscriptionResponse response = subscriptionService.subscribe(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @DeleteMapping
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal Long userId) {
        subscriptionService.cancel(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ApiResponse<SubscriptionResponse> getMySubscription(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(subscriptionService.getMySubscription(userId));
    }
}
