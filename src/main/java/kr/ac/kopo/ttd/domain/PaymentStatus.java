package kr.ac.kopo.ttd.domain;

public enum PaymentStatus {
    PENDING, PAID, FAILED;

    public boolean isTerminal() {
        return this == PAID || this == FAILED;
    }
}
