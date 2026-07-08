package kr.ac.kopo.ttd.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.ac.kopo.ttd.common.converter.StringListJsonConverter;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "problems")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProblemType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private SourceType sourceType;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private List<String> requirements;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "problem_constraints", nullable = false, columnDefinition = "text")
    private List<String> constraints;

    @Column(name = "skeleton_code", columnDefinition = "text")
    private String skeletonCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private ProblemStatus status = ProblemStatus.DRAFT;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 3;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void updateContent(String title, Difficulty difficulty, ProblemType type, SourceType sourceType,
                              String description, List<String> requirements, List<String> constraints,
                              String skeletonCode, int maxAttempts) {
        this.title = title;
        this.difficulty = difficulty;
        this.type = type;
        this.sourceType = sourceType;
        this.description = description;
        this.requirements = requirements;
        this.constraints = constraints;
        this.skeletonCode = skeletonCode;
        this.maxAttempts = maxAttempts;
    }

    public void changeStatus(ProblemStatus target) {
        this.status = target;
    }
}
