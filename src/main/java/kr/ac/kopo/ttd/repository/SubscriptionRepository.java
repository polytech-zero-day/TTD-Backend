package kr.ac.kopo.ttd.repository;

import kr.ac.kopo.ttd.domain.Subscription;
import kr.ac.kopo.ttd.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByUserIdAndStatusIn(Long userId, List<SubscriptionStatus> statuses);
    List<Subscription> findByStatusInAndNextBillingAtLessThanEqual(List<SubscriptionStatus> statuses, LocalDateTime now);
}
