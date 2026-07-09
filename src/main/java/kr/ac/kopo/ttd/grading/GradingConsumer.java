package kr.ac.kopo.ttd.grading;

import com.rabbitmq.client.Channel;
import kr.ac.kopo.ttd.domain.Attempt;
import kr.ac.kopo.ttd.domain.AttemptStatus;
import kr.ac.kopo.ttd.repository.AttemptMessageRepository;
import kr.ac.kopo.ttd.repository.AttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * 채점 컨슈머. LLM 호출의 비결정성을 고려해 즉시 재시도(RETRY_LIMIT회)로 일시 오류를 흡수하고,
 * 최종 실패 시 attempt를 GRADING_FAILED로 남긴다 — 제출물은 보존되며 재채점 API로 복구한다.
 * 메시지는 성공/실패와 무관하게 ack로 소비를 확정한다(실패 이력은 브로커가 아닌 DB 상태로 관리).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradingConsumer {

    private static final int RETRY_LIMIT = 2;

    private final AttemptRepository attemptRepository;
    private final AttemptMessageRepository messageRepository;
    private final RubricGrader rubricGrader;
    private final EfficiencyScorer efficiencyScorer;

    @RabbitListener(queues = "${app.rabbitmq.queue.grading}")
    @Transactional
    public void grade(String attemptIdPayload, Channel channel,
                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Long attemptId = null;
        try {
            attemptId = Long.valueOf(attemptIdPayload);
            Attempt attempt = attemptRepository.findById(attemptId).orElseThrow();

            RubricGrader.RubricResult rubric = gradeWithRetry(attempt, attemptId);
            int efficiency = efficiencyScorer.score(attempt.getTotalTokens());

            attempt.grade(rubric.score(), efficiency, rubric.feedback());
            log.info("채점 완료: attemptId={}, rubric={}, efficiency={}", attemptId, rubric.score(), efficiency);
        } catch (Exception e) {
            log.error("채점 최종 실패: payload={}", attemptIdPayload, e);
            markFailed(attemptId);
        } finally {
            channel.basicAck(deliveryTag, false);
        }
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
                log.warn("채점 시도 {}회차 실패: attemptId={}", tried, attemptId, e);
            }
        }
        throw last;
    }

    private void markFailed(Long attemptId) {
        if (attemptId == null) {
            return; // 페이로드 파싱 실패 — 대상 attempt를 특정할 수 없음
        }
        attemptRepository.findById(attemptId)
                .filter(attempt -> attempt.getStatus() == AttemptStatus.GRADING)
                .ifPresent(Attempt::failGrading);
    }
}
