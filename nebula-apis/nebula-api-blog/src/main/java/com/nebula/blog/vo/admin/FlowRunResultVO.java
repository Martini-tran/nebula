package com.nebula.blog.vo.admin;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI流程运行结果（管理员端）
 * 承载一次流程执行后编排上下文的产物与节点轨迹。
 *
 * @author nebula
 */
@Data
public class FlowRunResultVO {

    /**
     * 流程编码
     */
    private String flowCode;

    /**
     * 编排上下文全部产物（各节点写回的键值）
     */
    private Map<String, Object> attributes = new LinkedHashMap<>();

    /**
     * 节点执行轨迹（按执行顺序：节点编码 -> 产物）
     */
    private Map<String, Object> nodeResults = new LinkedHashMap<>();
}
