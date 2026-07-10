package kr.ac.kopo.ttd.domain;

import jakarta.persistence.*;
import kr.ac.kopo.ttd.common.constant.PaymentConstants;
import kr.ac.kopo.ttd.common.crypto.AesGcmConverter;
import kr.ac.kopo.ttd.common.exception.InvalidStatusTransitionException;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 월간 구독 1건. user는 JWT principal(Long userId)과의 대칭을 위해 FK 조인 없이 id만 보관한다
 * (Attempt.java와 동일 컨벤션). billingKey는 PortOne 발급 결제수단 토큰이라 PII급으로 취급해
 * AES-256-GCM 컨버터를 적용한다.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Convert(converter = AesGcmConverter.class)
    @Column(name = "billing_key_enc", nullable = false)
    private String billingKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "current_period_start", nullable = false)
    private LocalDateTime currentPeriodStart;

    @Column(name = "next_billing_at", nullable = false)
    private LocalDateTime nextBillingAt;

    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private int failedAttempts = 0;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 재결제 성공: 실패 카운트 리셋, 다음 결제일 갱신, PAST_DUE였다면 ACTIVE로 복귀 */
    public void recordSuccessfulCharge(LocalDateTime nextBillingAt) {
        if (status != SubscriptionStatus.ACTIVE) {
            changeStatus(SubscriptionStatus.ACTIVE);
        }
        this.failedAttempts = 0;
        this.currentPeriodStart = LocalDateTime.now();
        this.nextBillingAt = nextBillingAt;
    }

    /** 재결제 실패: 실패 카운트 누적, 임계치 초과 시 EXPIRED로 만료 처리 */
    public void recordFailedCharge() {
        this.failedAttempts++;
        SubscriptionStatus target = failedAttempts >= PaymentConstants.MAX_BILLING_RETRY
                ? SubscriptionStatus.EXPIRED
                : SubscriptionStatus.PAST_DUE;
        if (status != target) {
            changeStatus(target);
        }
    }

    public void cancel() {
        changeStatus(SubscriptionStatus.CANCELED);
        this.canceledAt = LocalDateTime.now();
    }

    private void changeStatus(SubscriptionStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException();
        }
        this.status = target;
    }
}
