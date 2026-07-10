package kr.ac.kopo.ttd.service;

import kr.ac.kopo.ttd.ai.AiChatResult;
import kr.ac.kopo.ttd.ai.AiClient;
import kr.ac.kopo.ttd.common.exception.AttemptMessageLimitExceededException;
import kr.ac.kopo.ttd.common.exception.AttemptNotFoundException;
import kr.ac.kopo.ttd.common.exception.AttemptNotInProgressException;
import kr.ac.kopo.ttd.common.exception.AttemptNotRegradableException;
import kr.ac.kopo.ttd.common.exception.AttemptQuotaExceededException;
import kr.ac.kopo.ttd.common.exception.BusinessException;
import kr.ac.kopo.ttd.common.exception.ErrorCode;
import kr.ac.kopo.ttd.common.exception.ProblemNotFoundException;
import kr.ac.kopo.ttd.domain.Attempt;
import kr.ac.kopo.ttd.domain.AttemptMessage;
import kr.ac.kopo.ttd.domain.AttemptStatus;
import kr.ac.kopo.ttd.domain.Difficulty;
import kr.ac.kopo.ttd.domain.Problem;
import kr.ac.kopo.ttd.domain.ProblemStatus;
import kr.ac.kopo.ttd.domain.MessageRole;
import kr.ac.kopo.ttd.domain.ProblemType;
import kr.ac.kopo.ttd.domain.RubricCriterion;
import kr.ac.kopo.ttd.domain.SourceType;
import kr.ac.kopo.ttd.dto.AttemptMessageRequest;
import kr.ac.kopo.ttd.dto.AttemptMessageResponse;
import kr.ac.kopo.ttd.dto.AttemptResultResponse;
import kr.ac.kopo.ttd.dto.AttemptSnapshotResponse;
import kr.ac.kopo.ttd.dto.AttemptStartRequest;
import kr.ac.kopo.ttd.dto.DraftUpdateRequest;
import kr.ac.kopo.ttd.grading.GradingProducer;
import kr.ac.kopo.ttd.repository.AttemptMessageRepository;
import kr.ac.kopo.ttd.repository.AttemptRepository;
import kr.ac.kopo.ttd.repository.ProblemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AttemptServiceTest {

    private static final Long USER_ID = 1L;
    private static final int MESSAGE_LIMIT = 10;
    private static final long TOKEN_BASELINE = 3000L;
    private static final long TIME_LIMIT_MINUTES = 45L;

    @Mock
    private AttemptRepository attemptRepository;

    @Mock
    private AttemptMessageRepository messageRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private AiClient aiClient;

    @Mock
    private GradingProducer gradingProducer;

    private AttemptService attemptService;

    @BeforeEach
    void setUp() {
        // @Value 프리미티브 파라미터 때문에 @InjectMocks 대신 직접 생성한다
        attemptService = new AttemptService(
                attemptRepository, messageRepository, problemRepository,
                aiClient, gradingProducer,
                MESSAGE_LIMIT, TIME_LIMIT_MINUTES);
    }

    private Problem activeProblem() {
        return Problem.builder()
                .title("고객 문의 라우팅 판정")
                .difficulty(Difficulty.L1)
                .type(ProblemType.CLASSIFY)
                .sourceType(SourceType.RUBRIC_ONLY)
                .description("설명")
                .requirements(List.of("요구사항1"))
                .constraints(List.of("제약1"))
                .maxAttempts(3)
                .build();
    }

    private Attempt inProgressAttempt(Problem problem) {
        return Attempt.builder()
                .userId(USER_ID)
                .problem(problem)
                .endsAt(LocalDateTime.now().plusMinutes(TIME_LIMIT_MINUTES))
                .build();
    }

    // ── 시작 ──────────────────────────────────────────────

    @Test
    void 응시를_시작하면_새_세션을_생성한다() {
        Problem problem = activeProblem();
        given(problemRepository.findByIdAndStatus(1L, ProblemStatus.ACTIVE)).willReturn(Optional.of(problem));
        given(attemptRepository.findByUserIdAndProblemIdAndStatus(USER_ID, problem.getId(), AttemptStatus.IN_PROGRESS))
                .willReturn(Optional.empty());
        given(attemptRepository.countByUserIdAndProblemId(USER_ID, problem.getId())).willReturn(0L);
        given(attemptRepository.save(any(Attempt.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(messageRepository.findByAttemptIdOrderByIdAsc(any())).willReturn(List.of());

        AttemptSnapshotResponse response = attemptService.start(USER_ID, new AttemptStartRequest(1L));

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.remainingSeconds()).isBetween(TIME_LIMIT_MINUTES * 60 - 5, TIME_LIMIT_MINUTES * 60);
        assertThat(response.usage().messagesUsed()).isZero();
        assertThat(response.usage().messagesLimit()).isEqualTo(MESSAGE_LIMIT);
        assertThat(response.usage().tokensBaseline()).isEqualTo(TOKEN_BASELINE);
    }

    @Test
    void 진행_중_세션이_있으면_새로_만들지_않고_그대로_반환한다() {
        Problem problem = activeProblem();
        Attempt existing = inProgressAttempt(problem);
        given(problemRepository.findByIdAndStatus(1L, ProblemStatus.ACTIVE)).willReturn(Optional.of(problem));
        given(attemptRepository.findByUserIdAndProblemIdAndStatus(USER_ID, problem.getId(), AttemptStatus.IN_PROGRESS))
                .willReturn(Optional.of(existing));
        given(messageRepository.findByAttemptIdOrderByIdAsc(any())).willReturn(List.of());

        AttemptSnapshotResponse response = attemptService.start(USER_ID, new AttemptStartRequest(1L));

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void 응시_횟수를_모두_사용하면_예외() {
        Problem problem = activeProblem();
        given(problemRepository.findByIdAndStatus(1L, ProblemStatus.ACTIVE)).willReturn(Optional.of(problem));
        given(attemptRepository.findByUserIdAndProblemIdAndStatus(USER_ID, problem.getId(), AttemptStatus.IN_PROGRESS))
                .willReturn(Optional.empty());
        given(attemptRepository.countByUserIdAndProblemId(USER_ID, problem.getId())).willReturn(3L);

        assertThatThrownBy(() -> attemptService.start(USER_ID, new AttemptStartRequest(1L)))
                .isInstanceOf(AttemptQuotaExceededException.class);

        verify(attemptRepository, never()).save(any());
    }

    @Test
    void 비활성_문제는_응시를_시작할_수_없다() {
        given(problemRepository.findByIdAndStatus(99L, ProblemStatus.ACTIVE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> attemptService.start(USER_ID, new AttemptStartRequest(99L)))
                .isInstanceOf(ProblemNotFoundException.class);
    }

    // ── 대화 ──────────────────────────────────────────────

    @Test
    void 메시지를_전송하면_AI응답과_서버_계산_사용량을_반환한다() {
        Attempt attempt = inProgressAttempt(activeProblem());
        given(attemptRepository.findById(1L)).willReturn(Optional.of(attempt));
        given(messageRepository.findByAttemptIdOrderByIdAsc(1L)).willReturn(List.of());
        given(aiClient.chat(anyString(), anyList())).willReturn(new AiChatResult("AI 답변", 500L));
        given(messageRepository.save(any(AttemptMessage.class))).willAnswer(invocation -> invocation.getArgument(0));

        AttemptMessageResponse response = attemptService.sendMessage(USER_ID, 1L, new AttemptMessageRequest("질문"));

        assertThat(response.message().content()).isEqualTo("AI 답변");
        assertThat(response.message().role()).isEqualTo("assistant");
        assertThat(response.usage().messagesUsed()).isEqualTo(1);
        assertThat(response.usage().tokensUsed()).isEqualTo(500L);
    }

    @Test
    void 메시지_횟수를_모두_사용하면_전송_불가() {
        Attempt attempt = inProgressAttempt(activeProblem());
        for (int i = 0; i < MESSAGE_LIMIT; i++) {
            attempt.recordExchange(100);
        }
        given(attemptRepository.findById(1L)).willReturn(Optional.of(attempt));

        assertThatThrownBy(() -> attemptService.sendMessage(USER_ID, 1L, new AttemptMessageRequest("질문")))
                .isInstanceOf(AttemptMessageLimitExceededException.class);

        verify(aiClient, never()).chat(anyString(), anyList());
    }

    @Test
    void 마감시각이_지난_세션은_마지막_draft로_자동_제출된다() {
        Attempt attempt = Attempt.builder()
                .userId(USER_ID)
                .problem(activeProblem())
                .endsAt(LocalDateTime.now().minusMinutes(1))
                .build();
        attempt.updateDraft("마지막 저장본");
        given(attemptRepository.findById(1L)).willReturn(Optional.of(attempt));

        assertThatThrownBy(() -> attemptService.sendMessage(USER_ID, 1L, new AttemptMessageRequest("질문")))
                .isInstanceOf(AttemptNotInProgressException.class);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.GRADING);
        assertThat(attempt.getArtifact()).isEqualTo("마지막 저장본");
        verify(gradingProducer).requestGrading(attempt.getId());
        verify(aiClient, never()).chat(anyString(), anyList());
    }

    @Test
    void 타인의_응시에_접근하면_예외() {
        Attempt attempt = inProgressAttempt(activeProblem());
        given(attemptRepository.findById(1L)).willReturn(Optional.of(attempt));

        assertThatThrownBy(() -> attemptService.sendMessage(999L, 1L, new AttemptMessageRequest("질문")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    // ── 결과 조회 ──────────────────────────────────────────

    @Test
    void 채점_완료된_응시는_리포트_전체를_담아_반환한다() {
        Attempt attempt = inProgressAttempt(activeProblem());
        attempt.submit("최종 결과물", LocalDateTime.now());
        attempt.grade(90, 80, 86, "총평입니다.",
                List.of(new RubricCriterion("요구사항 충족", 36, 40, "대체로 충족")));
        given(attemptRepository.findById(1L)).willReturn(Optional.of(attempt));
        given(messageRepository.findByAttemptIdOrderByIdAsc(attempt.getId())).willReturn(List.of(
                AttemptMessage.builder().attempt(attempt).role(MessageRole.USER)
                        .content("분류 기준을 알려줘").build(),
                AttemptMessage.builder().attempt(attempt).role(MessageRole.ASSISTANT)
                        .content("기준은 다음과 같습니다").tokensUsed(500L).build()));
        given(attemptRepository.countByUserIdAndProblemIdAndIdLessThanEqual(
                USER_ID, attempt.getProblem().getId(), attempt.getId())).willReturn(2);

        AttemptResultResponse result = attemptService.getResult(USER_ID, 1L);

        assertThat(result.finalScore()).isEqualTo(86);
        assertThat(result.criteria()).hasSize(1);
        assertThat(result.criteria().get(0).maxScore()).isEqualTo(40);
        assertThat(result.problemTitle()).isEqualTo("고객 문의 라우팅 판정");
        assertThat(result.attemptOrdinal()).isEqualTo(2);
        assertThat(result.maxAttempts()).isEqualTo(3);
        assertThat(result.tokenBudget()).isEqualTo(3000L);
        assertThat(result.messages()).hasSize(2);
        assertThat(result.messages().get(1).tokensUsed()).isEqualTo(500L);
    }

    @Test
    void 채점_전_응시의_결과는_점수가_비어있고_상태만_내려간다() {
        Attempt attempt = inProgressAttempt(activeProblem());
        attempt.submit("결과물", LocalDateTime.now());
        given(attemptRepository.findById(1L)).willReturn(Optional.of(attempt));

        AttemptResultResponse result = attemptService.getResult(USER_ID, 1L);

        assertThat(result.status()).isEqualTo("GRADING");
        assertThat(result.rubricScore()).isNull();
        assertThat(result.finalScore()).isNull();
        assertThat(result.criteria()).isEmpty();
    }

    // ── 제출 ──────────────────────────────────────────────

    @Test
    void 제출하면_GRADING으로_전환되고_채점을_요청한다() {
        Attempt attempt = inProgressAttempt(activeProblem());
        attempt.updateDraft("최종 결과물");
        given(attemptRepository.findById(1L)).willReturn(Optional.of(attempt));

        attemptService.submit(USER_ID, 1L);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.GRADING);
        assertThat(attempt.getArtifact()).isEqualTo("최종 결과물");
        assertThat(attempt.getSubmittedAt()).isNotNull();
        verify(gradingProducer).requestGrading(attempt.getId());
    }

    @Test
    void 이미_제출된_응시는_다시_제출할_수_없다() {
        Attempt attempt = inProgressAttempt(activeProblem());
        attempt.submit("첫 제출", LocalDateTime.now());
        given(attemptRepository.findById(1L)).willReturn(Optional.of(attempt));

        assertThatThrownBy(() -> attemptService.submit(USER_ID, 1L))
                .isInstanceOf(AttemptNotInProgressException.class);

        verify(gradingProducer, never()).requestGrading(any());
    }

    @Test
    void 제출된_세션에는_draft를_저장할_수_없다() {
        Attempt attempt = inProgressAttempt(activeProblem());
        attempt.submit("첫 제출", LocalDateTime.now());
        given(attemptRepository.findById(1L)).willReturn(Optional.of(attempt));

        assertThatThrownBy(() -> attemptService.updateDraft(USER_ID, 1L, new DraftUpdateRequest("수정 시도")))
                .isInstanceOf(AttemptNotInProgressException.class);
    }

    // ── 복원 ──────────────────────────────────────────────

    @Test
    void 진행_중_세션이_없으면_복원_조회시_예외() {
        given(attemptRepository.findByUserIdAndProblemIdAndStatus(USER_ID, 1L, AttemptStatus.IN_PROGRESS))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> attemptService.getCurrent(USER_ID, 1L))
                .isInstanceOf(AttemptNotFoundException.class);
    }

    // ── 재채점 ────────────────────────────────────────────

    private Attempt failedAttempt() {
        Attempt attempt = inProgressAttempt(activeProblem());
        attempt.submit("제출물", LocalDateTime.now());
        attempt.failGrading();
        return attempt;
    }

    @Test
    void 채점_실패_상태에서_재채점을_요청하면_GRADING으로_복귀하고_다시_발행한다() {
        Attempt attempt = failedAttempt();
        given(attemptRepository.findById(1L)).willReturn(Optional.of(attempt));

        attemptService.regrade(USER_ID, 1L);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.GRADING);
        assertThat(attempt.getArtifact()).isEqualTo("제출물"); // 제출물 보존 확인
        verify(gradingProducer).requestGrading(attempt.getId());
    }

    @Test
    void 채점_실패_상태가_아니면_재채점_불가() {
        Attempt attempt = inProgressAttempt(activeProblem());
        attempt.submit("제출물", LocalDateTime.now()); // GRADING 상태
        given(attemptRepository.findById(1L)).willReturn(Optional.of(attempt));

        assertThatThrownBy(() -> attemptService.regrade(USER_ID, 1L))
                .isInstanceOf(AttemptNotRegradableException.class);

        verify(gradingProducer, never()).requestGrading(any());
    }

    @Test
    void 타인의_응시는_재채점을_요청할_수_없다() {
        Attempt attempt = failedAttempt();
        given(attemptRepository.findById(1L)).willReturn(Optional.of(attempt));

        assertThatThrownBy(() -> attemptService.regrade(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }
}
