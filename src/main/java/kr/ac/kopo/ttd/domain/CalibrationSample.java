package kr.ac.kopo.ttd.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문제별 캘리브레이션 샘플. 상/중/하 등급의 모범답안과 기준 점수(0~100)를 담아 채점 기준선으로 쓴다.
 * 문제당 정확히 3건(상·중·하).
 */
@Entity
@Table(name = "calibration_samples")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CalibrationSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private CalibrationTier tier;

    @Column(name = "sample_answer", nullable = false, columnDefinition = "text")
    private String sampleAnswer;

    @Column(name = "reference_score", nullable = false)
    private int referenceScore;
}
