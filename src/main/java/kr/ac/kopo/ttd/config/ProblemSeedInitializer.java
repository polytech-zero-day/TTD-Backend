package kr.ac.kopo.ttd.config;

import kr.ac.kopo.ttd.domain.CalibrationSample;
import kr.ac.kopo.ttd.domain.CalibrationTier;
import kr.ac.kopo.ttd.domain.Difficulty;
import kr.ac.kopo.ttd.domain.Problem;
import kr.ac.kopo.ttd.domain.ProblemSet;
import kr.ac.kopo.ttd.domain.ProblemSetItem;
import kr.ac.kopo.ttd.domain.ProblemStatus;
import kr.ac.kopo.ttd.domain.ProblemType;
import kr.ac.kopo.ttd.domain.SourceType;
import kr.ac.kopo.ttd.domain.TestCase;
import kr.ac.kopo.ttd.dto.ProblemCreateRequest;
import kr.ac.kopo.ttd.dto.ProblemStatusUpdateRequest;
import kr.ac.kopo.ttd.repository.CalibrationSampleRepository;
import kr.ac.kopo.ttd.repository.ProblemRepository;
import kr.ac.kopo.ttd.repository.ProblemSetItemRepository;
import kr.ac.kopo.ttd.repository.ProblemSetRepository;
import kr.ac.kopo.ttd.repository.TestCaseRepository;
import kr.ac.kopo.ttd.service.ProblemService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * PromptRank 통합본(v3)의 기본 10문제 + 캘리브레이션 30건 + TestCase(AUTO_GRADED) + "MVP 기본 10문제" 세트를
 * 앱 기동 시 시딩한다. H2는 인메모리라 재기동마다 비지만, 항목별 existsBy/countBy 확인으로 중복 생성을
 * 막는다(멱등). 문제는 서비스로 생성(스켈레톤 정합성 검증 통과) 후 draft → pending → active로 전환하고,
 * 자식 데이터는 서비스가 없으므로 리포지토리로 직접 저장한다.
 * <p>
 * 캘리브레이션 sample_answer는 상/중/하 티어별 실제 응시 프롬프트(가답안)로 채워 루브릭 채점기의
 * 기준선 측정에 그대로 쓴다. 문제 7~10의 TestCase 입력/기대출력은 아직 구체 데이터가 없어
 * "[임시]" 요약 텍스트로 두었으며, 실제 데이터 확보 후 교체 예정이다.
 */
@Component
public class ProblemSeedInitializer implements ApplicationRunner {

    private static final int MAX_ATTEMPTS = 3;
    private static final String MVP_SET_NAME = "MVP 기본 10문제";

    private final ProblemRepository problemRepository;
    private final ProblemService problemService;
    private final CalibrationSampleRepository calibrationSampleRepository;
    private final TestCaseRepository testCaseRepository;
    private final ProblemSetRepository problemSetRepository;
    private final ProblemSetItemRepository problemSetItemRepository;

    public ProblemSeedInitializer(ProblemRepository problemRepository,
                                  ProblemService problemService,
                                  CalibrationSampleRepository calibrationSampleRepository,
                                  TestCaseRepository testCaseRepository,
                                  ProblemSetRepository problemSetRepository,
                                  ProblemSetItemRepository problemSetItemRepository) {
        this.problemRepository = problemRepository;
        this.problemService = problemService;
        this.calibrationSampleRepository = calibrationSampleRepository;
        this.testCaseRepository = testCaseRepository;
        this.problemSetRepository = problemSetRepository;
        this.problemSetItemRepository = problemSetItemRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Problem> orderedProblems = new ArrayList<>();
        for (ProblemSeed seed : problemSeeds()) {
            Problem problem = problemRepository.findByTitle(seed.problem().title())
                    .orElseGet(() -> createActiveProblem(seed.problem()));
            orderedProblems.add(problem);
            seedCalibrations(problem, seed.calibrations());
            seedTestCase(problem, seed);
        }
        seedProblemSet(orderedProblems);
    }

    private Problem createActiveProblem(ProblemCreateRequest request) {
        Long id = problemService.createProblem(request).id();
        problemService.changeStatus(id, new ProblemStatusUpdateRequest(ProblemStatus.PENDING));
        problemService.changeStatus(id, new ProblemStatusUpdateRequest(ProblemStatus.ACTIVE));
        return problemRepository.findById(id).orElseThrow();
    }

    private void seedCalibrations(Problem problem, List<CalibrationSeed> calibrations) {
        if (calibrationSampleRepository.countByProblemId(problem.getId()) > 0) {
            return;
        }
        for (CalibrationSeed calibration : calibrations) {
            calibrationSampleRepository.save(CalibrationSample.builder()
                    .problem(problem)
                    .tier(calibration.tier())
                    .referenceScore(calibration.referenceScore())
                    .sampleAnswer(calibration.sampleAnswer())
                    .build());
        }
    }

    private void seedTestCase(Problem problem, ProblemSeed seed) {
        if (problem.getSourceType() != SourceType.AUTO_GRADED) {
            return;
        }
        if (testCaseRepository.existsByProblemId(problem.getId())) {
            return;
        }
        testCaseRepository.save(TestCase.builder()
                .problem(problem)
                .input(seed.testInput())
                .expectedOutput(seed.testExpected())
                .isHidden(false)
                .build());
    }

    private void seedProblemSet(List<Problem> orderedProblems) {
        if (problemSetRepository.existsByName(MVP_SET_NAME)) {
            return;
        }
        ProblemSet set = problemSetRepository.save(ProblemSet.builder().name(MVP_SET_NAME).build());
        int displayOrder = 1;
        for (Problem problem : orderedProblems) {
            problemSetItemRepository.save(ProblemSetItem.builder()
                    .problemSet(set)
                    .problem(problem)
                    .displayOrder(displayOrder++)
                    .build());
        }
    }

    private static List<CalibrationSeed> calibrations(int high, String highText,
                                                      int mid, String midText,
                                                      int low, String lowText) {
        return List.of(
                new CalibrationSeed(CalibrationTier.HIGH, high, highText),
                new CalibrationSeed(CalibrationTier.MID, mid, midText),
                new CalibrationSeed(CalibrationTier.LOW, low, lowText)
        );
    }

    private List<ProblemSeed> problemSeeds() {
        return List.of(
                // 그룹 1. 규칙 기반 (문제 1~4, AUTO_GRADED) — 문서의 구체 입력/기대출력 사용
                new ProblemSeed(
                        new ProblemCreateRequest(
                                "고객 문의 라우팅 판정",
                                Difficulty.L1,
                                ProblemType.CLASSIFY,
                                SourceType.AUTO_GRADED,
                                "고객센터에 접수된 문의를 사전에 정의된 키워드 규칙에 따라 담당 부서로 배정하고 주문번호를 추출한다.",
                                List.of(
                                        "키워드 매칭으로 카테고리 판별(대소문자 무시): 환불[환불/refund], 배송[배송/택배/배송조회], 제품[불량/사이즈/색상], 그 외 기타",
                                        "여러 카테고리 키워드가 동시에 등장하면 우선순위 환불 > 배송 > 제품 > 기타 순으로 하나만 선택",
                                        "주문번호 패턴(ORD- + 숫자 6자리)을 추출하고, 없으면 null"
                                ),
                                List.of(
                                        "문의는 최대 30건, 각 텍스트 500자 이내",
                                        "출력은 입력 순서를 유지하며 지정된 필드 외 추가 금지",
                                        "명시된 키워드 규칙 외의 판단으로 카테고리를 바꾸지 않을 것"
                                ),
                                null,
                                MAX_ATTEMPTS
                        ),
                        calibrations(
                                95, """
                                아래 고객 문의 리스트를 분류하는 작업이야. 각 문의의 text를 보고 다음 규칙대로 판정해줘.

                                1) 카테고리 판별(대소문자 구분 없이 키워드 매칭):
                                   - '환불' 또는 'refund' 포함 → 환불
                                   - '배송' 또는 '택배' 또는 '배송조회' 포함 → 배송
                                   - '불량' 또는 '사이즈' 또는 '색상' 포함 → 제품
                                   - 위 어디에도 안 걸리면 → 기타
                                2) 여러 카테고리 키워드가 동시에 나오면 환불 > 배송 > 제품 > 기타 우선순위로 딱 하나만 고른다.
                                3) 주문번호는 'ORD-' 다음에 숫자 6자리(예: ORD-123456) 패턴을 추출하고, 없으면 null.

                                출력은 입력 순서 그대로 유지하고, 각 항목에 id / category / order_no 세 필드만 넣어줘. 그 외 필드나 설명은 붙이지 마.""",
                                68, """
                                고객 문의들을 카테고리별로 분류해줘. 환불이나 refund 있으면 환불, 배송·택배 관련이면 배송, 불량·사이즈·색상 얘기면 제품, 나머지는 기타로 해줘. 대소문자는 무시하고. 그리고 ORD-123456 같은 주문번호 있으면 뽑아주고 없으면 null로. 결과는 id, category, order_no로 보여줘.""",
                                30, "이 문의들 카테고리 좀 분류해줘"
                        ),
                        """
                        [
                          {"id": 1, "text": "ORD-123456 주문건 환불 요청드립니다. 배송은 이미 왔어요."},
                          {"id": 2, "text": "사이즈가 안 맞아서 교환하고 싶어요"},
                          {"id": 3, "text": "그냥 궁금한 게 있어요"}
                        ]
                        """,
                        """
                        [
                          {"id": 1, "category": "환불", "order_no": "ORD-123456"},
                          {"id": 2, "category": "제품", "order_no": null},
                          {"id": 3, "category": "기타", "order_no": null}
                        ]
                        """
                ),
                new ProblemSeed(
                        new ProblemCreateRequest(
                                "표준 라이브러리만으로 CSV 파서 구현",
                                Difficulty.L1,
                                ProblemType.CONSTRAINT,
                                SourceType.AUTO_GRADED,
                                "값 내부에 쉼표가 포함될 수 있는 CSV 한 줄을 파싱 라이브러리 없이 올바르게 분리한다.",
                                List.of(
                                        "큰따옴표로 감싸인 필드 내부의 쉼표는 구분자로 취급하지 않는다",
                                        "큰따옴표 두 개(\"\")는 이스케이프된 하나의 큰따옴표로 처리한다",
                                        "필드 앞뒤 공백은 제거하지 않는다"
                                ),
                                List.of(
                                        "csv 등 파싱 라이브러리 사용 금지 — 문자열 처리만으로 구현",
                                        "Python 3.x, PEP 8 준수(4칸 들여쓰기, snake_case)",
                                        "함수 시그니처: parse_csv_line(line: str) -> list[str]"
                                ),
                                null,
                                MAX_ATTEMPTS
                        ),
                        calibrations(
                                95, """
                                쉼표로 구분된 CSV 한 줄을 파싱하는 parse_csv_line(line: str) -> list[str] 함수를 만들어줘. 단, csv 같은 파싱 라이브러리는 쓰지 말고 문자열을 직접 한 글자씩 순회하면서 처리해줘.

                                규칙:
                                - 큰따옴표로 감싸인 필드 안의 쉼표는 구분자로 보지 않는다.
                                - 따옴표 안에서 큰따옴표 두 개("")가 연속으로 나오면 이스케이프된 하나의 큰따옴표(")로 처리한다.
                                - 필드 앞뒤 공백은 임의로 제거하지 않는다.

                                지금 따옴표 안인지 밖인지 상태 플래그를 하나 두고, 문자 단위로 돌면서 필드를 잘라주면 돼. Python 3 기준 PEP 8(4칸 들여쓰기, snake_case) 지켜줘.""",
                                65, """
                                CSV 한 줄을 파싱하는 parse_csv_line(line) 함수 만들어줘. 큰따옴표로 감싼 필드 안의 쉼표는 구분자로 처리하면 안 돼. 라이브러리 쓰지 말고 문자열 순회로 직접 구현하고, 앞뒤 공백은 그대로 두고. PEP 8 지켜줘.""",
                                25, "csv 파일 한 줄 파싱해서 리스트로 만들어줘. import csv 써서 간단하게."
                        ),
                        """
                        "김민준","서울, 강남구",28
                        "이서준","부산 ""해운대""구",31
                        """,
                        """
                        ["김민준", "서울, 강남구", "28"]
                        ["이서준", '부산 "해운대"구', "31"]
                        """
                ),
                new ProblemSeed(
                        new ProblemCreateRequest(
                                "브랜드 사이즈 매칭 통계",
                                Difficulty.L1,
                                ProblemType.ANALYSIS_BASIC,
                                SourceType.AUTO_GRADED,
                                "브랜드별 사이즈 데이터와 고객 요청이 주어질 때 사이즈 판정 결과 분포를 집계한다.",
                                List.of(
                                        "키·가슴·허리가 모두 사이즈 범위(최소 이상 최대 이하)에 드는 사이즈를 찾는다",
                                        "여럿이면 가장 작은 사이즈(입력 순서상 먼저)를 채택한다",
                                        "없으면 UP(모든 치수가 최대보다 큼)/DOWN(모든 치수가 최소보다 작음)/MISMATCH(그 외)로 판정",
                                        "브랜드가 없으면 UNKNOWN",
                                        "전체 요청에 대해 판정 결과별 건수를 집계한다"
                                ),
                                List.of(
                                        "판정 로직은 규칙을 정확히 구현",
                                        "집계는 판정 유형·건수만 (추가 통계 금지)"
                                ),
                                null,
                                MAX_ATTEMPTS
                        ),
                        calibrations(
                                92, """
                                브랜드별 사이즈 표와 고객 요청 데이터로 사이즈 판정 분포를 집계하는 작업이야. 각 요청마다 아래 순서로 판정해줘.

                                1) 요청한 브랜드가 사이즈 표에 없으면 UNKNOWN.
                                2) 브랜드가 있으면, 키·가슴·허리 세 치수가 '모두' 어떤 사이즈의 (최소 이상 최대 이하) 범위에 드는 사이즈를 찾는다. 해당되는 사이즈가 여러 개면 입력 순서상 가장 먼저(가장 작은) 나온 사이즈를 채택.
                                3) 맞는 사이즈가 없으면: 세 치수가 모두 최대보다 크면 UP, 세 치수가 모두 최소보다 작으면 DOWN, 그 외는 MISMATCH.

                                마지막에 전체 요청을 돌면서 판정 결과별 건수만 집계해줘. 추가 통계는 만들지 말고.""",
                                60, """
                                브랜드 사이즈 표 기준으로 각 고객 요청의 사이즈를 판정해줘. 키·가슴·허리가 범위에 맞는 사이즈를 찾고, 여러 개면 제일 작은 걸로. 맞는 게 없으면 치수가 크면 UP, 작으면 DOWN, 아니면 MISMATCH로. 브랜드 없으면 UNKNOWN. 마지막에 판정별 건수 세줘.""",
                                20, "이 사이즈 데이터 보고 고객들 사이즈 판정해서 통계 좀 내줘"
                        ),
                        """
                        1,6
                        Musinsa,3
                        S,160,170,85,95,70,80
                        M,165,175,90,100,75,85
                        L,170,180,95,105,80,90
                        Musinsa,160,94,80
                        Musinsa,175,95,85
                        Nike,170,96,80
                        Musinsa,182,106,91
                        Musinsa,155,80,61
                        Musinsa,180,95,70
                        """,
                        """
                        Musinsa,S / Musinsa,M / Nike,UNKNOWN / Musinsa,UP / Musinsa,DOWN / Musinsa,MISMATCH
                        집계: S 1, M 1, UNKNOWN 1, UP 1, DOWN 1, MISMATCH 1
                        """
                ),
                new ProblemSeed(
                        new ProblemCreateRequest(
                                "매장 픽업 예약 순차 처리",
                                Difficulty.L2,
                                ProblemType.ANALYSIS_ADV,
                                SourceType.AUTO_GRADED,
                                "여러 매장의 재고·시간대별 한도를 고려해 예약 요청을 순서대로 처리하고, 실패 시 우선순위가 가장 높은 사유 하나만 판정한다.",
                                List.of(
                                        "스토어 ID 존재 검증(실패 우선순위 1: STORE)",
                                        "희망 시간이 오픈 이상 마감 미만(2: TIME)",
                                        "시간대(시 단위) 기존 예약 수 < 시간당 한도(3: FULL)",
                                        "요청 수량 ≤ 재고(4: STOCK)",
                                        "성공 시 재고 차감·예약 수 +1, 입력 순서대로 처리",
                                        "결과·총 성공 건수 출력 및 실패 사유별 분포·최대 병목 사유 1문장 판단"
                                ),
                                List.of(
                                        "입력 순서대로 순차 처리(동시성 불필요)",
                                        "우선순위가 겹치면 가장 높은 사유 하나만 출력"
                                ),
                                null,
                                MAX_ATTEMPTS
                        ),
                        calibrations(
                                90, """
                                매장 픽업 예약 요청을 입력 순서대로 하나씩 처리하는 작업이야. 각 요청을 아래 우선순위 순서로 검증하고, 실패하면 가장 높은 우선순위의 사유 하나만 판정해줘.

                                1) STORE: 요청한 스토어 ID가 존재하는지
                                2) TIME: 희망 시간이 오픈 시간 이상, 마감 시간 미만인지
                                3) FULL: 그 시간대(시 단위)의 기존 예약 수가 시간당 한도 미만인지
                                4) STOCK: 요청 수량이 남은 재고 이하인지

                                네 가지를 다 통과하면 성공(OK)이고, 성공한 경우에만 해당 상품 재고를 요청 수량만큼 차감하고 그 시간대 예약 수를 1 늘려줘. 이 상태(재고·예약 수)는 다음 요청 처리에 그대로 이어져야 해.

                                출력은 각 요청의 결과(id, OK 또는 FAIL+사유)와 총 성공 건수, 그리고 실패 사유별 분포를 보고 어떤 사유가 가장 큰 병목인지 한 문장으로 정리해줘.""",
                                62, """
                                매장 예약 요청들을 순서대로 처리해줘. 스토어가 있는지(STORE), 시간이 영업시간 안인지(TIME), 시간대 예약이 꽉 찼는지(FULL), 재고가 충분한지(STOCK) 확인해서 안 되면 FAIL이랑 사유를 붙이고, 되면 OK. 성공하면 재고 줄이고 예약 수 올려주고. 총 성공 몇 건인지도 알려줘.""",
                                28, "이 예약 요청들 처리해서 되는지 안 되는지 알려줘"
                        ),
                        """
                        2,7
                        1,10,20,3
                        2,11,21,2
                        1,A001:10,A002:5
                        2,A001:8,A003:3
                        1,1,A001,2,10:30
                        2,1,A001,3,10:00
                        3,1,A001,6,10:45
                        4,1,A002,2,09:30
                        5,2,A001,5,15:00
                        6,2,A003,5,20:30
                        7,3,A001,1,12:00
                        """,
                        """
                        1,OK / 2,OK / 3,FAIL,STOCK / 4,FAIL,TIME / 5,OK / 6,FAIL,STOCK / 7,FAIL,STORE
                        총 성공: 3
                        """
                ),
                // 그룹 2. 서술형 (문제 5~6, RUBRIC_ONLY) — TestCase 없음
                new ProblemSeed(
                        new ProblemCreateRequest(
                                "스터디룸 예약 정책 설계",
                                Difficulty.L2,
                                ProblemType.AMBIGUOUS,
                                SourceType.RUBRIC_ONLY,
                                "교내 스터디룸 예약의 공정성 문제를 해결할 정책을 스스로 정의하고 근거와 함께 설계한다.",
                                List.of(
                                        "\"공정성\" 정의를 스스로 가정하고 근거 제시",
                                        "예약 정책 규칙 최소 3가지 구체적 설계",
                                        "각 규칙의 선택 근거 1~2문장",
                                        "놓칠 수 있는 예외 상황 1가지 이상 발견 및 대응"
                                ),
                                List.of(
                                        "코드 구현 불필요 — 정책 설계 문서가 결과물",
                                        "공정성 정의 없이 규칙만 나열하면 감점",
                                        "통계적 근거를 지어내지 말 것"
                                ),
                                null,
                                MAX_ATTEMPTS
                        ),
                        calibrations(
                                90, """
                                교내 스터디룸 예약이 선착순이다 보니 생기는 불공정 문제를 해결하는 정책을 설계해볼게.

                                먼저 '공정성'을 이렇게 정의할게: 지금의 핵심 불공정은 예약 성공 여부가 '누가 더 좋은 네트워크·기기로 정각에 클릭했느냐'에 좌우된다는 점이야. 즉 접근 자격이 아니라 반응 속도가 자원 배분을 결정하는 상태. 그래서 '반복 이용 편중을 줄이고, 클릭 속도 경쟁을 완화하는 것'을 공정성 목표로 잡을게.

                                정책 규칙 3가지:
                                1) 1인당 주간 예약 상한(주 3시간): 소수 다이용자에게 슬롯이 쏠리는 걸 구조적으로 막기 위해.
                                2) 정각 오픈 대신 전날 하루 신청 접수 후 최근 이용이 적은 사람에게 가중치를 주는 배정: 클릭 속도 경쟁 자체를 없애기 위해.
                                3) 노쇼 페널티(2회 노쇼 시 1주 예약 제한): 잡아만 두고 안 쓰는 자원 낭비를 막기 위해.

                                놓치기 쉬운 예외: 팀 프로젝트처럼 여러 명이 같은 방을 함께 써야 하는 경우, 1인 상한 규칙이 오히려 팀 활동을 막을 수 있어. 이 경우엔 '그룹 예약 슬롯'을 따로 두고, 인원수를 등록하면 대표 1명 명의로 잡되 상한은 팀 단위로 계산하도록 예외 처리할게.""",
                                58, """
                                스터디룸 예약 정책을 이렇게 만들면 좋겠어.
                                1) 1인당 예약 시간에 제한을 둔다.
                                2) 노쇼하면 페널티를 준다.
                                3) 예약은 정해진 시간에 오픈한다.
                                이렇게 하면 공정하게 쓸 수 있을 것 같아. 각 규칙은 독점을 막고 자원을 아끼기 위한 거야.""",
                                25, "예약이 자꾸 몰려서 문제니까 서버를 증설하거나 방을 더 만들면 되지 않을까?"
                        ),
                        null,
                        null
                ),
                new ProblemSeed(
                        new ProblemCreateRequest(
                                "매장 운영 우선순위 판단 보고서",
                                Difficulty.L2,
                                ProblemType.REPORT,
                                SourceType.RUBRIC_ONLY,
                                "매장 픽업 실패 통계를 바탕으로 어느 매장을 먼저 개선할지 데이터 기반 보고서를 작성한다.",
                                List.of(
                                        "매장별 실패율·실패 사유 분포 비교",
                                        "개선 우선순위 기준을 스스로 설계",
                                        "우선순위 1~2개 매장 선정 및 데이터 기반 논증",
                                        "보고서 형식(요약 → 방법 → 결과 → 제안)"
                                ),
                                List.of(
                                        "단일 지표만으로 결론 내리면 감점 — 다축 판단",
                                        "존재하지 않는 지표 임의 생성 금지"
                                ),
                                null,
                                MAX_ATTEMPTS
                        ),
                        calibrations(
                                88, """
                                매장 픽업 실패 통계로 어느 매장을 먼저 개선할지 보고서를 써볼게.

                                [요약] 실패율만 보면 A매장이 가장 높지만, 실패 건수의 절대량과 고객 대기 영향까지 함께 보면 우선순위가 달라진다.

                                [방법] 단일 지표로 판단하지 않기 위해 세 축으로 비교했어: (1) 실패율(실패/전체), (2) 실패 절대 건수, (3) 실패 사유 중 STOCK·FULL처럼 대기·이탈로 이어지는 사유의 비중. 세 축을 '실패율 × 평균 대기 영향'으로 가중 점수화해서 정렬했어.

                                [결과] 비교표 기준으로 B매장은 실패율은 2위지만 실패 건수와 STOCK 사유 비중이 가장 높아 종합 점수 1위, A매장이 2위였어.

                                [제안] 1순위 B매장(재고 보충·시간당 한도 상향), 2순위 A매장(오픈 시간 조정)으로 개선을 권장해. 실패율 단일 지표만 봤다면 놓쳤을 부분이야.""",
                                55, """
                                매장별 실패율을 계산해서 높은 순으로 정렬했어. 실패율이 제일 높은 A매장을 가장 먼저 개선하면 될 것 같아. A매장부터 순서대로 개선하는 걸 추천해.""",
                                22, "A매장 실패 12건, B매장 실패 9건, C매장 실패 5건이야. 이 숫자들 참고해."
                        ),
                        null,
                        null
                ),
                // 그룹 3. 스켈레톤 개선형 (문제 7~10, AUTO_GRADED) — TestCase는 [임시] 플레이스홀더
                new ProblemSeed(
                        new ProblemCreateRequest(
                                "CSV 로드 후 기초 통계 출력",
                                Difficulty.L1,
                                ProblemType.SKELETON_STAT,
                                SourceType.AUTO_GRADED,
                                "sensor_log.csv를 읽어 출력만 하는 스켈레톤을 기초 통계 기능까지 발전시킨다.",
                                List.of(
                                        "sensor_type별 value의 평균·최댓값·최솟값 출력",
                                        "status가 error인 행의 개수 출력",
                                        "location별 행 수를 많은 순으로 정렬해 출력"
                                ),
                                List.of(
                                        "제공된 스켈레톤을 출발점으로 사용(pandas 유지)",
                                        "컬럼명·스키마 임의 변경 금지",
                                        "요구된 3가지 출력을 모두 포함"
                                ),
                                """
                                import pandas as pd

                                csv_file_path = 'sensor_log.csv'
                                df = pd.read_csv(csv_file_path)
                                print(df)
                                """,
                                MAX_ATTEMPTS
                        ),
                        calibrations(
                                93, """
                                아래는 sensor_log.csv를 읽어서 그대로 출력만 하는 코드야. 이 스켈레톤(pandas의 read_csv 부분)은 그대로 재사용하고, print(df) 대신 아래 세 가지 통계를 출력하도록 발전시켜줘. 컬럼명이나 스키마는 바꾸지 말고.

                                1) sensor_type별로 value의 평균·최댓값·최솟값을 출력
                                2) status가 'error'인 행의 개수를 출력
                                3) location별 행 수를 많은 순(내림차순)으로 정렬해서 출력

                                세 가지 출력이 모두 포함되게 해줘.""",
                                64, """
                                이 sensor_log.csv 읽는 코드에서, sensor_type별 value의 평균·최대·최소를 구하고 status가 error인 행 개수도 세서 출력하도록 바꿔줘. pandas는 그대로 쓰면 돼.""",
                                26, "이 코드 좀 분석해줘"
                        ),
                        "[임시] sensor_log.csv 샘플 데이터(sensor_id, timestamp, sensor_type, value, status, location) — 추후 실데이터로 교체",
                        "[임시] sensor_type별 평균/최대/최소 통계 표, status=error 행 개수(정수), location별 카운트 내림차순 — 추후 교체"
                ),
                new ProblemSeed(
                        new ProblemCreateRequest(
                                "최소 HTTP 서버에 라우팅 추가",
                                Difficulty.L1,
                                ProblemType.SKELETON_HTTP,
                                SourceType.AUTO_GRADED,
                                "항상 Hello World만 반환하는 최소 HTTP 서버를 표준 라이브러리만으로 경로별 응답하도록 발전시킨다.",
                                List.of(
                                        "GET /health → 상태코드 200, 본문 OK",
                                        "GET /time → 현재 시각을 HH:MM:SS 문자열로 반환",
                                        "그 외 경로 → 상태코드 404, 본문 Not Found"
                                ),
                                List.of(
                                        "표준 라이브러리만 사용(외부 웹 프레임워크 금지)",
                                        "제공된 http.server 구조를 출발점으로 유지",
                                        "포트 8080 유지"
                                ),
                                """
                                from http.server import BaseHTTPRequestHandler, HTTPServer

                                class Handler(BaseHTTPRequestHandler):
                                    def do_GET(self):
                                        self.send_response(200)
                                        self.end_headers()
                                        self.wfile.write(b"Hello World")

                                HTTPServer(('', 8080), Handler).serve_forever()
                                """,
                                MAX_ATTEMPTS
                        ),
                        calibrations(
                                92, """
                                아래는 어떤 경로로 요청해도 항상 Hello World만 반환하는 최소 HTTP 서버야. 표준 라이브러리(http.server)만 쓰고 포트 8080은 그대로 유지하면서, self.path로 경로를 분기해서 아래처럼 응답하도록 고쳐줘. 외부 웹 프레임워크는 쓰지 마.

                                - GET /health → 상태코드 200, 본문 'OK'
                                - GET /time → 상태코드 200, 현재 시각을 'HH:MM:SS' 형식 문자열로 반환
                                - 그 외 모든 경로 → 상태코드 404, 본문 'Not Found'""",
                                60, """
                                이 HTTP 서버가 /health로 오면 200에 OK를, /time으로 오면 현재 시각 HH:MM:SS를 반환하도록 self.path로 분기해서 고쳐줘. 표준 라이브러리만 쓰고 포트 8080은 유지해줘.""",
                                24, "이 서버에 라우팅 좀 추가해줘"
                        ),
                        "[임시] 요청 경로 시나리오: GET /health, GET /time, GET /unknown — 추후 실제 요청/응답 데이터로 교체",
                        "[임시] /health→200 OK, /time→200 HH:MM:SS, 그 외→404 Not Found — 추후 교체"
                ),
                new ProblemSeed(
                        new ProblemCreateRequest(
                                "로그 파서에 이상 감지 추가",
                                Difficulty.L2,
                                ProblemType.SKELETON_LOG,
                                SourceType.AUTO_GRADED,
                                "로그를 줄 단위로 세기만 하는 스켈레톤에 이상 트래픽 감지 기능을 추가한다. 로그 한 줄 형식: timestamp ip path status_code.",
                                List.of(
                                        "동일 IP가 60초 이내 30회 이상 요청한 경우를 이상 IP로 판정",
                                        "이상 IP 목록과 각 IP의 총 요청 수 출력",
                                        "status_code가 5xx인 요청 수를 별도로 집계해 출력"
                                ),
                                List.of(
                                        "표준 라이브러리(datetime 등)만 사용, 머신러닝 라이브러리 금지",
                                        "이상 판정 기준(60초/30회)을 코드에 명시적으로 반영",
                                        "제공된 파일 읽기 구조를 출발점으로 유지"
                                ),
                                """
                                count = 0
                                with open('access_log.txt') as f:
                                    for line in f:
                                        count += 1
                                print(count)
                                """,
                                MAX_ATTEMPTS
                        ),
                        calibrations(
                                89, """
                                아래는 access_log.txt를 줄 수만 세는 코드야. 로그 한 줄 형식은 'timestamp ip path status_code'(공백 구분)이고, 파일 읽는 구조는 그대로 두고 이상 트래픽 감지 기능을 추가해줘. 표준 라이브러리(datetime 등)만 쓰고 머신러닝 라이브러리는 쓰지 마.

                                - 같은 IP가 60초 이내에 30회 이상 요청한 경우를 이상 IP로 판정. IP별로 요청 시각을 모아서 60초 슬라이딩 윈도우 안의 요청 수가 30회 이상인지 검사해줘. 60초/30회 기준은 코드에 상수로 명시적으로 반영해줘.
                                - 이상 IP 목록과 각 IP의 총 요청 수를 출력.
                                - status_code가 5xx인 요청 수도 따로 집계해서 출력.""",
                                61, """
                                이 로그 파서가 IP별로 요청 수를 세서, 요청이 30회 이상인 IP를 이상 IP로 보고 목록이랑 요청 수를 출력하게 해줘. status_code가 5xx인 것도 개수 세서 출력하고. 표준 라이브러리만 써줘.""",
                                27, "이 로그에 이상 감지 기능 좀 넣어줘"
                        ),
                        "[임시] access_log.txt 샘플(timestamp ip path status_code, 공백 구분) — 추후 실데이터로 교체",
                        "[임시] 이상 IP 목록과 각 IP의 총 요청 수, 5xx 요청 수 집계 — 추후 교체"
                ),
                new ProblemSeed(
                        new ProblemCreateRequest(
                                "집계 함수를 우선순위 판정으로 확장",
                                Difficulty.L2,
                                ProblemType.SKELETON_RESERVE,
                                SourceType.AUTO_GRADED,
                                "개수만 반환하는 process 함수를 픽업 예약 규칙(문제 4와 동일)을 처리하도록 발전시킨다. 스토어 정보는 별도 딕셔너리로 주어진다고 가정한다.",
                                List.of(
                                        "각 요청을 STORE > TIME > FULL > STOCK 우선순위로 검증",
                                        "성공 시 (id, \"OK\"), 실패 시 (id, \"FAIL\", 사유) 반환",
                                        "성공 시 재고 차감·시간대 예약 수 누적, 입력 순서대로 처리",
                                        "총 성공 건수도 함께 반환"
                                ),
                                List.of(
                                        "우선순위가 겹치면 가장 높은 사유 하나만",
                                        "제공된 process(requests) 시그니처를 출발점으로 확장(반환 구조는 확장 가능)",
                                        "상태(재고·예약 수)는 요청 간 누적되어야 함"
                                ),
                                """
                                def process(requests):
                                    return len(requests)

                                # requests: [{"id":1,"store":1,"item":"A001","qty":2,"time":"10:30"}, ...]
                                """,
                                MAX_ATTEMPTS
                        ),
                        calibrations(
                                90, """
                                아래 process(requests)는 요청 개수만 반환하는 함수야. 이걸 픽업 예약 규칙을 처리하도록 확장해줘. 스토어 정보(오픈/마감/시간당 한도/재고)는 별도 딕셔너리로 주어진다고 가정하면 돼.

                                - 각 요청을 STORE > TIME > FULL > STOCK 우선순위 순서로 검증하고, 실패하면 가장 높은 우선순위의 사유 하나만.
                                - 성공이면 (id, 'OK'), 실패면 (id, 'FAIL', 사유)를 반환.
                                - 성공한 경우에만 재고를 요청 수량만큼 차감하고 그 시간대 예약 수를 1 늘려줘. 이 상태는 요청 사이에 계속 누적되어야 해.
                                - 입력 순서대로 처리하고, 결과 리스트와 함께 총 성공 건수도 반환하도록 반환 구조를 확장해줘.""",
                                63, """
                                이 process 함수가 각 요청을 STORE, TIME, FULL, STOCK 순서로 검사해서 되면 (id, 'OK'), 안 되면 (id, 'FAIL', 사유)를 반환하도록 확장해줘. 총 성공 건수도 같이 반환해줘.""",
                                25, "이 함수가 예약 처리하게 만들어줘"
                        ),
                        "[임시] requests 리스트와 스토어 정보(오픈/마감/시간당 한도/재고) 딕셔너리 샘플 — 추후 실데이터로 교체",
                        "[임시] (id, OK) / (id, FAIL, 사유) 결과 리스트와 총 성공 건수 — 추후 교체"
                )
        );
    }

    private record ProblemSeed(
            ProblemCreateRequest problem,
            List<CalibrationSeed> calibrations,
            String testInput,
            String testExpected
    ) {
    }

    private record CalibrationSeed(
            CalibrationTier tier,
            int referenceScore,
            String sampleAnswer
    ) {
    }
}
