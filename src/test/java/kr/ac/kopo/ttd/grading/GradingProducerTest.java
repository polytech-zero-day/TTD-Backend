package kr.ac.kopo.ttd.grading;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GradingProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private GradingProducer producer() {
        return new GradingProducer(rabbitTemplate, "ttd.exchange", "grading");
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 트랜잭션이_없으면_즉시_발행한다() {
        producer().requestGrading(7L);

        verify(rabbitTemplate).convertAndSend("ttd.exchange", "grading", "7");
    }

    @Test
    void 트랜잭션_중이면_커밋_이후에_발행한다() {
        // 커밋 전 발행 시 컨슈머가 제출 전 상태를 읽는 레이스 방지 — 커밋 시점까지 발행 보류 검증
        TransactionSynchronizationManager.initSynchronization();

        producer().requestGrading(7L);
        verify(rabbitTemplate, never()).convertAndSend(eq("ttd.exchange"), eq("grading"), eq("7"));

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit(); // 커밋 시그널
        }
        verify(rabbitTemplate).convertAndSend("ttd.exchange", "grading", "7");
    }
}
