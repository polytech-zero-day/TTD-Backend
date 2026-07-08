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
 * 캘리브레이션 sample_answer와 문제 7~10의 TestCase 입력/기대출력은 문서에 구체 데이터가 없어
 * "[임시]" 요약 텍스트로 채웠으며, 실제 데이터 확보 후 교체 예정이다.
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
                                95, "[임시] 정답 100% 일치. 우선순위·대소문자 무시·주문번호 패턴을 프롬프트에 모두 구체적으로 기술.",
                                68, "[임시] 3건 중 2건 일치(복합 키워드 1건 오분류). 우선순위 처리 규칙 미포함.",
                                30, "[임시] 1건만 일치, 주문번호 추출 누락. '분류해줘' 수준의 짧은 프롬프트."
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
                                95, "[임시] 상태 플래그로 문자 단위 순회, 이스케이프까지 정확 처리, PEP 8 준수, 라이브러리 미사용.",
                                65, "[임시] 기본 분리는 되나 이스케이프(\"\") 케이스 오류.",
                                25, "[임시] csv 모듈 사용으로 제약 위반."
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
                                92, "[임시] 6건 모두 정답 일치, 규칙 순서대로 정확 분기, 집계 정확.",
                                60, "[임시] 5건 일치. UP 조건에서 '세 부위 모두' 조건 누락.",
                                20, "[임시] 브랜드 존재 확인(UNKNOWN) 로직 없음, 집계 형식 불일치."
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
                                90, "[임시] 7건 모두 일치, 총 3건 정확, 'STOCK이 최다 병목' 정확 분석.",
                                62, "[임시] 6건 일치(TIME→FULL 오판, 검사 순서 오류), 병목 판단 문장 누락.",
                                28, "[임시] 재고·예약 수 상태 누적 실패로 5번부터 결과 틀어짐."
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
                                90, "[임시] 공정성을 '선착순의 네트워크 의존성' 문제로 정의→규칙 3가지 근거와 함께→팀 프로젝트 예외까지 발견해 그룹 슬롯 대안 제시.",
                                58, "[임시] 공정성 정의 없이 규칙 3가지 나열, 근거 형식적, 예외 없음.",
                                25, "[임시] '서버 증설'만 제시 — 정책 설계 요구를 인프라 문제로 치환."
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
                                88, "[임시] 다축 비교표→'실패율×대기시간' 자체 기준 점수화→2개 매장 선정 근거 명확→보고서 형식 완비.",
                                55, "[임시] 실패율 단일 지표로 정렬, 자체 기준 설계 없음.",
                                22, "[임시] 숫자 나열만, 결론·제안·형식 없음."
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
                                93, "[임시] 3가지 요구를 항목별로 명확히 지시, 스켈레톤(read_csv) 재사용 명시. 출력 3종 정답 일치.",
                                64, "[임시] sensor_type 통계·error 개수는 맞으나 location 내림차순 정렬 누락.",
                                26, "[임시] '이 코드 분석해줘'로만 지시, print(df) 유지. 통계 미구현."
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
                                92, "[임시] 3개 경로의 상태코드·본문을 표로 전달, self.path 분기 명시. 세 경로 정답 응답.",
                                60, "[임시] /health·/time은 동작하나 미정의 경로에서 404 대신 200 반환.",
                                24, "[임시] '라우팅 추가해줘'로만 지시해 Flask 생성 → 표준 라이브러리 제약 위반, 스켈레톤 폐기."
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
                                89, "[임시] '60초 슬라이딩 윈도우로 IP별 요청 시각 수집, 30회 이상 검사' 구체 지시. 이상 IP·5xx 집계 정답.",
                                61, "[임시] IP별 총 요청 수로만 판정(시간 윈도우 무시)해 '60초 이내' 조건 누락. 5xx 집계는 정확.",
                                27, "[임시] '이상 감지 넣어줘'로만 지시, 임의 기준 사용. 스켈레톤 count만 유지."
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
                                90, "[임시] 4단계 우선순위·상태 누적 규칙 명확 지시, process 확장+성공 수 함께 반환 명시. 결과 정답.",
                                63, "[임시] 우선순위 검증은 구현했으나 상태 누적 미지시로 재고 미갱신. 뒤로 갈수록 오류.",
                                25, "[임시] '이 함수 예약 처리하게 해줘'로만 지시. 우선순위·사유·누적 미반영."
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
