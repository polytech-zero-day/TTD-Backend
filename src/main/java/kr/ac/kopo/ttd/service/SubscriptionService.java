package kr.ac.kopo.ttd.service;

import kr.ac.kopo.ttd.common.exception.PaymentFailedException;
import kr.ac.kopo.ttd.common.exception.SubscriptionAlreadyActiveException;
import kr.ac.kopo.ttd.common.exception.SubscriptionNotFoundException;
import kr.ac.kopo.ttd.domain.Payment;
import kr.ac.kopo.ttd.domain.Subscription;
import kr.ac.kopo.ttd.domain.SubscriptionStatus;
import kr.ac.kopo.ttd.dto.SubscriptionResponse;
import kr.ac.kopo.ttd.dto.SubscriptionSubscribeRequest;
import kr.ac.kopo.ttd.payment.PortOneClient;
import kr.ac.kopo.ttd.payment.PortOnePaymentResult;
import kr.ac.kopo.ttd.repository.PaymentRepository;
import kr.ac.kopo.ttd.repository.SubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class SubscriptionService {

    private static final List<SubscriptionStatus> OWNED_ACTIVE_STATUSES =
            List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final PortOneClient portOneClient;
    private final long monthlyPriceKrw;
    private final String orderName;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            PaymentRepository paymentRepository,
            PortOneClient portOneClient,
            @Value("${app.payment.monthly-price-krw}") long monthlyPriceKrw,
            @Value("${app.payment.order-name}") String orderName) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
        this.portOneClient = portOneClient;
        this.monthlyPriceKrw = monthlyPriceKrw;
        this.orderName = orderName;
    }

    /**
     * 빌링키 등록(프론트에서 PortOne SDK로 이미 발급받은 값) + 첫 결제.
     * noRollbackFor: 결제 실패 시 markFailed·cancel로 남긴 원장이 예외 롤백으로 사라지면 안 된다
     * (특히 승인-후-응답유실 시 실패 기록마저 없으면 대조 불가). 실패해도 커밋해 흔적을 남긴다.
     */
    @Transactional(noRollbackFor = PaymentFailedException.class)
    public SubscriptionResponse subscribe(Long userId, SubscriptionSubscribeRequest request) {
        if (subscriptionRepository.findByUserIdAndStatusIn(userId, OWNED_ACTIVE_STATUSES).isPresent()) {
            throw new SubscriptionAlreadyActiveException();
        }

        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .billingKey(request.billingKey())
                .currentPeriodStart(now)
                .nextBillingAt(now.plusMonths(1))
                .build());

        String paymentId = newPaymentId("sub", subscription.getId());
        Payment payment = paymentRepository.save(Payment.builder()
                .userId(userId)
                .subscriptionId(subscription.getId())
                .portonePaymentId(paymentId)
                .amount(monthlyPriceKrw)
                .build());

        PortOnePaymentResult result = portOneClient.payWithBillingKey(
                paymentId, request.billingKey(), monthlyPriceKrw, orderName);
        if (!result.success()) {
            payment.markFailed(result.failReason());
            subscription.cancel();
            throw new PaymentFailedException();
        }
        payment.markPaid(result.paidAt());
        return SubscriptionResponse.from(subscription);
    }

    /** 구독 취소. PortOne 빌링키 폐기는 best-effort(실패해도 로컬 취소는 유지). */
    @Transactional
    public void cancel(Long userId) {
        Subscription subscription = findOwned(userId);
        subscription.cancel();
        portOneClient.deleteBillingKey(subscription.getBillingKey());
    }

    public SubscriptionResponse getMySubscription(Long userId) {
        return SubscriptionResponse.from(findOwned(userId));
    }

    /** 재결제 스케줄러가 오늘 청구 대상인 구독 id 목록을 조회할 때 사용한다. */
    public List<Long> findDueSubscriptionIds(LocalDateTime now) {
        return subscriptionRepository.findByStatusInAndNextBillingAtLessThanEqual(OWNED_ACTIVE_STATUSES, now)
                .stream().map(Subscription::getId).toList();
    }

    /** 구독 1건 재결제. 스케줄러가 건별로 호출한다(자기 자신 호출로 인한 트랜잭션 프록시 우회 방지). */
    @Transactional
    public void chargeSingleSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(SubscriptionNotFoundException::new);

        // due 목록 조회와 실제 청구 사이에 취소/만료됐을 수 있다. 재확인 없이 청구하면
        // 취소된 구독에 돈이 나가고, 이후 CANCELED→ACTIVE 전환 예외로 결제 원장까지 롤백된다.
        if (!OWNED_ACTIVE_STATUSES.contains(subscription.getStatus())) {
            log.info("재결제 대상 상태가 아니라 스킵: subscriptionId={}, status={}",
                    subscriptionId, subscription.getStatus());
            return;
        }

        String paymentId = newPaymentId("resub", subscription.getId());
        Payment payment = paymentRepository.save(Payment.builder()
                .userId(subscription.getUserId())
                .subscriptionId(subscription.getId())
                .portonePaymentId(paymentId)
                .amount(monthlyPriceKrw)
                .build());

        PortOnePaymentResult result = portOneClient.payWithBillingKey(
                paymentId, subscription.getBillingKey(), monthlyPriceKrw, orderName);
        if (result.success()) {
            payment.markPaid(result.paidAt());
            subscription.recordSuccessfulCharge(LocalDateTime.now().plusMonths(1));
        } else {
            payment.markFailed(result.failReason());
            subscription.recordFailedCharge();
        }
    }

    private Subscription findOwned(Long userId) {
        return subscriptionRepository.findByUserIdAndStatusIn(userId, OWNED_ACTIVE_STATUSES)
                .orElseThrow(SubscriptionNotFoundException::new);
    }

    private String newPaymentId(String prefix, Long subscriptionId) {
        return prefix + "-" + subscriptionId + "-" + UUID.randomUUID();
    }
}
