package kr.ac.kopo.ttd.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import kr.ac.kopo.ttd.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final String errorCode;
    private final String message;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(true, null, null, null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, errorCode.name(), errorCode.getMessage());
    }

    public static ApiResponse<Void> error(String errorCode, String message) {
        return new ApiResponse<>(false, null, errorCode, message);
    }
}
