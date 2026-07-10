package kr.ac.kopo.ttd.grading;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class GradingProducer {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public GradingProducer(
            RabbitTemplate rabbitTemplate,
            @Value("${app.rabbitmq.exchange}") String exchange,
            @Value("${app.rabbitmq.routing-key.grading}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    /**
     * 채점 요청 발행. 트랜잭션 중이면 커밋 이후로 미룬다 — 커밋 전에 발행하면
     * 컨슈머가 제출 전 상태(IN_PROGRESS)를 읽어 채점 메시지를 무효 소진하는
     * 레이스가 생긴다 (제출 직후 캐시 히트로 채점이 즉시 끝나는 경우 실제 발생).
     */
    public void requestGrading(Long attemptId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(attemptId);
                }
            });
        } else {
            send(attemptId);
        }
    }

    private void send(Long attemptId) {
        rabbitTemplate.convertAndSend(exchange, routingKey, String.valueOf(attemptId));
    }
}
