package kr.ac.kopo.ttd.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MutableStateDefenseTest {

    @Test
    void 채점_세부_목록은_외부_변경으로부터_보호된다() {
        Attempt attempt = Attempt.builder()
                .userId(1L)
                .problem(problem())
                .status(AttemptStatus.GRADING)
                .endsAt(LocalDateTime.now().plusMinutes(1))
                .build();
        List<RubricCriterion> criteria = new ArrayList<>(List.of(
                new RubricCriterion("요구사항", 30, 40, "충족")));

        attempt.grade(75, 80, 77, "양호", criteria);
        criteria.clear();

        assertThat(attempt.getRubricDetail()).hasSize(1);
    }

    @Test
    void 문제_요구사항과_제약조건은_외부_변경으로부터_보호된다() {
        Problem problem = problem();
        List<String> requirements = new ArrayList<>(List.of("요구사항"));
        List<String> constraints = new ArrayList<>(List.of("제약조건"));

        problem.updateContent("문제", Difficulty.L1, ProblemType.CLASSIFY, SourceType.AUTO_GRADED,
                "설명", requirements, constraints, null, 3);
        requirements.clear();
        constraints.clear();

        assertThat(problem.getRequirements()).containsExactly("요구사항");
        assertThat(problem.getConstraints()).containsExactly("제약조건");
    }

    private static Problem problem() {
        return Problem.builder()
                .title("문제")
                .difficulty(Difficulty.L1)
                .type(ProblemType.CLASSIFY)
                .sourceType(SourceType.AUTO_GRADED)
                .description("설명")
                .requirements(List.of())
                .constraints(List.of())
                .build();
    }
}
