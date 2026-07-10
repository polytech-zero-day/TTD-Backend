package kr.ac.kopo.ttd.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    PROBLEM_NOT_FOUND(HttpStatus.NOT_FOUND, "문제를 찾을 수 없습니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "허용되지 않은 상태 전환입니다."),
    INVALID_SKELETON_CODE(HttpStatus.BAD_REQUEST, "스켈레톤형 문제는 스켈레톤 코드가 필수이며, 그 외 문제는 스켈레톤 코드를 가질 수 없습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 refresh token입니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "응시 내역을 찾을 수 없습니다."),
    ATTEMPT_QUOTA_EXCEEDED(HttpStatus.CONFLICT, "이 문제의 응시 가능 횟수를 모두 사용했습니다."),
    ATTEMPT_MESSAGE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "AI 메시지 횟수를 모두 사용했습니다."),
    ATTEMPT_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 응시가 아닙니다. 이미 제출되었거나 시간이 만료되었습니다."),
    ATTEMPT_NOT_REGRADABLE(HttpStatus.CONFLICT, "채점 실패 상태의 응시만 재채점을 요청할 수 있습니다."),
    INVALID_AI_MODEL(HttpStatus.BAD_REQUEST, "허용되지 않은 AI 모델입니다."),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "구독 내역을 찾을 수 없습니다."),
    SUBSCRIPTION_ALREADY_ACTIVE(HttpStatus.CONFLICT, "이미 활성화된 구독이 있습니다."),
    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "결제에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
