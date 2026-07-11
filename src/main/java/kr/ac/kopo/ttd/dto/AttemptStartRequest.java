package kr.ac.kopo.ttd.dto;

import jakarta.validation.constraints.NotNull;

public record AttemptStartRequest(@NotNull Long problemId, String chatModel) {
    /** 기존 클라이언트와 테스트는 모델 미선택 시 서버 기본값을 사용한다. */
    public AttemptStartRequest(Long problemId) {
        this(problemId, null);
    }
}
