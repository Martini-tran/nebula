package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 条件编译器
 * 把流程边的 SpEL 条件表达式编译为 {@link Predicate}，以编排上下文为根对象求值——表达式中可直接调用
 * 上下文方法（如 {@code getString('intent') == 'series'}），也可用 {@code #ctx} 变量显式引用。
 * 解析后的 {@link Expression} 按表达式文本缓存，避免重复解析。
 *
 * <p>空表达式编译为 null（由调用方视作无条件直达）；解析失败在编译期抛出，求值失败在运行期抛出，
 * 使配置错误尽早暴露。
 *
 * @author nebula
 */
public class ConditionCompiler {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    private final Map<String, Expression> cache = new ConcurrentHashMap<>();

    /**
     * 编译条件表达式为谓词
     *
     * @param expr SpEL 表达式，空表示无条件
     * @return 谓词；表达式为空时返回 null
     */
    public Predicate<OrchestrationContext> compile(String expr) {
        if (expr == null || expr.isBlank()) {
            return null;
        }
        Expression expression = cache.computeIfAbsent(expr, this::parse);
        return ctx -> evaluate(expression, expr, ctx);
    }

    private Expression parse(String expr) {
        try {
            return parser.parseExpression(expr);
        } catch (RuntimeException e) {
            throw new OrchestrationException("条件表达式解析失败: " + expr + " -> " + e.getMessage(), e);
        }
    }

    private boolean evaluate(Expression expression, String expr, OrchestrationContext ctx) {
        try {
            StandardEvaluationContext ec = new StandardEvaluationContext(ctx);
            ec.setVariable("ctx", ctx);
            Boolean result = expression.getValue(ec, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (RuntimeException e) {
            throw new OrchestrationException("条件表达式求值失败: " + expr + " -> " + e.getMessage(), e);
        }
    }
}
