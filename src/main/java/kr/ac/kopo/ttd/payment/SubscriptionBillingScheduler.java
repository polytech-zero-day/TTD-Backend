package kr.ac.kopo.ttd.payment;

import kr.ac.kopo.ttd.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 매일 정해진 시각에 다음 결제일(nextBillingAt)이 도래한 구독을 저장된 빌링키로 재결제한다.
 * 구독별로 SubscriptionService의 별도 트랜잭션 메서드를 호출해, 한 구독의 결제 실패가
 * 다른 구독 처리를 막지 않도록 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionBillingScheduler {

    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "${app.portone.billing-cron}")
    public void chargeDueSubscriptions() {
        List<Long> dueSubscriptionIds = subscriptionService.findDueSubscriptionIds(LocalDateTime.now());
        log.info("구독 재결제 배치 시작: 대상 {}건", dueSubscriptionIds.size());
        for (Long subscriptionId : dueSubscriptionIds) {
            try {
                subscriptionService.chargeSingleSubscription(subscriptionId);
            } catch (Exception e) {
                log.error("구독 재결제 처리 중 오류: subscriptionId={}", subscriptionId, e);
            }
        }
    }
}
