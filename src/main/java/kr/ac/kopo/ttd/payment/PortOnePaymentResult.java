package kr.ac.kopo.ttd.payment;

import java.time.LocalDateTime;

/** PortOneClient 호출 결과. 서비스 레이어는 PortOne의 원본 응답 형태를 모르고 이 record만 본다. */
public record PortOnePaymentResult(boolean success, LocalDateTime paidAt, String failReason) {

    public static PortOnePaymentResult success(LocalDateTime paidAt) {
        return new PortOnePaymentResult(true, paidAt, null);
    }

    public static PortOnePaymentResult failed(String reason) {
        return new PortOnePaymentResult(false, null, reason);
    }
}
