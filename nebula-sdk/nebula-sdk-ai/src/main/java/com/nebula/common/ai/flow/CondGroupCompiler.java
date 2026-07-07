package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.OrchestrationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 可视化条件编译器
 * 把前端「条件构造器」落库的结构化条件（CondGroup）编译为 {@link Predicate}，以编排上下文求值。
 * 与手写 SpEL 的 {@link ConditionCompiler} 并列：IF 边用 SpEL 串，FOR 循环 break 用本结构化条件
 * （前端 {@code nodeConfig.loop.breakCondition} 存的是 CondGroup JSON，非 SpEL 串）。
 *
 * <p>结构（对齐前端 condition.ts）：
 * <pre>
 * { "logic": "AND|OR", "clauses": [ { "left": "变量键", "op": "eq|neq|...", "right": "字面量" } ] }
 * </pre>
 * left 作为上下文键 {@code ctx.get(left)} 取值；right 为字面量字符串。算子：
 * eq/neq/gt/gte/lt/lte（数值优先，退化字符串比较）、contains/notContains（子串/集合含元素）、
 * isEmpty/isNotEmpty（null 或空串/空集合）、isTrue/isFalse（布尔）。
 *
 * @author nebula
 */
public class CondGroupCompiler {

    /**
     * 编译结构化条件为谓词。null / 空 clauses → null（调用方视作「无 break 条件」）。
     *
     * @param condGroup 前端落库的 CondGroup Map（来自 nodeConfig.loop.breakCondition）
     * @return 谓词；无有效子句时返回 null
     */
    @SuppressWarnings("unchecked")
    public Predicate<OrchestrationContext> compile(Object condGroup) {
        if (!(condGroup instanceof Map<?, ?> map)) {
            return null;
        }
        boolean or = "OR".equalsIgnoreCase(String.valueOf(map.get("logic")));

        Object clausesRaw = map.get("clauses");
        if (!(clausesRaw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<Predicate<OrchestrationContext>> preds = new ArrayList<>();
        for (Object c : list) {
            if (c instanceof Map<?, ?> clause) {
                Predicate<OrchestrationContext> p = compileClause((Map<String, Object>) clause);
                if (p != null) {
                    preds.add(p);
                }
            }
        }
        if (preds.isEmpty()) {
            return null;
        }
        return ctx -> {
            for (Predicate<OrchestrationContext> p : preds) {
                boolean r = p.test(ctx);
                if (or && r) {
                    return true; // OR 短路
                }
                if (!or && !r) {
                    return false; // AND 短路
                }
            }
            return !or; // AND 全真 → true；OR 全假 → false
        };
    }

    /** 编译单个子句；left 为空或算子缺失返回 null（跳过该子句） */
    private Predicate<OrchestrationContext> compileClause(Map<String, Object> clause) {
        String left = str(clause.get("left"));
        String op = str(clause.get("op"));
        String right = str(clause.get("right"));
        if (left == null || left.isBlank() || op == null || op.isBlank()) {
            return null;
        }
        String key = left.trim();
        return ctx -> evalClause(ctx.get(key), op, right);
    }

    /** 对单个子句求值：actual 为 ctx 取到的左值，op 算子，right 字面量右值 */
    private boolean evalClause(Object actual, String op, String right) {
        return switch (op) {
            case "isEmpty" -> isEmpty(actual);
            case "isNotEmpty" -> !isEmpty(actual);
            case "isTrue" -> toBool(actual);
            case "isFalse" -> !toBool(actual);
            case "eq" -> eq(actual, right);
            case "neq" -> !eq(actual, right);
            case "contains" -> contains(actual, right);
            case "notContains" -> !contains(actual, right);
            case "gt", "gte", "lt", "lte" -> compareOp(actual, right, op);
            default -> false; // 未知算子，视为不命中
        };
    }

    private boolean eq(Object actual, String right) {
        if (actual == null) {
            return right == null || right.isEmpty();
        }
        return String.valueOf(actual).equals(right);
    }

    private boolean contains(Object actual, String right) {
        if (actual == null || right == null) {
            return false;
        }
        if (actual instanceof java.util.Collection<?> coll) {
            return coll.stream().anyMatch(e -> String.valueOf(e).equals(right));
        }
        return String.valueOf(actual).contains(right);
    }

    /** 数值比较优先；不可解析为数值则退化字符串字典序比较 */
    private boolean compareOp(Object actual, String right, String op) {
        if (actual == null || right == null) {
            return false;
        }
        Double a = toDouble(actual);
        Double b = toDouble(right);
        int cmp;
        if (a != null && b != null) {
            cmp = Double.compare(a, b);
        } else {
            cmp = String.valueOf(actual).compareTo(right);
        }
        return switch (op) {
            case "gt" -> cmp > 0;
            case "gte" -> cmp >= 0;
            case "lt" -> cmp < 0;
            case "lte" -> cmp <= 0;
            default -> false;
        };
    }

    private boolean isEmpty(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof CharSequence cs) {
            return cs.toString().isBlank();
        }
        if (v instanceof java.util.Collection<?> coll) {
            return coll.isEmpty();
        }
        if (v instanceof Map<?, ?> m) {
            return m.isEmpty();
        }
        return false;
    }

    private boolean toBool(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v == null) {
            return false;
        }
        String s = String.valueOf(v).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    private Double toDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return v == null ? null : Double.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
