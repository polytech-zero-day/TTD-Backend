package kr.ac.kopo.ttd.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 응시 중 AI 대화 이력. 루브릭 채점(결과물+대화이력)과 효율 점수의 원천 데이터이므로
 * 응시 종료 후에도 삭제하지 않는다.
 */
@Entity
@Table(name = "attempt_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AttemptMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private Attempt attempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** 이 교환에 든 총 토큰(prompt+completion). USER 메시지는 0, ASSISTANT 메시지에 기록. */
    @Column(name = "tokens_used", nullable = false)
    @Builder.Default
    private long tokensUsed = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}