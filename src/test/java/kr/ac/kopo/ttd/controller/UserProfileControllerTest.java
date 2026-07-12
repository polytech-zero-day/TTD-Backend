package kr.ac.kopo.ttd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.ac.kopo.ttd.common.jwt.JwtProvider;
import kr.ac.kopo.ttd.config.RestAccessDeniedHandler;
import kr.ac.kopo.ttd.config.RestAuthenticationEntryPoint;
import kr.ac.kopo.ttd.config.SecurityConfig;
import kr.ac.kopo.ttd.domain.UserRole;
import kr.ac.kopo.ttd.dto.MyProfileResponse;
import kr.ac.kopo.ttd.dto.NicknameUpdateRequest;
import kr.ac.kopo.ttd.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
@Import({SecurityConfig.class, JwtProvider.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class UserProfileControllerTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private UserProfileService userProfileService;

    private String bearerToken() {
        return "Bearer " + jwtProvider.generateAccessToken(USER_ID, UserRole.USER);
    }

    @Test
    void 인증_없이_닉네임을_변경할_수_없다() throws Exception {
        mockMvc.perform(patch("/api/users/me/nickname")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new NicknameUpdateRequest("새닉네임"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 닉네임을_변경하면_갱신된_프로필을_반환한다() throws Exception {
        given(userProfileService.changeNickname(eq(USER_ID), any()))
                .willReturn(new MyProfileResponse(USER_ID, "user@example.com", "새닉네임", "USER", LocalDateTime.now()));

        mockMvc.perform(patch("/api/users/me/nickname")
                        .header("Authorization", bearerToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new NicknameUpdateRequest("새닉네임"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"));
    }

    @Test
    void 빈_닉네임은_거부한다() throws Exception {
        mockMvc.perform(patch("/api/users/me/nickname")
                        .header("Authorization", bearerToken())
                        .contentType("application/json")
                        .content("{\"nickname\":\" \"}"))
                .andExpect(status().isBadRequest());
    }
}
