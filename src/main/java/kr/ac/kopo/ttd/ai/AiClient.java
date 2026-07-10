package kr.ac.kopo.ttd.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import kr.ac.kopo.ttd.domain.AiPurpose;
import kr.ac.kopo.ttd.service.AiModelSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * Spring AI 호출 단일 창구 (ai/package-info 참고). 프롬프트 로깅·토큰 사용량 추출·
 * Redis 응답 캐싱(동일 대화 재요청 시 비용 절감)을 이 클래스에서만 처리한다.
 * 모델은 용도별로 AiModelSettingService에서 조회해 호출 시점에 주입한다(재시작 없이 교체).
 */
@Slf4j
@Component
public class AiClient {

    private static final String CACHE_PREFIX = "ai-cache:";

    private final ChatClient chatClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiModelSettingService modelSettingService;
    private final Duration cacheTtl;

    public AiClient(
            ChatClient.Builder chatClientBuilder,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            AiModelSettingService modelSettingService,
            @Value("${app.ai.cache-ttl-hours}") long cacheTtlHours) {
        this.chatClient = chatClientBuilder.build();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.modelSettingService = modelSettingService;
        this.cacheTtl = Duration.ofHours(cacheTtlHours);
    }

    /** 대화 이력 전체를 넘겨 다음 응답을 받는다. history 는 오래된 순. */
    public AiChatResult chat(String systemPrompt, List<Message> history, AiPurpose purpose) {
        return call(systemPrompt, history, false, purpose);
    }

    /**
     * OpenAI JSON 모드(response_format=json_object)로 호출해 문법적으로 유효한 JSON만 받는다.
     * 채점 등 구조화 응답을 파싱하는 곳 전용 — 피드백 문자열 안의 따옴표가 JSON을 깨는
     * 문제를 모델 강제 레벨에서 차단한다. (프롬프트에 "JSON" 언급이 있어야 동작)
     */
    public AiChatResult chatJson(String systemPrompt, List<Message> history, AiPurpose purpose) {
        return call(systemPrompt, history, true, purpose);
    }

    private AiChatResult call(String systemPrompt, List<Message> history, boolean jsonMode, AiPurpose purpose) {
        String model = modelSettingService.modelFor(purpose);
        // 모델이 캐시 키에 포함되어야 함 — 미포함 시 모델을 바꿔도 이전 모델의 캐시 응답이 반환된다
        String cacheKey = cacheKey(systemPrompt, history, jsonMode, model);
        AiChatResult cached = readCache(cacheKey);
        if (cached != null) {
            return cached; // 캐시 히트여도 토큰은 원 호출 값 그대로 과금(효율 점수 공정성)
        }

        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(model);
        if (jsonMode) {
            options.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build());
        }
        ChatResponse response = chatClient.prompt()
                .system(systemPrompt)
                .messages(history)
                .options(options)
                .call().chatResponse();

        String content = response.getResult().getOutput().getText();
        long tokens = response.getMetadata().getUsage().getTotalTokens();
        log.info("AI 호출 완료: purpose={}, model={}, historySize={}, jsonMode={}, totalTokens={}",
                purpose, model, history.size(), jsonMode, tokens);

        AiChatResult result = new AiChatResult(content, tokens);
        writeCache(cacheKey, result);
        return result;
    }

    public static Message user(String content) {
        return new UserMessage(content);
    }

    public static Message assistant(String content) {
        return new AssistantMessage(content);
    }

    private String cacheKey(String systemPrompt, List<Message> history, boolean jsonMode, String model) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(jsonMode ? (byte) 1 : (byte) 0);
            digest.update(model.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0); // 구분자 — 모델명과 프롬프트 경계 고정
            digest.update(systemPrompt.getBytes(StandardCharsets.UTF_8));
            for (Message m : history) {
                digest.update(m.getMessageType().getValue().getBytes(StandardCharsets.UTF_8));
                digest.update(m.getText().getBytes(StandardCharsets.UTF_8));
            }
            return CACHE_PREFIX + HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("캐시 키 생성에 실패했습니다.", e);
        }
    }

    private AiChatResult readCache(String key) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, AiChatResult.class);
        } catch (JacksonException e) {
            return null; // 캐시 파손은 무시하고 실호출
        }
    }

    private void writeCache(String key, AiChatResult result) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), cacheTtl);
        } catch (JacksonException e) {
            log.warn("AI 응답 캐시 저장 실패", e);
        }
    }
}