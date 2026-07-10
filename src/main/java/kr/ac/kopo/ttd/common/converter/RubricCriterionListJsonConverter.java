package kr.ac.kopo.ttd.common.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import kr.ac.kopo.ttd.domain.RubricCriterion;

import java.util.List;

/**
 * {@code List<RubricCriterion>}을 JSON 문자열로 저장하는 JPA 컨버터.
 * ATTEMPTS.rubric_detail 컬럼에 적용한다 (StringListJsonConverter와 동일 패턴).
 */
@Converter
public class RubricCriterionListJsonConverter implements AttributeConverter<List<RubricCriterion>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<RubricCriterion>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<RubricCriterion> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("루브릭 항목 목록을 JSON으로 직렬화하는 데 실패했습니다.", e);
        }
    }

    @Override
    public List<RubricCriterion> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(dbData, LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON을 루브릭 항목 목록으로 역직렬화하는 데 실패했습니다.", e);
        }
    }
}
