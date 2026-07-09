package com.nebula.common.ai.flow.input;

import com.nebula.common.ai.orchestration.OrchestrationException;

import java.util.Collections;
import java.util.List;

/**
 * 入参校验异常
 * START 入参不满足声明的 schema 时抛出，聚合<b>全部</b>违规项（不短路），供上层（manager REST）
 * 转成 400 + 结构化 errors 列表一次返回给前端。继承 {@link OrchestrationException} 以纳入编排异常体系。
 *
 * @author nebula
 */
public class InputValidationException extends OrchestrationException {

    private final transient List<InputValidationError> errors;

    public InputValidationException(List<InputValidationError> errors) {
        super(buildMessage(errors));
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * 全部违规项（只读）
     *
     * @return 违规列表
     */
    public List<InputValidationError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    private static String buildMessage(List<InputValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "入参校验失败";
        }
        StringBuilder sb = new StringBuilder("入参校验失败：");
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) {
                sb.append("；");
            }
            sb.append(errors.get(i).getMessage());
        }
        return sb.toString();
    }
}
