package kr.ac.kopo.ttd.domain;

import jakarta.persistence.*;
import kr.ac.kopo.ttd.common.converter.RubricCriterionListJsonConverter;
import kr.ac.kopo.ttd.common.exception.InvalidStatusTransitionException;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 문제 응시 세션 1회. 사용자·문제당 진행 중(IN_PROGRESS) 세션은 1개만 허용하며,
 * 마감 시각(endsAt)·메시지 횟수·토큰 사용량 등 채점 원천 데이터는 전부 서버가 소유한다.
 * user는 JWT principal(Long userId)과의 대칭을 위해 FK 조인 없이 id만 보관한다.
 */
@Entity
@Table(name = "attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 낙관적 락 — 동시 대화·제출 시 사용량(토큰) 갱신이 last-writer-wins로 유실되는 것을 막는다. */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private long version = 0;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    /**
     * 응시 시작 시점의 유료(PAID) 여부를 고정 저장한다. 응시 도중 구독 변동이 있어도
     * 한 응시 내 모델·한도 정책이 일관되게 유지되도록(재조회 없이) 시작 시 스냅샷한다.
     */
    @Column(name = "premium", nullable = false)
    @Builder.Default
    private boolean premium = false;

    /**
     * 응시 생성 시 확정한 대화 모델. 이후 관리자 설정이나 구독 상태가 바뀌어도
     * 진행 중 응시와 결과 리포트가 실제 사용 모델을 일관되게 가리키도록 보존한다.
     */
    @Column(name = "chat_model", length = 100)
    private String chatModel;

    @Column(name = "message_count", nullable = false)
    @Builder.Default
    private int messageCount = 0;

    @Column(name = "total_tokens", nullable = false)
    @Builder.Default
    private long totalTokens = 0;

    @Column(columnDefinition = "text")
    private String draft; // 우측 패널 자동 저장분. 만료 시 이걸로 자동 제출

    @Column(columnDefinition = "text")
    private String artifact; // 제출 확정된 최종 결과물

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "rubric_score")
    private Integer rubricScore;

    @Column(name = "efficiency_score")
    private Integer efficiencyScore;

    /** 종합 점수 = 루브릭×0.6 + 효율×0.4 (가중치는 채점 시점 값으로 고정 저장). */
    @Column(name = "final_score")
    private Integer finalScore;

    @Column(columnDefinition = "text")
    private String feedback;

    /** 루브릭 항목별 점수·코멘트 (JSON 배열). 결과 리포트의 항목별 표시에 쓰인다. */
    @Convert(converter = RubricCriterionListJsonConverter.class)
    @Column(name = "rubric_detail", columnDefinition = "text")
    private List<RubricCriterion> rubricDetail;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private LocalDateTime startedAt;

    public boolean isExpired(LocalDateTime now) {
        return status == AttemptStatus.IN_PROGRESS && now.isAfter(endsAt);
    }

    public void recordExchange(long tokensUsed) {
        this.messageCount++;
        this.totalTokens += tokensUsed;
    }

    public void updateDraft(String draft) {
        this.draft = draft;
    }

    public void submit(String artifact, LocalDateTime now) {
        changeStatus(AttemptStatus.GRADING);
        this.artifact = artifact;
        this.submittedAt = now;
    }

    /** 대화·결과물 없이 시간이 만료된 세션은 AI 채점 비용 없이 종료한다. */
    public void abandon() {
        changeStatus(AttemptStatus.ABANDONED);
    }

    public boolean hasSubmissionContent() {
        return messageCount > 0 || (draft != null && !draft.isBlank());
    }

    public boolean isResumable() {
        return status == AttemptStatus.IN_PROGRESS
                || status == AttemptStatus.GRADING
                || status == AttemptStatus.GRADING_FAILED;
    }

    /** 채점 실패를 기록한다. 제출물·대화 이력은 보존되며 재채점으로 복구할 수 있다. */
    public void failGrading() {
        changeStatus(AttemptStatus.GRADING_FAILED);
    }

    /** 재채점을 위해 채점 대기 상태로 되돌린다. */
    public void requeueGrading() {
        changeStatus(AttemptStatus.GRADING);
    }

    public void grade(int rubricScore, int efficiencyScore, int finalScore,
                      String feedback, List<RubricCriterion> rubricDetail) {
        changeStatus(AttemptStatus.GRADED);
        this.rubricScore = rubricScore;
        this.efficiencyScore = efficiencyScore;
        this.finalScore = finalScore;
        this.feedback = feedback;
        this.rubricDetail = new ArrayList<>(rubricDetail);
    }

    private void changeStatus(AttemptStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException();
        }
        this.status = target;
    }
}
