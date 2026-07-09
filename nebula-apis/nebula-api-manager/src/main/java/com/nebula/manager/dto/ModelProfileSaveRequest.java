package com.nebula.manager.dto;

import lombok.Data;

import java.util.Map;

/**
 * AI模型档案保存请求（创建/更新共用）
 * apiKey 以明文传入，由服务端加密落库；更新时该字段留空表示「不修改原密钥」。
 *
 * @author nebula
 */
@Data
public class ModelProfileSaveRequest {

    /**
     * 档案编码，全局唯一（创建必填；更新时不可变更）
     */
    private String profileCode;

    /**
     * 档案名称
     */
    private String name;

    /**
     * 服务提供商标识（openai/deepseek 等）
     */
    private String provider;

    /**
     * API基础地址
     */
    private String baseUrl;

    /**
     * API密钥（明文；更新时留空表示不修改）
     */
    private String apiKey;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 默认采样温度
     */
    private Double temperature;

    /**
     * 默认最大输出token数
     */
    private Integer maxTokens;

    /**
     * 默认Top P采样参数
     */
    private Double topP;

    /**
     * 请求超时（毫秒）
     */
    private Integer timeoutMs;

    /**
     * 扩展参数，透传厂商私有参数
     */
    private Map<String, Object> options;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;
}
