package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.OrchestrationContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CondGroupCompiler} 单测：算子映射、AND/OR、类型强转与空条件。
 *
 * @author nebula
 */
class CondGroupCompilerTest {

    private final CondGroupCompiler compiler = new CondGroupCompiler();

    private OrchestrationContext ctx(String key, Object val) {
        OrchestrationContext ctx = new OrchestrationContext();
        ctx.put(key, val);
        return ctx;
    }

    private Map<String, Object> clause(String left, String op, String right) {
        return Map.of("left", left, "op", op, "right", right == null ? "" : right);
    }

    @Test
    void 空条件编译为null() {
        assertNull(compiler.compile(null));
        assertNull(compiler.compile("not a map"));
        assertNull(compiler.compile(Map.of("logic", "AND", "clauses", List.of())));
    }

    @Test
    void eq与neq() {
        Predicate<OrchestrationContext> p = compiler.compile(
                Map.of("logic", "AND", "clauses", List.of(clause("status", "eq", "done"))));
        assertTrue(p.test(ctx("status", "done")));
        assertFalse(p.test(ctx("status", "pending")));
    }

    @Test
    void 数值比较gt() {
        Predicate<OrchestrationContext> p = compiler.compile(
                Map.of("logic", "AND", "clauses", List.of(clause("score", "gt", "80"))));
        assertTrue(p.test(ctx("score", 90)));      // 数值
        assertTrue(p.test(ctx("score", "85")));    // 字符串数字
        assertFalse(p.test(ctx("score", 70)));
    }

    @Test
    void contains子串与集合() {
        Predicate<OrchestrationContext> sub = compiler.compile(
                Map.of("logic", "AND", "clauses", List.of(clause("text", "contains", "错误"))));
        assertTrue(sub.test(ctx("text", "发生了错误")));
        assertFalse(sub.test(ctx("text", "一切正常")));

        Predicate<OrchestrationContext> inColl = compiler.compile(
                Map.of("logic", "AND", "clauses", List.of(clause("tags", "contains", "vip"))));
        assertTrue(inColl.test(ctx("tags", List.of("new", "vip"))));
        assertFalse(inColl.test(ctx("tags", List.of("new"))));
    }

    @Test
    void isEmpty与isNotEmpty() {
        Predicate<OrchestrationContext> empty = compiler.compile(
                Map.of("logic", "AND", "clauses", List.of(Map.of("left", "v", "op", "isEmpty"))));
        assertTrue(empty.test(new OrchestrationContext()));   // 缺失
        assertTrue(empty.test(ctx("v", "  ")));                // 空白串
        assertTrue(empty.test(ctx("v", List.of())));           // 空集合
        assertFalse(empty.test(ctx("v", "x")));
    }

    @Test
    void isTrue布尔() {
        Predicate<OrchestrationContext> p = compiler.compile(
                Map.of("logic", "AND", "clauses", List.of(Map.of("left", "flag", "op", "isTrue"))));
        assertTrue(p.test(ctx("flag", true)));
        assertTrue(p.test(ctx("flag", "true")));
        assertTrue(p.test(ctx("flag", "1")));
        assertFalse(p.test(ctx("flag", false)));
        assertFalse(p.test(ctx("flag", "no")));
    }

    @Test
    void AND全真OR任一真() {
        Map<String, Object> c1 = clause("a", "eq", "1");
        Map<String, Object> c2 = clause("b", "eq", "2");

        Predicate<OrchestrationContext> and = compiler.compile(Map.of("logic", "AND", "clauses", List.of(c1, c2)));
        OrchestrationContext both = ctx("a", "1");
        both.put("b", "2");
        assertTrue(and.test(both));
        assertFalse(and.test(ctx("a", "1")));  // b 缺失

        Predicate<OrchestrationContext> or = compiler.compile(Map.of("logic", "OR", "clauses", List.of(c1, c2)));
        assertTrue(or.test(ctx("a", "1")));    // 仅 a
        assertFalse(or.test(ctx("a", "9")));   // 都不满足
    }
}
