package kr.ac.kopo.ttd.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 결제 원장 1건(구독 등록 시 첫 결제 또는 재결제 시도마다 1행). portonePaymentId가 PortOne 쪽
 * 결제 건과의 멱등성 키로, 웹훅 재전송과 동시성 처리 시 이 값으로 중복 반영을 막는다.
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "portone_payment_id", nullable = false, unique = true)
    private String portonePaymentId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "fail_reason")
    private String failReason;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** 결제 확정(성공). 이미 종결(PAID/FAILED) 상태면 웹훅 재전송으로 보고 조용히 무시한다. */
    public void markPaid(LocalDateTime paidAt) {
        if (status.isTerminal()) {
            return;
        }
        this.status = PaymentStatus.PAID;
        this.paidAt = paidAt;
    }

    /** 결제 실패 확정. 이미 종결 상태면 웹훅 재전송으로 보고 조용히 무시한다. */
    public void markFailed(String reason) {
        if (status.isTerminal()) {
            return;
        }
        this.status = PaymentStatus.FAILED;
        this.failReason = reason;
    }
}
