package kr.ac.kopo.ttd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.ac.kopo.ttd.common.jwt.JwtProvider;
import kr.ac.kopo.ttd.config.RestAccessDeniedHandler;
import kr.ac.kopo.ttd.config.RestAuthenticationEntryPoint;
import kr.ac.kopo.ttd.config.SecurityConfig;
import kr.ac.kopo.ttd.domain.SubscriptionStatus;
import kr.ac.kopo.ttd.domain.UserRole;
import kr.ac.kopo.ttd.dto.SubscriptionResponse;
import kr.ac.kopo.ttd.dto.SubscriptionSubscribeRequest;
import kr.ac.kopo.ttd.service.SubscriptionService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
@Import({SecurityConfig.class, JwtProvider.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class SubscriptionControllerTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private SubscriptionService subscriptionService;

    private String bearerToken() {
        return "Bearer " + jwtProvider.generateAccessToken(USER_ID, UserRole.USER);
    }

    @Test
    void 인증_없이_구독을_등록할_수_없다() throws Exception {
        mockMvc.perform(post("/api/subscriptions")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SubscriptionSubscribeRequest("billing-key-1"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 구독을_등록하면_201과_상태를_반환한다() throws Exception {
        SubscriptionResponse response = new SubscriptionResponse(
                SubscriptionStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now().plusMonths(1), null, false, null);
        given(subscriptionService.subscribe(eq(USER_ID), any())).willReturn(response);

        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", bearerToken())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SubscriptionSubscribeRequest("billing-key-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void 빌링키가_없으면_400() throws Exception {
        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", bearerToken())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 구독을_취소하면_기간_종료_예약_상태를_반환한다() throws Exception {
        SubscriptionResponse response = new SubscriptionResponse(
                SubscriptionStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now().plusMonths(1),
                null, true, LocalDateTime.now());
        given(subscriptionService.cancel(USER_ID)).willReturn(response);

        mockMvc.perform(delete("/api/subscriptions").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelAtPeriodEnd").value(true));
    }

    @Test
    void 내_구독_상태를_조회한다() throws Exception {
        SubscriptionResponse response = new SubscriptionResponse(
                SubscriptionStatus.PAST_DUE, LocalDateTime.now(), LocalDateTime.now(), null, false, null);
        given(subscriptionService.getMySubscription(USER_ID)).willReturn(response);

        mockMvc.perform(get("/api/subscriptions/me").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAST_DUE"));
    }
}
