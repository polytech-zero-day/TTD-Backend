/**
 * PortOne V2 결제 연동을 전담한다. Controller/Service는 PG사 REST API를 직접 호출하지 않고
 * 이 패키지의 PortOneClient를 경유해야 한다 (ai 패키지의 AiClient와 동일한 "외부 연동 전담
 * 창구" 컨벤션 — 프로바이더 교체, 인증 헤더 조립, 응답 필드 매핑을 한 곳에서 통제하기 위함).
 */
package kr.ac.kopo.ttd.payment;
