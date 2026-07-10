package kr.ac.kopo.ttd.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 용도별 AI 모델 설정. 관리자가 재시작 없이 모델을 교체하기 위한 단일 원천으로,
 * 용도(purpose)당 한 행만 존재한다. 미설정 용도는 서비스가 yaml 기본 모델로 폴백한다.
 */
@Entity
@Table(name = "ai_model_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AiModelSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private AiPurpose purpose;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "updated_by")
    private Long updatedBy; // 마지막으로 변경한 관리자 userId

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void changeModel(String model, Long updatedBy) {
        this.model = model;
        this.updatedBy = updatedBy;
    }
}
