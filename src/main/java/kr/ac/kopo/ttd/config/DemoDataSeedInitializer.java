package kr.ac.kopo.ttd.config;

import kr.ac.kopo.ttd.common.crypto.HmacHasher;
import kr.ac.kopo.ttd.domain.Attempt;
import kr.ac.kopo.ttd.domain.AttemptStatus;
import kr.ac.kopo.ttd.domain.Problem;
import kr.ac.kopo.ttd.domain.ProblemStatus;
import kr.ac.kopo.ttd.domain.RubricCriterion;
import kr.ac.kopo.ttd.domain.UserRole;
import kr.ac.kopo.ttd.dto.UserCreateRequest;
import kr.ac.kopo.ttd.dto.UserResponse;
import kr.ac.kopo.ttd.grading.EfficiencyScorer;
import kr.ac.kopo.ttd.repository.AttemptRepository;
import kr.ac.kopo.ttd.repository.ProblemRepository;
import kr.ac.kopo.ttd.repository.UserRepository;
import kr.ac.kopo.ttd.service.UserAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 발표 시연용 데모 데이터 시딩. 리더보드·산점도·마이페이지가 풍성하게 보이도록 가짜 응시자와
 * GRADED 응시 기록을 다량 심는다. {@code @Profile("demo")}로 데모 프로필에서만 실행되어
 * 운영/일반 로컬 DB를 오염시키지 않는다.
 * <p>
 * 설계 원칙(docs/데모데이터시딩_분석.md 준수):
 * <ul>
 *   <li>USERS는 반드시 {@link UserAdminService#createUser}로 생성 — email 암호화(AES-GCM)/
 *       email_hash(HMAC)/password_hash(BCrypt)가 자동 처리된다. 직접 INSERT 금지.</li>
 *   <li>attempts는 상태전환 서비스 흐름을 우회하고 {@code Attempt.builder().status(GRADED)}로
 *       직접 저장한다. 상태전환 가드는 도메인 메서드에만 있으므로 빌더 저장으로 GRADED를 바로 만든다.</li>
 *   <li>효율 점수는 {@link EfficiencyScorer} 빈을 재사용해 실제 산식과 단일 원천을 유지한다.</li>
 * </ul>
 * {@link ProblemSeedInitializer}(@Order(1)) 다음에 실행되어야 문제가 존재하므로 {@code @Order(2)}.
 * 재기동 중복 시딩은 데모 로그인 계정 존재 여부로 막는다(멱등).
 */
@Slf4j
@Component
@Profile("demo")
@Order(2)
public class DemoDataSeedInitializer implements ApplicationRunner {

    // ── 데이터 규모 (발표용 기본값 — 재조정 시 이 상수만 변경) ──────────────────────────
    private static final int USER_COUNT = 18;                 // 데모 로그인 계정 1명 포함
    private static final int PROBLEMS_PER_USER_MIN = 5;       // 유저당 응시 문제 수 범위
    private static final int PROBLEMS_PER_USER_MAX = 9;
    private static final int MAX_ATTEMPTS_PER_PROBLEM = 2;    // 문제당 1~2회 응시(attempts 컬럼 변화)

    // ── 종합 점수 가중치 (현재 ScoreWeights와 동일 — 산식 변경 시 함께 조정) ──────────────
    private static final double WEIGHT_RUBRIC = 0.6;
    private static final double WEIGHT_EFFICIENCY = 0.4;

    // ── 재현성 ────────────────────────────────────────────────────────────────────
    private static final long RANDOM_SEED = 20260713L;        // 매 실행 동일 데이터
    private static final int SUBMITTED_WITHIN_DAYS = 21;       // 최근 N일 내 랜덤 제출 시각

    // ── 데모 로그인 계정 (시연에서 로그인해 마이페이지 확인) ──────────────────────────────
    private static final String DEMO_LOGIN_EMAIL = "demo@ttd.local";
    private static final String DEMO_LOGIN_PASSWORD = "demo1234!";
    private static final String DEMO_LOGIN_NICKNAME = "데모현우";
    private static final String DEMO_USER_PASSWORD = "seed1234!"; // 나머지 유저 공통(로그인 불필요)

    // 리더보드에 노출될 닉네임 풀(데모 로그인 계정 제외 17명 이상 필요).
    private static final List<String> NICKNAMES = List.of(
            "김서준", "이지우", "박도윤", "최하은", "정예준", "강수아", "조민준", "윤서연",
            "장하준", "임지호", "한아린", "오시우", "서지안", "신유나", "권준우", "황채원",
            "안건우", "송다은", "홍지훈", "문가은");

    /**
     * 유저 실력 티어. rubric 점수 밴드와 토큰 사용 배수(잘하는 유저일수록 토큰을 적게 써 효율↑)를
     * 함께 정의해, 산점도에서 점이 대각선으로 흩어지고 리더보드에 순위 격차가 생기도록 한다.
     */
    private enum Tier {
        HIGH(90, 100, 0.50, 0.95),   // 소수 상위권
        MID(60, 85, 0.80, 1.60),     // 다수 중위권
        LOW(40, 60, 1.20, 2.50);     // 일부 하위권

        final int rubricMin, rubricMax;
        final double tokenFactorMin, tokenFactorMax;

        Tier(int rubricMin, int rubricMax, double tokenFactorMin, double tokenFactorMax) {
            this.rubricMin = rubricMin;
            this.rubricMax = rubricMax;
            this.tokenFactorMin = tokenFactorMin;
            this.tokenFactorMax = tokenFactorMax;
        }
    }

    private final UserRepository userRepository;
    private final UserAdminService userAdminService;
    private final ProblemRepository problemRepository;
    private final AttemptRepository attemptRepository;
    private final EfficiencyScorer efficiencyScorer;
    private final HmacHasher hmacHasher;

    private final Random random = new Random(RANDOM_SEED);

    public DemoDataSeedInitializer(UserRepository userRepository,
                                   UserAdminService userAdminService,
                                   ProblemRepository problemRepository,
                                   AttemptRepository attemptRepository,
                                   EfficiencyScorer efficiencyScorer,
                                   HmacHasher hmacHasher) {
        this.userRepository = userRepository;
        this.userAdminService = userAdminService;
        this.problemRepository = problemRepository;
        this.attemptRepository = attemptRepository;
        this.efficiencyScorer = efficiencyScorer;
        this.hmacHasher = hmacHasher;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 멱등: 데모 로그인 계정이 이미 있으면 시딩을 건너뛴다.
        if (userRepository.existsByEmailHash(hmacHasher.hash(DEMO_LOGIN_EMAIL))) {
            log.info("[DemoSeed] 데모 데이터가 이미 존재하여 시딩을 건너뜁니다. (login={})", DEMO_LOGIN_EMAIL);
            return;
        }

        // 방어: 문제가 아직 없으면(순서 문제 등) 시딩하지 않는다.
        List<Problem> problems = problemRepository.findAllByStatus(ProblemStatus.ACTIVE);
        if (problems.isEmpty()) {
            log.warn("[DemoSeed] 활성 문제가 없어 데모 응시 시딩을 건너뜁니다. (ProblemSeedInitializer 이후 실행 여부 확인)");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int totalAttempts = 0;

        for (int i = 0; i < USER_COUNT; i++) {
            boolean isDemoLogin = (i == 0);
            UserResponse user = createUser(i, isDemoLogin);
            if (user == null) {
                continue; // 이메일 충돌 등으로 생성 실패 시 스킵(로그는 createUser에서)
            }
            Tier tier = tierForIndex(i);
            totalAttempts += seedAttemptsFor(user.id(), tier, isDemoLogin, problems, now);
        }

        log.info("[DemoSeed] 완료 — 유저 {}명, GRADED 응시 {}건 시딩. 데모 로그인 계정: {} / {}",
                USER_COUNT, totalAttempts, DEMO_LOGIN_EMAIL, DEMO_LOGIN_PASSWORD);
    }

    private UserResponse createUser(int index, boolean isDemoLogin) {
        String email = isDemoLogin ? DEMO_LOGIN_EMAIL : String.format("demo-user-%02d@ttd.local", index + 1);
        String password = isDemoLogin ? DEMO_LOGIN_PASSWORD : DEMO_USER_PASSWORD;
        String nickname = isDemoLogin ? DEMO_LOGIN_NICKNAME : NICKNAMES.get((index - 1) % NICKNAMES.size());
        try {
            return userAdminService.createUser(new UserCreateRequest(email, password, nickname, UserRole.USER));
        } catch (RuntimeException e) {
            log.warn("[DemoSeed] 유저 생성 실패로 스킵: {} ({})", email, e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 티어 분포: 상위권 3명, 중위권 10명, 하위권 5명(총 18). 데모 로그인 계정(index 0)은 중위권으로 두어
     * 순위표 중간에서 myRank/myPercentile이 자연스럽게 보이도록 한다.
     */
    private Tier tierForIndex(int index) {
        if (index >= 1 && index <= 3) {
            return Tier.HIGH;
        }
        if (index >= 14) {
            return Tier.LOW;
        }
        return Tier.MID;
    }

    private int seedAttemptsFor(Long userId, Tier tier, boolean isDemoLogin,
                                List<Problem> problems, LocalDateTime now) {
        List<Problem> chosen = pickProblems(problems, isDemoLogin);
        int count = 0;
        for (Problem problem : chosen) {
            // 데모 로그인 계정은 문제당 응시를 상한까지 넉넉히 부여해 마이페이지 통계가 확실히 채워지게 한다.
            int attempts = isDemoLogin
                    ? MAX_ATTEMPTS_PER_PROBLEM
                    : 1 + random.nextInt(MAX_ATTEMPTS_PER_PROBLEM);
            for (int a = 0; a < attempts; a++) {
                attemptRepository.save(buildGradedAttempt(userId, problem, tier, now));
                count++;
            }
        }
        return count;
    }

    /** 활성 문제 중 5~9개(데모 로그인 계정은 상한 근처)를 랜덤 선택. 문제 수가 적으면 전체를 쓴다. */
    private List<Problem> pickProblems(List<Problem> problems, boolean isDemoLogin) {
        int min = Math.min(PROBLEMS_PER_USER_MIN, problems.size());
        int max = Math.min(PROBLEMS_PER_USER_MAX, problems.size());
        int target = isDemoLogin ? max : (min + random.nextInt(max - min + 1));

        List<Problem> shuffled = new ArrayList<>(problems);
        Collections.shuffle(shuffled, random);
        return shuffled.subList(0, target);
    }

    private Attempt buildGradedAttempt(Long userId, Problem problem, Tier tier, LocalDateTime now) {
        int rubric = randomInRange(tier.rubricMin, tier.rubricMax);

        long budget = problem.getTokenBudget();
        double factor = tier.tokenFactorMin + random.nextDouble() * (tier.tokenFactorMax - tier.tokenFactorMin);
        long totalTokens = Math.max(1, Math.round(budget * factor));

        // 효율은 실제 EfficiencyScorer 산식으로 계산(단일 원천 유지).
        int efficiency = efficiencyScorer.score(totalTokens, budget);
        int finalScore = (int) Math.round(rubric * WEIGHT_RUBRIC + efficiency * WEIGHT_EFFICIENCY);

        LocalDateTime submittedAt = now.minusDays(random.nextInt(SUBMITTED_WITHIN_DAYS))
                .minusHours(random.nextInt(24))
                .minusMinutes(random.nextInt(60));

        return Attempt.builder()
                .userId(userId)
                .problem(problem)
                .status(AttemptStatus.GRADED)
                .endsAt(submittedAt)               // not null 필수
                .submittedAt(submittedAt)
                .messageCount(2 + random.nextInt(6))
                .totalTokens(totalTokens)
                .rubricScore(rubric)
                .efficiencyScore(efficiency)
                .finalScore(finalScore)
                .feedback(feedbackFor(finalScore))
                .rubricDetail(rubricDetail(rubric))
                .premium(random.nextInt(4) == 0)   // 약 25% 유료
                .chatModel("gpt-5.4-mini")
                .artifact("[데모] 시연용 제출 결과물")
                .build();
    }

    /** 항목별 점수 합이 전체 rubric과 일치하도록 40/30/30 배점으로 분해(결과화면 시연용). */
    private List<RubricCriterion> rubricDetail(int rubric) {
        int c1 = (int) Math.round(rubric * 0.40);
        int c2 = (int) Math.round(rubric * 0.30);
        int c3 = rubric - c1 - c2; // 나머지로 합 보정
        return List.of(
                new RubricCriterion("요구사항 충족", c1, 40, "[데모] 핵심 요구사항을 대체로 반영했습니다."),
                new RubricCriterion("근거 제시의 구체성", c2, 30, "[데모] 판단 근거를 제시했습니다."),
                new RubricCriterion("절차 설계의 타당성", c3, 30, "[데모] 처리 절차가 합리적입니다."));
    }

    private String feedbackFor(int finalScore) {
        if (finalScore >= 90) {
            return "[데모] 요구사항을 충실히 충족하고 토큰도 효율적으로 사용했습니다.";
        }
        if (finalScore >= 70) {
            return "[데모] 전반적으로 무난하나 일부 근거 제시가 더 필요합니다.";
        }
        return "[데모] 요구사항 반영과 토큰 효율 모두 개선이 필요합니다.";
    }

    private int randomInRange(int minInclusive, int maxInclusive) {
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }
}
