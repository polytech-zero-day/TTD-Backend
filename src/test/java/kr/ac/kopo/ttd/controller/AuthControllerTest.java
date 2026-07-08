package kr.ac.kopo.ttd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.ac.kopo.ttd.common.jwt.JwtProvider;
import kr.ac.kopo.ttd.config.RestAccessDeniedHandler;
import kr.ac.kopo.ttd.config.RestAuthenticationEntryPoint;
import kr.ac.kopo.ttd.config.SecurityConfig;
import kr.ac.kopo.ttd.domain.UserRole;
import kr.ac.kopo.ttd.dto.LoginRequest;
import kr.ac.kopo.ttd.dto.RefreshRequest;
import kr.ac.kopo.ttd.dto.SignupRequest;
import kr.ac.kopo.ttd.dto.TokenResponse;
import kr.ac.kopo.ttd.dto.UserResponse;
import kr.ac.kopo.ttd.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtProvider.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private AuthService authService;

    @Test
    void 회원가입은_인증_없이_호출할_수_있다() throws Exception {
        SignupRequest request = new SignupRequest("dummy@example.com", "password123", "nick");
        given(authService.signup(any())).willReturn(
                new UserResponse(1L, request.email(), request.nickname(), UserRole.USER, LocalDateTime.now())
        );

        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void 로그인은_인증_없이_호출할_수_있다() throws Exception {
        LoginRequest request = new LoginRequest("dummy@example.com", "password123");
        given(authService.login(any())).willReturn(
                new TokenResponse("access-token", "refresh-token", "Bearer", 1800L)
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    void 토큰_재발급은_인증_없이_호출할_수_있다() throws Exception {
        RefreshRequest request = new RefreshRequest("refresh-token");
        given(authService.refresh(any())).willReturn(
                new TokenResponse("new-access-token", "new-refresh-token", "Bearer", 1800L)
        );

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
    }

    @Test
    void 로그아웃은_토큰이_없으면_거부된다() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그아웃은_유효한_토큰이_있으면_성공한다() throws Exception {
        String accessToken = jwtProvider.generateAccessToken(1L, UserRole.USER);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }
}
