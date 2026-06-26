package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.OrchestrationContext;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConditionCompiler} 单测
 *
 * @author nebula
 */
class ConditionCompilerTest {

    private final ConditionCompiler compiler = new ConditionCompiler();

    @Test
    void 空表达式编译为null() {
        assertNull(compiler.compile(null));
        assertNull(compiler.compile("  "));
    }

    @Test
    void 以上下文为根求值字符串相等() {
        Predicate<OrchestrationContext> predicate = compiler.compile("getString('intent') == 'series'");
        OrchestrationContext hit = new OrchestrationContext().put("intent", "series");
        OrchestrationContext miss = new OrchestrationContext().put("intent", "single");
        assertTrue(predicate.test(hit));
        assertFalse(predicate.test(miss));
    }

    @Test
    void 支持ctx变量显式引用() {
        Predicate<OrchestrationContext> predicate = compiler.compile("#ctx.contains('topics')");
        assertTrue(predicate.test(new OrchestrationContext().put("topics", "a,b")));
        assertFalse(predicate.test(new OrchestrationContext()));
    }
}
