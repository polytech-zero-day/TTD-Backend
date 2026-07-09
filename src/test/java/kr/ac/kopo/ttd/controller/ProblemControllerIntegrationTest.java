package kr.ac.kopo.ttd.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProblemSeedInitializer가 test 프로파일에서 문제 10개를 active로 시딩한 상태를 전제로,
 * 응시자용 목록 엔드포인트가 그 10개를 반환하는지 전체 컨텍스트로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProblemControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void 응시자_목록은_시딩된_문제_10개를_200으로_반환한다() throws Exception {
        mockMvc.perform(get("/api/problems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(10));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 시딩된_문제_10개는_모두_active_상태다() throws Exception {
        // ProblemSummaryResponse에는 status 필드가 없으므로, status가 노출되는
        // 관리자 목록(AdminProblemResponse.status)으로 전체가 active인지 단언한다.
        mockMvc.perform(get("/api/admin/problems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(10))
                .andExpect(jsonPath("$.data[?(@.status != 'active')]").isEmpty());
    }

    @Test
    void 미인증_사용자는_응시자용_목록_조회가_401이다() throws Exception {
        mockMvc.perform(get("/api/problems"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void USER_권한은_관리자용_목록_조회가_403이다() throws Exception {
        mockMvc.perform(get("/api/admin/problems"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 미인증_사용자는_관리자용_목록_조회가_401이다() throws Exception {
        mockMvc.perform(get("/api/admin/problems"))
                .andExpect(status().isUnauthorized());
    }
}
