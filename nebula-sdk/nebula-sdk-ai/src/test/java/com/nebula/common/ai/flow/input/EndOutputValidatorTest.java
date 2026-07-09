package com.nebula.common.ai.flow.input;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * END 出参 schema 校验单测：parse 解析 + 缺必出字段/类型不符校验 + 未声明不校验。
 *
 * @author nebula
 */
class EndOutputValidatorTest {

    private final EndOutputValidator validator = new EndOutputValidator();

    @Test
    void 未声明schema不校验() {
        assertTrue(validator.validate(List.of(), Map.of()).isEmpty());
        assertTrue(validator.parse(null).isEmpty());
        assertTrue(validator.parse(List.of()).isEmpty());
    }

    @Test
    void parse解析schema() {
        var schema = validator.parse(List.of(
                Map.of("name", "answer", "type", "String", "required", true),
                Map.of("name", "score", "type", "Number", "required", false)));
        assertEquals(2, schema.size());
        assertEquals("answer", schema.get(0).getName());
        assertTrue(schema.get(0).isRequired());
    }

    @Test
    void 缺必出字段报错() {
        var schema = validator.parse(List.of(
                Map.of("name", "answer", "type", "String", "required", true)));
        var errs = validator.validate(schema, Map.of("other", "x"));
        assertEquals(1, errs.size());
        assertEquals(InputValidationError.Type.REQUIRED_MISSING, errs.get(0).getType());
    }

    @Test
    void 类型不符报错() {
        var schema = validator.parse(List.of(
                Map.of("name", "score", "type", "Number", "required", true)));
        var errs = validator.validate(schema, Map.of("score", "notNumber"));
        assertTrue(errs.stream().anyMatch(e -> e.getType() == InputValidationError.Type.TYPE_MISMATCH));
        // 数字字符串通过
        assertTrue(validator.validate(schema, Map.of("score", "88")).isEmpty());
    }

    @Test
    void 非必出字段缺省不报错() {
        var schema = validator.parse(List.of(
                Map.of("name", "extra", "type", "String", "required", false)));
        assertTrue(validator.validate(schema, Map.of("answer", "hi")).isEmpty());
    }
}
