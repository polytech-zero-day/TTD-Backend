package kr.ac.kopo.ttd.grading;

import com.rabbitmq.client.Channel;
import kr.ac.kopo.ttd.domain.Attempt;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class GradingConsumer {

    private final AttemptRepository attemptRepository;
    private final AttemptMessageRepository messageRepository;
    private final RubricGrader rubricGrader;
    private final EfficiencyScorer efficiencyScorer;

    /** yaml의 acknowledge-mode: manual 에 맞춰 성공 시에만 ack. 실패는 requeue 없이 버리고 로그. */
    @RabbitListener(queues = "${app.rabbitmq.queue.grading}")
    @Transactional
    public void grade(String attemptIdPayload, Channel channel,
                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            Long attemptId = Long.valueOf(attemptIdPayload);
            Attempt attempt = attemptRepository.findById(attemptId).orElseThrow();

            RubricGrader.RubricResult rubric = rubricGrader.grade(
                    attempt.getProblem(), attempt.getArtifact(),
                    messageRepository.findByAttemptIdOrderByIdAsc(attemptId));
            int efficiency = efficiencyScorer.score(attempt.getTotalTokens());

            attempt.grade(rubric.score(), efficiency, rubric.feedback());
            channel.basicAck(deliveryTag, false);
            log.info("채점 완료: attemptId={}, rubric={}, efficiency={}", attemptId, rubric.score(), efficiency);
        } catch (Exception e) {
            log.error("채점 실패: payload={}", attemptIdPayload, e);
            channel.basicNack(deliveryTag, false, false); // TODO(3.7): DLQ 재처리 붙이면 교체
        }
    }
}