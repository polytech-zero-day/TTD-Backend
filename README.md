# TTD-Backend

**TTD(Text To Develop)** — "코드를 직접 짜는 능력"이 아니라 **AI에게 원하는 결과물을 정확히 지시하고 검증하는 능력**을 진단하는 플랫폼의 백엔드입니다.

응시자는 문제에서 요구하는 규칙·제약을 읽고 **프롬프트를 작성**하며, LLM이 만들어낸 산출물을 **루브릭 기반으로 채점**해 정확성·효율성 점수와 리더보드 순위를 산출합니다.

---

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| Language / Runtime | Java 17 (Gradle toolchain) |
| Framework | Spring Boot 4.1.0, Spring AI 2.0.0 |
| 인증 | Spring Security + JWT (jjwt 0.13) — Access Token(본문) + Refresh Token(httpOnly 쿠키) |
| 영속성 | Spring Data JPA / Hibernate, H2(로컬·테스트), PostgreSQL(운영) |
| AI | Spring AI OpenAI (`ChatClient`) — 채점·채팅용 모델 분리 |
| 캐시 / 메시징 | Redis(LLM 응답 캐싱), RabbitMQ(샌드박스 실행·채점 큐, AMQP) |
| 결제 | PortOne V2 빌링키 구독 결제 (데모용 Mock 승인 모드 지원) |
| API 문서 | springdoc OpenAPI (Swagger UI) |
| 보안 점검 | Find Security Bugs (SpotBugs 6.4.4 + findsecbugs 1.14.0) |

---

## 주요 기능

- **인증/인가** — 회원가입·로그인·토큰 재발급·로그아웃. Access Token은 응답 본문, Refresh Token은 `httpOnly` 쿠키(`ttd_refresh`)로 관리. `USER`/`ADMIN` 권한 분리.
- **문제(Problem)** — 규칙 기반(AUTO_GRADED)·서술형(RUBRIC_ONLY)·스켈레톤 개선형 등 유형별 문제. `DRAFT → PENDING → ACTIVE` 상태 흐름.
- **응시(Attempt)** — 문제별 응시 시작 → LLM과 프롬프트 대화 → 초안 저장 → 제출. 제한 시간·메시지 한도 관리, 빈 응시 자동 폐기(`ABANDONED`).
- **채점(Grading)** — 루브릭 기준 LLM 채점으로 정확성·효율성 점수 산출, 재채점(regrade) 지원.
- **캘리브레이션(Calibration)** — 문제별 상/중/하 기준 샘플을 채점기에 돌려 채점 일관성(티어 일치율)을 측정.
- **리더보드/통계** — 점수 랭킹, 개인 통계, 산점도(scatter) 데이터.
- **구독/결제** — PortOne 빌링키 기반 월간 구독, 기간 만료 예약 해지(cancel-at-period-end). 유료(PAID) 혜택: 상위 AI 모델 응시, 응시 무제한, 프롬프트 무제한.
- **관리자** — 문제·회원 관리, AI 모델 설정(용도별 모델 지정), 캘리브레이션 실행.

---

## 프로젝트 구조

```
src/main/java/kr/ac/kopo/ttd
├── ai          # Spring AI 클라이언트·프롬프트
├── common      # 공통 (상수, 컨버터, 암호화, 예외, JWT)
├── config      # 설정 및 시드 초기화(ProblemSeedInitializer)
├── controller  # REST 컨트롤러
├── domain      # JPA 엔티티
├── dto         # 요청/응답 DTO
├── grading     # 루브릭 채점기·캘리브레이션 러너
├── payment     # PortOne 결제 연동
├── repository  # Spring Data JPA 리포지토리
├── sandbox     # 코드 실행 샌드박스 연동
└── service     # 비즈니스 로직
```

---

## API 개요

| 영역 | Base Path | 주요 엔드포인트 |
|---|---|---|
| 인증 | `/api/auth` | `signup`, `login`, `refresh`, `logout` |
| 문제(공개) | `/api/problems` | 목록, `{id}` 상세 |
| 응시 | `/api/attempts` | `current`, `{id}/messages`, `{id}/draft`, `{id}/submit`, `{id}/result`, `{id}/regrade`, `my`, `my/stats`, `scatter` |
| 리더보드(공개) | `/api/leaderboard` | 랭킹 조회 |
| 구독 | `/api/subscriptions/me` | 내 구독 상태 |
| 프로필 | `/api/users/me` | `nickname` 변경 |
| 관리자 | `/api/admin/*` | `problems`, `users`, `settings/ai-models/{purpose}`, `calibration/run` |

> 전체 명세는 앱 기동 후 Swagger UI(`/swagger-ui.html`)에서 확인할 수 있습니다.

---

## 로컬 실행

### 사전 준비
- JDK 17+
- OpenAI API Key
- (기능 전체 사용 시) Redis, RabbitMQ

### 실행
```bash
# 기본 프로필은 local (H2 인메모리 + 데모 Mock 결제)
export OPENAI_API_KEY=sk-...
./gradlew bootRun
```

기동 시 `ProblemSeedInitializer`가 기본 10문제 + 캘리브레이션 샘플 30건 + "MVP 기본 10문제" 세트를 멱등하게 시딩합니다.

### 주요 환경 변수

| 변수 | 설명 | 기본값(local) |
|---|---|---|
| `OPENAI_API_KEY` | OpenAI API 키 | `local-openai-key` |
| `AI_MODEL` / `AI_PREMIUM_CHAT_MODEL` | 기본 / 유료 응시 모델 | `gpt-5.4-mini` / `gpt-5.4` |
| `PAYMENT_MOCK_ENABLED` | 데모 Mock 결제 승인 모드 | `true`(local) / `false`(운영) |
| `JPA_DDL_AUTO` | Hibernate DDL 모드 | `update`(local) / `validate`(운영) |
| `CORS_ALLOWED_ORIGINS` | 허용 오리진 | `http://localhost:5173` |
| `PORTONE_*` | PortOne store/secret/channel | 더미값(local) |
| `AUTH_REFRESH_COOKIE_SECURE` / `_SAME_SITE` | Refresh 쿠키 보안 옵션 | `false` / `Lax`(local) |

> 운영 프로필에서는 AES/HMAC/JWT 키, PortOne 자격증명, 관리자 초기 비밀번호를 반드시 환경 변수로 주입해야 합니다.

---

## 테스트 & 보안 점검

```bash
./gradlew test           # 단위·통합 테스트 (H2 create-drop)
./gradlew securityCheck  # Find Security Bugs 리포트 생성
./gradlew clean test securityCheck --no-daemon   # CI와 동일
```

`securityCheck`는 도입 초기 단계라 발견 항목이 있어도 빌드를 막지 않고(`ignoreFailures=true`) 리포트를 우선 확인합니다.

### CI
`develop` 대상 push·PR에서 GitHub Actions가 `test + securityCheck`를 자동 실행합니다. (`.github/workflows/ci.yml`)

---

## 프로필

| 프로필 | 용도 | 특징 |
|---|---|---|
| `local` (기본) | 로컬 개발 | H2 콘솔·`ddl-auto: update`, Mock 결제 활성, Secure 쿠키 off |
| `test` | 테스트 | H2 `create-drop`, 더미 키 주입 |
| (운영) | 배포 | PostgreSQL, `ddl-auto: validate`, 실 자격증명 필수 |

---

## 팀

폴리텍 웹개발 프로젝트 — TTD(Text To Develop)

| 파트 | 담당 | 영역 |
|---|---|---|
| A | 윤여훈 | 문제 도메인 |
| B | 고윤 | 백오피스 · 인증 · 결제 |
| C | 한성민 | 응시 · 실행 · 채점 |
| D | 차윤희 | 리포트 · 리더보드 |
