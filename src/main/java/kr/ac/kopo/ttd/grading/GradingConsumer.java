package kr.ac.kopo.ttd.grading;

import com.rabbitmq.client.Channel;
import kr.ac.kopo.ttd.domain.Attempt;
import kr.ac.kopo.ttd.common.constant.ScoreWeights;
import kr.ac.kopo.ttd.domain.AttemptStatus;
import kr.ac.kopo.ttd.repository.AttemptMessageRepository;
import kr.ac.kopo.ttd.repository.AttemptRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;

/**
 * 채점 컨슈머. LLM 호출의 비결정성을 고려해 즉시 재시도(RETRY_LIMIT회)로 일시 오류를 흡수하고,
 * 최종 실패 시 attempt를 GRADING_FAILED로 남긴다 — 제출물은 보존되며 재채점 API로 복구한다.
 *
 * ack는 반드시 트랜잭션 커밋 이후에 한다. @Transactional 메서드에서 finally로 ack하면 커밋보다
 * 먼저 실행되어, 커밋 실패 시 결과는 롤백되는데 메시지는 소진되어 GRADING 영구 고착이 된다.
 * 그래서 커밋을 동기적으로 끝내는 TransactionTemplate로 감싸고, 그 뒤에 ack/재큐를 결정한다.
 */
@Slf4j
@Component
public class GradingConsumer {

    private static final int RETRY_LIMIT = 2;

    private final AttemptRepository attemptRepository;
    private final AttemptMessageRepository messageRepository;
    private final RubricGrader rubricGrader;
    private final EfficiencyScorer efficiencyScorer;
    private final TransactionTemplate transactionTemplate;

    public GradingConsumer(AttemptRepository attemptRepository,
                           AttemptMessageRepository messageRepository,
                           RubricGrader rubricGrader,
                           EfficiencyScorer efficiencyScorer,
                           PlatformTransactionManager transactionManager) {
        this.attemptRepository = attemptRepository;
        this.messageRepository = messageRepository;
        this.rubricGrader = rubricGrader;
        this.efficiencyScorer = efficiencyScorer;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @RabbitListener(queues = "${app.rabbitmq.queue.grading}")
    public void grade(String attemptIdPayload, Channel channel,
                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Long attemptId = null;
        try {
            attemptId = Long.valueOf(attemptIdPayload);
            gradeInTransaction(attemptId); // 여기서 커밋까지 동기적으로 완료된다
        } catch (Exception e) {
            log.error("채점 최종 실패: attemptId={}, errorType={}",
                    attemptId, e.getClass().getSimpleName());
            markFailed(attemptId); // 별도 트랜잭션으로 GRADING_FAILED 확정
        } finally {
            channel.basicAck(deliveryTag, false); // 커밋 이후에만 소비 확정
        }
    }

    /** 채점 본문 — 트랜잭션 커밋이 이 메서드 반환 전에 끝난다(ack-after-commit 보장). */
    private void gradeInTransaction(Long attemptId) {
        transactionTemplate.executeWithoutResult(status -> {
            Attempt attempt = attemptRepository.findById(attemptId).orElseThrow();

            if (attempt.getStatus() != AttemptStatus.GRADING) {
                // 중복 배달·재시작 후 유령 메시지 방어 — LLM 호출 전에 걸러 비용을 아낀다
                log.warn("채점 대상이 아닌 응시라 스킵: attemptId={}, status={}", attemptId, attempt.getStatus());
                return;
            }

            RubricGrader.RubricResult rubric = gradeWithRetry(attempt, attemptId);
            int efficiency = efficiencyScorer.score(
                    attempt.getTotalTokens(), attempt.getProblem().getTokenBudget());
            int finalScore = (int) Math.round(
                    rubric.score() * ScoreWeights.RUBRIC + efficiency * ScoreWeights.EFFICIENCY);

            attempt.grade(rubric.score(), efficiency, finalScore, rubric.feedback(), rubric.criteria());
            log.info("채점 완료: attemptId={}, rubric={}, efficiency={}, final={}",
                    attemptId, rubric.score(), efficiency, finalScore);
        });
    }

    /** 일시 오류(LLM 순간 장애·비정형 응답) 흡수를 위한 즉시 재시도. */
    private RubricGrader.RubricResult gradeWithRetry(Attempt attempt, Long attemptId) {
        RuntimeException last = null;
        for (int tried = 1; tried <= RETRY_LIMIT; tried++) {
            try {
                return rubricGrader.grade(
                        attempt.getProblem(), attempt.getArtifact(),
                        messageRepository.findByAttemptIdOrderByIdAsc(attemptId));
            } catch (RuntimeException e) {
                last = e;
                log.warn("채점 시도 {}회차 실패: attemptId={}, errorType={}",
                        tried, attemptId, e.getClass().getSimpleName());
            }
        }
        throw last;
    }

    private void markFailed(Long attemptId) {
        if (attemptId == null) {
            return; // 페이로드 파싱 실패 — 대상 attempt를 특정할 수 없음
        }
        // 채점 트랜잭션과 분리된 새 트랜잭션에서 실패를 확정한다(그래야 커밋 후 ack가 안전).
        transactionTemplate.executeWithoutResult(status ->
                attemptRepository.findById(attemptId)
                        .filter(attempt -> attempt.getStatus() == AttemptStatus.GRADING)
                        .ifPresent(Attempt::failGrading));
    }
}
