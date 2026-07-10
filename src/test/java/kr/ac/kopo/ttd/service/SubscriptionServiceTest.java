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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final Long USER_ID = 1L;
    private static final long MONTHLY_PRICE_KRW = 9900L;
    private static final String ORDER_NAME = "TTD 월간 구독";

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PortOneClient portOneClient;

    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(
                subscriptionRepository, paymentRepository, portOneClient, MONTHLY_PRICE_KRW, ORDER_NAME);
    }

    private void stubSaves() {
        given(subscriptionRepository.save(any(Subscription.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    private Subscription activeSubscription() {
        return Subscription.builder()
                .userId(USER_ID)
                .billingKey("billing-key-1")
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .nextBillingAt(LocalDateTime.now())
                .build();
    }

    // ── 구독 등록 ──────────────────────────────────────────

    @Test
    void 구독을_등록하면_첫_결제_후_ACTIVE_상태가_된다() {
        stubSaves();
        given(subscriptionRepository.findByUserIdAndStatusIn(eq(USER_ID), any())).willReturn(Optional.empty());
        given(portOneClient.payWithBillingKey(anyString(), anyString(), anyLong(), anyString()))
                .willReturn(PortOnePaymentResult.success(LocalDateTime.now()));

        SubscriptionResponse response = subscriptionService.subscribe(
                USER_ID, new SubscriptionSubscribeRequest("billing-key-1"));

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void 이미_활성_구독이_있으면_등록할_수_없다() {
        given(subscriptionRepository.findByUserIdAndStatusIn(eq(USER_ID), any()))
                .willReturn(Optional.of(activeSubscription()));

        assertThatThrownBy(() -> subscriptionService.subscribe(USER_ID, new SubscriptionSubscribeRequest("billing-key-1")))
                .isInstanceOf(SubscriptionAlreadyActiveException.class);

        verify(portOneClient, never()).payWithBillingKey(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void 첫_결제가_실패하면_구독이_생성되지_않고_예외가_발생한다() {
        stubSaves();
        given(subscriptionRepository.findByUserIdAndStatusIn(eq(USER_ID), any())).willReturn(Optional.empty());
        given(portOneClient.payWithBillingKey(anyString(), anyString(), anyLong(), anyString()))
                .willReturn(PortOnePaymentResult.failed("CARD_DECLINED"));

        assertThatThrownBy(() -> subscriptionService.subscribe(USER_ID, new SubscriptionSubscribeRequest("billing-key-1")))
                .isInstanceOf(PaymentFailedException.class);
    }

    // ── 취소 ──────────────────────────────────────────────

    @Test
    void 구독을_취소하면_PortOne_빌링키도_폐기한다() {
        Subscription subscription = activeSubscription();
        given(subscriptionRepository.findByUserIdAndStatusIn(eq(USER_ID), any())).willReturn(Optional.of(subscription));

        subscriptionService.cancel(USER_ID);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        verify(portOneClient).deleteBillingKey("billing-key-1");
    }

    @Test
    void 구독이_없으면_취소할_수_없다() {
        given(subscriptionRepository.findByUserIdAndStatusIn(eq(USER_ID), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.cancel(USER_ID))
                .isInstanceOf(SubscriptionNotFoundException.class);
    }

    // ── 재결제 스케줄러 ────────────────────────────────────

    @Test
    void 재결제에_성공하면_다음_결제일이_연장되고_실패_카운트가_초기화된다() {
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
        Subscription subscription = activeSubscription();
        given(subscriptionRepository.findById(1L)).willReturn(Optional.of(subscription));
        given(portOneClient.payWithBillingKey(anyString(), anyString(), anyLong(), anyString()))
                .willReturn(PortOnePaymentResult.success(LocalDateTime.now()));

        subscriptionService.chargeSingleSubscription(1L);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getFailedAttempts()).isZero();
        assertThat(subscription.getNextBillingAt()).isAfter(LocalDateTime.now().plusDays(20));
    }

    @Test
    void 재결제가_3회_연속_실패하면_구독이_만료된다() {
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
        Subscription subscription = activeSubscription();
        given(subscriptionRepository.findById(1L)).willReturn(Optional.of(subscription));
        given(portOneClient.payWithBillingKey(anyString(), anyString(), anyLong(), anyString()))
                .willReturn(PortOnePaymentResult.failed("CARD_DECLINED"));

        subscriptionService.chargeSingleSubscription(1L);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        subscriptionService.chargeSingleSubscription(1L);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        subscriptionService.chargeSingleSubscription(1L);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
    }
}
