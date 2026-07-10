# 코드 리뷰 결과 (2026-07-10) — TTD Front/Back develop

멀티에이전트 리뷰(8 앵글 × 파인더 → 검증). **확인된 실버그 21건.** 심각도 순.
소유: C=한성민(응시·채점), B=고윤(결제·인증), 인프라=배포(윤), 정리=공통.

## 🔴 치명적

| # | 파일:라인 | 요약 | 소유 |
|---|---|---|---|
| 1 | useAttempt.ts:116 + AttemptService.java:61 | 타이머 만료 시 `startAttempt`를 만료 트리거로 쓰지만 `start()`는 `expireIfNeeded()`를 안 부름 → 자동제출 미동작, 응시 IN_PROGRESS 영구 고착, 폴링 무한 | C |
| 2 | SubscriptionService.java:104 | due 수집~청구 사이 취소된 구독을 상태 재확인 없이 청구 → CANCELED→ACTIVE 예외로 롤백 → 결제행 소멸(돈만 나감) | B |
| 3 | SubscriptionService.java:74 | 결제 실패 처리가 자기 트랜잭션에서 예외 throw → FAILED payment 행 롤백 → 승인-후-응답유실 시 원장 0건 | B |
| 4 | GradingConsumer.java:67 | `basicAck`가 커밋 전 finally에서 실행 → 커밋 실패 시 결과 롤백+메시지 소진 → GRADING 영구 고착(복구 불가) | C |
| 5 | SecurityConfig.java:66 | `/h2-console/**` 인증 없이 노출 + sa/빈 비번 + prod 프로파일 없음 → DB 탈취/RCE | 인프라 |
| 6 | PortOneClient.java:79 | 승인 판정이 `status=="PAID"`뿐, 금액/통화 미검증 + 웹훅 엔드포인트 없음 | B |
| 7 | application.yaml:60 | admin 기본 비번 `admin1234!`, 강제 변경 장치 없음 | 인프라 |

## 🟠 높음

| # | 파일:라인 | 요약 | 소유 |
|---|---|---|---|
| 8 | useAttempt.ts:203 | `confirmSubmit`이 모든 ApiError를 "자동제출됨"으로 간주 → 401/500 시 IN_PROGRESS인데 폴링 탈출 분기 없어 채점 모달 무한 | C |
| 9 | useAttempt.ts:82 + AttemptService.java:61 | 제출 후 새로고침 → `start()`가 IN_PROGRESS만 복원하므로 새 응시 생성(quota 소진) 또는 quota 초과 튕김. 복원 브랜치 unreachable | C |
| 10 | AttemptService.java:61 | 시작이 유니크제약/락 없는 check-then-act → 동시 시작 시 IN_PROGRESS 2개, quota 우회, 이후 getCurrent에서 IncorrectResultSize | C |

## 🟡 중간

| # | 파일:라인 | 요약 | 소유 |
|---|---|---|---|
| 11 | useAttempt.ts:124 | countdown 이펙트 deps에 매 렌더 새로 생기는 콜백 → 타이핑 중 남은시간 멈춤·서버 endsAt과 드리프트 | C |
| 12 | useAttempt.ts:45 | `beginResultPolling`이 기존 interval clear 안 함 → 이중호출 시 interval 누수·이중 navigate | C |
| 13 | useAttempt.ts:114 | setState updater 안에서 부작용(startAttempt/폴링) 실행 → StrictMode 이중 실행 | C |
| 14 | useAttempt.ts:112 | `failed` 페이즈에서 타이머 안 멈춤 → grading↔failed 진동 + 매 사이클 startAttempt | C |
| 15 | Attempt.java (@Version 없음) | 메시지 전송 레이스 → totalTokens last-writer-wins 언더카운트 → 효율 100점 치팅 가능 | C |
| 16 | RubricGrader.java:58 | 사용자 산출물의 `[/DATA]` 미이스케이프 → 인젝션 컨테인먼트 우회 가능 | C |
| 17 | subscription.ts:7 | 모든 에러를 null(FREE)로 정규화 → 결제 직후 새로고침 시 일시오류를 "구독 없음"으로 오인해 요금제로 추방 | C/B |
| 18 | GradingConsumer.java:31 / LeaderboardService.java:50 / AttemptRepository.java:37 | 종합점수 가중치 0.6/0.4 3곳 독립 정의 → 회의 변경 시 finalScore·리더보드·통계 desync | C/D |

## 🧹 정리 (동작 무해)

| # | 파일:라인 | 요약 |
|---|---|---|
| 19 | App.tsx:26,39 | `/problems/:id` 라우트 2번 등록(뒤엣것 dead) |
| 20 | GradingPage.tsx | 도달 불가 + 버튼이 없는 `/result`로 이동 → 삭제 대상 |
| 21 | dummyResult.ts / dummyUser 5곳 / formatDate 5곳 | API 연동 후 미사용 더미·중복 헬퍼 → 공용화/삭제 |

## 검증 노트
- #6: 파인더가 "PORTONE_WEBHOOK_SECRET 설정됨"이라 했으나 **실제 yaml엔 그 키 없음** — 사유 오류, 결론(웹훅 미구현·금액 미검증)은 유효.
- 하드 부정(REFUTED) 0건. 전 항목 CONFIRMED/PLAUSIBLE.
