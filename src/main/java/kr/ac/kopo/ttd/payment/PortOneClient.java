package kr.ac.kopo.ttd.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * PortOne V2 REST API(https://api.portone.io) 호출 단일 창구. Controller/Service는
 * PG사 API를 직접 호출하지 않고 이 클래스만 경유한다(ai/AiClient와 동일 컨벤션).
 *
 * <p>PortOne 응답 스키마 주의: 문서 사이트가 SPA라 amount/customer 등 세부 필드를 완전히
 * 확정하지 못한 채 구현했다. 실제 PortOne 테스트 스토어 키 발급 후 sandbox 결제 1건으로
 * {@link BillingKeyPaymentRequest}/{@link PortOnePaymentApiResponse} 필드를 검증해야 한다.
 * 필드 조정이 필요해도 영향 범위가 이 파일로 한정되도록 요청/응답 매핑을 여기에만 둔다.</p>
 */
@Slf4j
@Component
public class PortOneClient {

    private static final String BASE_URL = "https://api.portone.io";

    private final RestClient restClient;
    private final String storeId;
    private final String channelKey;
    private final boolean mockEnabled;

    public PortOneClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.portone.api-secret}") String apiSecret,
            @Value("${app.portone.store-id}") String storeId,
            @Value("${app.portone.channel-key}") String channelKey,
            @Value("${app.payment.mock-enabled}") boolean mockEnabled) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "PortOne " + apiSecret)
                .build();
        this.storeId = storeId;
        this.channelKey = channelKey;
        this.mockEnabled = mockEnabled;
    }

    /**
     * 빌링키로 결제를 요청한다(구독 등록 시 첫 결제, 재결제 스케줄러 공용). 응답을 못 받는
     * 경우(타임아웃 등)까지 실패로 처리해 최소한 Payment 원장에 시도 기록이 남도록 한다 —
     * 웹훅 없이 동기 응답만 쓰므로, PortOne 쪽은 승인됐는데 응답이 유실된 경우는 감지할 수
     * 없다(잔여 리스크. 의심되면 PortOne 관리자 콘솔에서 paymentId로 직접 대조 필요).
     */
    public PortOnePaymentResult payWithBillingKey(String paymentId, String billingKey, long amountKrw, String orderName) {
        if (mockEnabled && isDemoBillingKey(billingKey)) {
            // 데모 빌링키(프론트 F117 미가맹점 우회 등)만 PG 실호출 없이 성공 처리한다(설계서 S-13).
            // 실제 발급된 빌링키는 mockEnabled여도 실제 PortOne 결제로 진행 — 데모 승인 대상은 데모 키뿐.
            log.info("[데모 결제] 데모 빌링키 승인(PG 미호출): paymentId={}, amount={}", paymentId, amountKrw);
            return PortOnePaymentResult.success(LocalDateTime.now());
        }
        try {
            PortOnePaymentApiResponse response = restClient.post()
                    .uri("/payments/{paymentId}/billing-key", paymentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new BillingKeyPaymentRequest(
                            storeId, billingKey, channelKey, orderName,
                            new BillingKeyPaymentRequest.Amount(amountKrw), "KRW"))
                    .retrieve()
                    .body(PortOnePaymentApiResponse.class);
            return toResult(response, amountKrw);
        } catch (RestClientException e) {
            log.warn("PortOne 빌링키 결제 요청 실패: paymentId={}", paymentId, e);
            return PortOnePaymentResult.failed("PORTONE_API_ERROR: " + e.getMessage());
        }
    }

    /** 구독 취소 시 PortOne 측 빌링키도 폐기한다. best-effort — 실패해도 로컬 구독 취소는 막지 않는다. */
    public void deleteBillingKey(String billingKey) {
        if (mockEnabled && isDemoBillingKey(billingKey)) {
            return; // 데모 빌링키는 실제로 발급된 적이 없으므로 폐기도 건너뛴다.
        }
        try {
            restClient.delete().uri("/billing-keys/{billingKey}", billingKey).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("PortOne 빌링키 삭제 실패(구독 취소 자체는 계속 진행): billingKey 삭제만 실패, 로컬 구독은 정상 취소됨", e);
        }
    }

    /**
     * 프론트 데모 결제가 발급한 가짜 빌링키인지 판별한다(F117 우회 {@code demo-*}, mock 폴백 {@code mock-*}).
     * mockEnabled일 때만 이 키들을 내부 승인 대상으로 삼아, 실제 발급 빌링키는 그대로 PG로 보낸다.
     */
    private static boolean isDemoBillingKey(String billingKey) {
        return billingKey != null
                && (billingKey.startsWith("demo-") || billingKey.startsWith("mock-"));
    }

    private PortOnePaymentResult toResult(PortOnePaymentApiResponse response, long expectedAmountKrw) {
        if (response == null || !"PAID".equalsIgnoreCase(response.status())) {
            String reason = response != null && response.failure() != null
                    ? response.failure().reason()
                    : (response != null ? response.status() : "EMPTY_RESPONSE");
            return PortOnePaymentResult.failed(reason);
        }
        // 승인 금액 검증: 응답 금액이 요청 금액과 다르면 성공으로 처리하지 않는다(할인/변조 방어).
        // 잠정 스키마라 금액 필드가 없으면(null) 검증만 건너뛰고 경고 — 스키마 확정 후 fail-closed로 강화 필요.
        Long paidTotal = response.amount() != null ? response.amount().total() : null;
        if (paidTotal == null) {
            log.warn("PortOne 응답에 결제 금액이 없어 금액 검증을 건너뜀(스키마 확인 필요): expected={}", expectedAmountKrw);
        } else if (paidTotal != expectedAmountKrw) {
            log.error("PortOne 승인 금액 불일치: expected={}, actual={}", expectedAmountKrw, paidTotal);
            return PortOnePaymentResult.failed("AMOUNT_MISMATCH: expected " + expectedAmountKrw + " got " + paidTotal);
        }
        return PortOnePaymentResult.success(parsePaidAt(response.paidAt()));
    }

    private LocalDateTime parsePaidAt(String paidAt) {
        if (paidAt == null) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(paidAt).toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    /** 빌링키 결제 요청 바디. 필드명은 리스크 주석 참고 — 검증 전까지 잠정 스키마. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BillingKeyPaymentRequest(
            String storeId, String billingKey, String channelKey, String orderName,
            Amount amount, String currency) {
        private record Amount(long total) {}
    }

    /** PortOne 결제 조회/응답 바디. 알 수 없는 필드는 무시(ignoreUnknown)해 스키마 변동에 방어적으로 대응. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PortOnePaymentApiResponse(String status, String paidAt, Amount amount, Failure failure) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Amount(Long total) {}
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Failure(String reason) {}
    }
}
