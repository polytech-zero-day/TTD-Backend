package kr.ac.kopo.ttd.grading;

import com.rabbitmq.client.Channel;
import kr.ac.kopo.ttd.domain.Attempt;
import kr.ac.kopo.ttd.domain.AttemptStatus;
import kr.ac.kopo.ttd.domain.Difficulty;
import kr.ac.kopo.ttd.domain.Problem;
import kr.ac.kopo.ttd.domain.ProblemType;
import kr.ac.kopo.ttd.domain.SourceType;
import kr.ac.kopo.ttd.repository.AttemptMessageRepository;
import kr.ac.kopo.ttd.repository.AttemptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GradingConsumerTest {

    @Mock
    private AttemptRepository attemptRepository;

    @Mock
    private AttemptMessageRepository messageRepository;

    @Mock
    private RubricGrader rubricGrader;

    @Mock
    private EfficiencyScorer efficiencyScorer;

    @Mock
    private Channel channel;

    @InjectMocks
    private GradingConsumer gradingConsumer;

    private Attempt gradingAttempt() {
        Problem problem = Problem.builder()
                .title("문제")
                .difficulty(Difficulty.L1)
                .type(ProblemType.CLASSIFY)
                .sourceType(SourceType.RUBRIC_ONLY)
                .description("설명")
                .requirements(List.of("요구사항"))
                .constraints(List.of("제약"))
                .build();
        Attempt attempt = Attempt.builder()
                .userId(1L)
                .problem(problem)
                .endsAt(LocalDateTime.now().plusMinutes(45))
                .build();
        attempt.submit("제출물", LocalDateTime.now()); // GRADING 상태
        return attempt;
    }

    @Test
    void 채점에_성공하면_GRADED로_저장하고_ack한다() throws IOException {
        Attempt attempt = gradingAttempt();
        given(attemptRepository.findById(1L)).willReturn(Optional.of(attempt));
        given(rubricGrader.grade(any(), anyString(), any()))
                .willReturn(new RubricGrader.RubricResult(90, "좋음", List.of(
                        new kr.ac.kopo.ttd.domain.RubricCriterion("요구사항 충족", 36, 40, "충실"),
                        new kr.ac.kopo.ttd.domain.RubricCriterion("근거 제시의 구체성", 27, 30, "구체적"),
                        new kr.ac.kopo.ttd.domain.RubricCriterion("절차 설계의 타당성", 27, 30, "타당"))));
        given(efficiencyScorer.score(anyLong(), anyLong())).willReturn(85);

        gradingConsumer.grade("1", channel, 11L);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.GRADED);
        assertThat(attempt.getRubricScore()).isEqualTo(90);
        assertThat(attempt.getEfficiencyScore()).isEqualTo(85);
        assertThat(attempt.getFinalScore()).isEqualTo(88); // 90*0.6 + 85*0.4
        assertThat(attempt.getRubricDetail()).hasSize(3);
        verify(channel).basicAck(11L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void 일시_오류는_즉시_재시도로_흡수한다() throws IOException {
        Attempt attempt = gradingAttempt();
        given(attemptRepository.findById(1L)).willReturn(Optional.of(attempt));
        given(rubricGrader.grade(any(), anyString(), any()))
                .willThrow(new IllegalStateException("일시 오류"))
                .willReturn(new RubricGrader.RubricResult(80, "재시도 성공", List.of()));
        given(efficiencyScorer.score(anyLong(), anyLong())).willReturn(100);

        gradingConsumer.grade("1", channel, 11L);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.GRADED);
        assertThat(attempt.getRubricScore()).isEqualTo(80);
        verify(channel).basicAck(11L, false);
    }

    @Test
    void 재시도까지_모두_실패하면_GRADING_FAILED로_남기고_ack한다() throws IOException {
        Attempt attempt = gradingAttempt();
        given(attemptRepository.findById(1L)).willReturn(Optional.of(attempt));
        given(rubricGrader.grade(any(), anyString(), any()))
                .willThrow(new IllegalStateException("파싱 실패"));

        gradingConsumer.grade("1", channel, 11L);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.GRADING_FAILED);
        assertThat(attempt.getArtifact()).isEqualTo("제출물"); // 제출물 보존
        verify(channel).basicAck(11L, false); // 실패해도 메시지는 소비 확정 (상태는 DB로 관리)
    }

    @Test
    void 채점_대상이_아닌_응시는_LLM_호출_없이_스킵하고_ack한다() throws IOException {
        // 브로커 영속 큐 + 인메모리 DB 재시작 조합에서 생기는 유령 메시지 방어
        Problem problem = Problem.builder()
                .title("문제").difficulty(Difficulty.L1).type(ProblemType.CLASSIFY)
                .sourceType(SourceType.RUBRIC_ONLY).description("설명")
                .requirements(List.of("요구사항")).constraints(List.of("제약"))
                .build();
        Attempt inProgress = Attempt.builder()
                .userId(1L).problem(problem)
                .endsAt(LocalDateTime.now().plusMinutes(45))
                .build(); // IN_PROGRESS — 채점 대상 아님
        given(attemptRepository.findById(1L)).willReturn(Optional.of(inProgress));

        gradingConsumer.grade("1", channel, 11L);

        assertThat(inProgress.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS); // 상태 불변
        verify(rubricGrader, never()).grade(any(), anyString(), any()); // LLM 비용 발생 안 함
        verify(channel).basicAck(11L, false); // 메시지는 소진
    }

    @Test
    void 페이로드가_잘못되어도_메시지는_소비_확정한다() throws IOException {
        gradingConsumer.grade("not-a-number", channel, 11L);

        verify(channel).basicAck(11L, false);
        verify(attemptRepository, never()).findById(any());
    }
}
