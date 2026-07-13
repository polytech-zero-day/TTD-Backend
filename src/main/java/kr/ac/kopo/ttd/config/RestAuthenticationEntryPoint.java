package kr.ac.kopo.ttd.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.ac.kopo.ttd.common.ApiResponse;
import kr.ac.kopo.ttd.common.exception.ErrorCode;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 인증되지 않은 요청이 보호된 리소스에 접근하면 401 + ApiResponse 포맷으로 응답한다.
 * (엔트리포인트를 지정하지 않으면 Spring Security 기본값이 403을 반환해 GlobalExceptionHandler와
 * 응답 포맷/상태코드가 어긋나므로 명시적으로 등록)
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(errorCode));
    }
}
