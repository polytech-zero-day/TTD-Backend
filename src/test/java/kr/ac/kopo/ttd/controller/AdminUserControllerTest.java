package kr.ac.kopo.ttd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.ac.kopo.ttd.common.jwt.JwtProvider;
import kr.ac.kopo.ttd.config.RestAccessDeniedHandler;
import kr.ac.kopo.ttd.config.RestAuthenticationEntryPoint;
import kr.ac.kopo.ttd.config.SecurityConfig;
import kr.ac.kopo.ttd.domain.UserRole;
import kr.ac.kopo.ttd.dto.UserCreateRequest;
import kr.ac.kopo.ttd.dto.UserResponse;
import kr.ac.kopo.ttd.service.UserAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, JwtProvider.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private UserAdminService userAdminService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void ADMIN은_유저_목록을_조회할_수_있다() throws Exception {
        given(userAdminService.getUsers()).willReturn(List.of(
                new UserResponse(1L, "dummy@example.com", "nick", UserRole.USER, LocalDateTime.now())
        ));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].email").value("dummy@example.com"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void USER는_접근이_거부된다() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 인증되지_않으면_접근이_거부된다() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ADMIN은_유저를_생성할_수_있다() throws Exception {
        UserCreateRequest request = new UserCreateRequest("dummy@example.com", "password123", "nick", UserRole.USER);
        given(userAdminService.createUser(any())).willReturn(
                new UserResponse(1L, request.email(), request.nickname(), request.role(), LocalDateTime.now())
        );

        mockMvc.perform(post("/api/admin/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("dummy@example.com"));
    }
}
