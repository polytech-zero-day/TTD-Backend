package kr.ac.kopo.ttd.repository;

import kr.ac.kopo.ttd.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
