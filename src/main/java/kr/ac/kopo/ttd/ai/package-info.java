/**
 * Spring AI 연동을 전담한다. Controller/Service는 ChatClient를 직접 주입하지 않고
 * 이 패키지의 AiClient 래퍼를 경유해야 한다 (프로바이더 교체, 프롬프트 로깅, 토큰 사용량
 * 추적을 한 곳에서 통제하기 위함).
 */
package kr.ac.kopo.ttd.ai;
