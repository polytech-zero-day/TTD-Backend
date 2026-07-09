package kr.ac.kopo.ttd.domain;

import jakarta.persistence.*;
import kr.ac.kopo.ttd.common.exception.InvalidStatusTransitionException;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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

    @Column(columnDefinition = "text")
    private String feedback;

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

    public void grade(int rubricScore, int efficiencyScore, String feedback) {
        changeStatus(AttemptStatus.GRADED);
        this.rubricScore = rubricScore;
        this.efficiencyScore = efficiencyScore;
        this.feedback = feedback;
    }

    private void changeStatus(AttemptStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException();
        }
        this.status = target;
    }
}