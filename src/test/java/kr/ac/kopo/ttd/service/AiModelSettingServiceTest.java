package kr.ac.kopo.ttd.service;

import kr.ac.kopo.ttd.common.exception.BusinessException;
import kr.ac.kopo.ttd.common.exception.ErrorCode;
import kr.ac.kopo.ttd.domain.AiModelSetting;
import kr.ac.kopo.ttd.domain.AiPurpose;
import kr.ac.kopo.ttd.dto.AiModelSettingResponse;
import kr.ac.kopo.ttd.dto.AiModelSettingsResponse;
import kr.ac.kopo.ttd.repository.AiModelSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiModelSettingServiceTest {

    private static final String DEFAULT = "gpt-5.4-mini";
    private static final String PREMIUM = "gpt-5.4";
    private static final String AVAILABLE = "gpt-5.4-mini, gpt-5.4, gpt-5.4-nano";

    @Mock
    private AiModelSettingRepository repository;

    private AiModelSettingService service() {
        return new AiModelSettingService(repository, DEFAULT, PREMIUM, AVAILABLE);
    }

    @Test
    void 설정이_없으면_기본_모델로_폴백한다() {
        given(repository.findByPurpose(AiPurpose.GRADING)).willReturn(Optional.empty());

        assertThat(service().modelFor(AiPurpose.GRADING)).isEqualTo(DEFAULT);
    }

    @Test
    void 설정이_있으면_지정_모델을_반환한다() {
        AiModelSetting setting = AiModelSetting.builder()
                .purpose(AiPurpose.GRADING).model("gpt-5.4").updatedBy(1L).build();
        given(repository.findByPurpose(AiPurpose.GRADING)).willReturn(Optional.of(setting));

        assertThat(service().modelFor(AiPurpose.GRADING)).isEqualTo("gpt-5.4");
    }

    @Test
    void 설정_목록은_미설정_용도를_기본값_fromDefault로_표시한다() {
        given(repository.findByPurpose(any())).willReturn(Optional.empty());

        AiModelSettingsResponse res = service().getSettings();

        assertThat(res.settings()).hasSize(AiPurpose.values().length);
        assertThat(res.settings()).allMatch(s -> s.fromDefault() && s.model().equals(DEFAULT));
        assertThat(res.availableModels()).containsExactly("gpt-5.4-mini", "gpt-5.4", "gpt-5.4-nano");
    }

    @Test
    void 신규_용도_변경은_행을_생성하고_기록자를_남긴다() {
        given(repository.findByPurpose(AiPurpose.CHAT)).willReturn(Optional.empty());
        given(repository.save(any(AiModelSetting.class))).willAnswer(inv -> inv.getArgument(0));

        AiModelSettingResponse res = service().updateModel(AiPurpose.CHAT, "gpt-5.4", 7L);

        ArgumentCaptor<AiModelSetting> captor = ArgumentCaptor.forClass(AiModelSetting.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getModel()).isEqualTo("gpt-5.4");
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(7L);
        assertThat(res.fromDefault()).isFalse();
    }

    @Test
    void 기존_용도_변경은_모델과_기록자를_갱신한다() {
        AiModelSetting existing = AiModelSetting.builder()
                .purpose(AiPurpose.GRADING).model("gpt-5.4-mini").updatedBy(1L).build();
        given(repository.findByPurpose(AiPurpose.GRADING)).willReturn(Optional.of(existing));

        service().updateModel(AiPurpose.GRADING, "gpt-5.4", 9L);

        assertThat(existing.getModel()).isEqualTo("gpt-5.4");
        assertThat(existing.getUpdatedBy()).isEqualTo(9L);
    }

    @Test
    void 허용_목록에_없는_모델은_거부한다() {
        assertThatThrownBy(() -> service().updateModel(AiPurpose.CHAT, "gpt-6-ultra", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_AI_MODEL);
    }

    @Test
    void 유료_응시는_프리미엄_모델을_쓴다() {
        assertThat(service().chatModelFor(true)).isEqualTo(PREMIUM);
    }

    @Test
    void 무료_응시는_CHAT_용도_설정_모델을_쓴다() {
        given(repository.findByPurpose(AiPurpose.CHAT)).willReturn(Optional.empty());

        assertThat(service().chatModelFor(false)).isEqualTo(DEFAULT);
    }
}
