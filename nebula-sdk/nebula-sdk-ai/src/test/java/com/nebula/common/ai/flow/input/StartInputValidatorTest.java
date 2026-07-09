package com.nebula.common.ai.flow.input;

import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * START 入参校验单测：required/类型/越界/长度/正则/枚举/File 元数据 + 聚合不短路 + Parser 两形态。
 *
 * @author nebula
 */
class StartInputValidatorTest {

    private final StartInputValidator validator = new StartInputValidator();

    private final StartInputSpecParser parser = new StartInputSpecParser();

    private boolean hasError(List<InputValidationError> errs, String key, InputValidationError.Type type) {
        return errs.stream().anyMatch(e -> key.equals(e.getKey()) && e.getType() == type);
    }

    @Test
    void required缺失() {
        var spec = new StartInputSpec().setKey("city").setRequired(true).setType("String");
        var errs = validator.validate(List.of(spec), Map.of());
        assertTrue(hasError(errs, "city", InputValidationError.Type.REQUIRED_MISSING));
    }

    @Test
    void 非必填缺省不报错() {
        var spec = new StartInputSpec().setKey("opt").setRequired(false).setType("String");
        assertTrue(validator.validate(List.of(spec), Map.of()).isEmpty());
    }

    @Test
    void 类型不符Number() {
        var spec = new StartInputSpec().setKey("age").setType("Number");
        var errs = validator.validate(List.of(spec), Map.of("age", "abc"));
        assertTrue(hasError(errs, "age", InputValidationError.Type.TYPE_MISMATCH));
        // 字符串化数字应通过
        assertTrue(validator.validate(List.of(spec), Map.of("age", "18")).isEmpty());
    }

    @Test
    void 数值越界() {
        var spec = new StartInputSpec().setKey("n").setType("Number").setMin(1.0).setMax(10.0);
        assertTrue(hasError(validator.validate(List.of(spec), Map.of("n", 0)),
                "n", InputValidationError.Type.OUT_OF_RANGE));
        assertTrue(hasError(validator.validate(List.of(spec), Map.of("n", 99)),
                "n", InputValidationError.Type.OUT_OF_RANGE));
        assertTrue(validator.validate(List.of(spec), Map.of("n", 5)).isEmpty());
    }

    @Test
    void 长度与正则与枚举() {
        var len = new StartInputSpec().setKey("s").setType("String").setMinLength(2).setMaxLength(4);
        assertTrue(hasError(validator.validate(List.of(len), Map.of("s", "x")),
                "s", InputValidationError.Type.LENGTH_OUT_OF_RANGE));

        var pat = new StartInputSpec().setKey("p").setType("String").setPattern("^\\d+$");
        assertTrue(hasError(validator.validate(List.of(pat), Map.of("p", "ab")),
                "p", InputValidationError.Type.PATTERN_MISMATCH));
        assertTrue(validator.validate(List.of(pat), Map.of("p", "123")).isEmpty());

        var en = new StartInputSpec().setKey("e").setType("String").setEnumValues("a,b,c");
        assertTrue(hasError(validator.validate(List.of(en), Map.of("e", "z")),
                "e", InputValidationError.Type.NOT_IN_ENUM));
        assertTrue(validator.validate(List.of(en), Map.of("e", "b")).isEmpty());
    }

    @Test
    void File元数据大小与格式() {
        var spec = new StartInputSpec().setKey("f").setType("File").setMaxSizeMb(5.0).setAccept("png,jpg");
        var big = validator.validate(List.of(spec), Map.of("f", Map.of("name", "a.png", "sizeMb", 10)));
        assertTrue(hasError(big, "f", InputValidationError.Type.FILE_META_INVALID));
        var badExt = validator.validate(List.of(spec), Map.of("f", Map.of("name", "a.gif", "sizeMb", 1)));
        assertTrue(hasError(badExt, "f", InputValidationError.Type.FILE_META_INVALID));
        var ok = validator.validate(List.of(spec), Map.of("f", Map.of("name", "a.png", "sizeMb", 1)));
        assertTrue(ok.isEmpty());
    }

    @Test
    void 聚合多个错误不短路() {
        var s1 = new StartInputSpec().setKey("a").setRequired(true).setType("String");
        var s2 = new StartInputSpec().setKey("b").setType("Number").setMin(0.0);
        var errs = validator.validate(List.of(s1, s2), Map.of("b", -5));
        assertEquals(2, errs.size());
    }

    @Test
    void Parser数组形态摊平validation() {
        FlowDefinition def = new FlowDefinition().setFlowCode("f");
        FlowNodeDefinition start = new FlowNodeDefinition().setNodeCode("s").setNodeType("START");
        start.getNodeConfig().put("inputs", List.of(
                Map.of("key", "city", "type", "String", "required", true,
                        "validation", Map.of("minLength", 2, "enumValues", "北京,上海"))));
        def.getNodes().add(start);

        List<StartInputSpec> specs = parser.parse(def);
        assertEquals(1, specs.size());
        assertEquals("city", specs.get(0).getKey());
        assertTrue(specs.get(0).isRequired());
        assertEquals(2, specs.get(0).getMinLength());
        assertEquals("北京,上海", specs.get(0).getEnumValues());
    }

    @Test
    void Parser对象形态推断类型() {
        FlowDefinition def = new FlowDefinition().setFlowCode("f");
        FlowNodeDefinition start = new FlowNodeDefinition().setNodeCode("s").setNodeType("START");
        start.getNodeConfig().put("inputs", Map.of("city", "北京", "count", 3));
        def.getNodes().add(start);

        List<StartInputSpec> specs = parser.parse(def);
        assertEquals(2, specs.size());
        assertTrue(specs.stream().anyMatch(s -> "count".equals(s.getKey()) && "Number".equals(s.getType())));
        assertTrue(specs.stream().anyMatch(s -> "city".equals(s.getKey()) && "String".equals(s.getType())));
    }

    @Test
    void 无START节点返回空规格() {
        FlowDefinition def = new FlowDefinition().setFlowCode("f");
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("n").setNodeType("PROMPT"));
        assertTrue(parser.parse(def).isEmpty());
    }
}
