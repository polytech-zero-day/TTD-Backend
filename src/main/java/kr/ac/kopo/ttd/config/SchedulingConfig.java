package kr.ac.kopo.ttd.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 구독 자동 재결제 배치(SubscriptionBillingScheduler) 등 @Scheduled 잡을 위한 설정. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
