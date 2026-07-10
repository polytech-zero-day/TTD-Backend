package kr.ac.kopo.ttd.common.constant;

public final class PaymentConstants {

    /** 재결제 연속 실패 허용 횟수. 초과 시 구독을 EXPIRED로 만료 처리한다. */
    public static final int MAX_BILLING_RETRY = 3;

    private PaymentConstants() {
    }
}
