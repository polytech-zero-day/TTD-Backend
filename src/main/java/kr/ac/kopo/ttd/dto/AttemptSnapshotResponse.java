// dto/AttemptSnapshotResponse.java — 시작·새로고침 복원 공용 (프론트 AttemptStartResponse 대응)
package kr.ac.kopo.ttd.dto;

import java.util.List;

public record AttemptSnapshotResponse(
        Long attemptId,
        String status,
        long remainingSeconds,
        AttemptUsageResponse usage,
        List<ChatMessageResponse> messages,
        String draft,
        String chatModel) {}