package com.nebula.manager.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI模型档案行（管理员端）
 * 出于安全考虑不回传明文 apiKey，仅以 {@code apiKeyMasked} 给出掩码、以 {@code hasApiKey} 标记是否已配置。
 *
 * @author nebula
 */
@Data
public class ModelProfileVO {

    /**
     * 档案ID
     */
    private Long id;

    /**
     * 档案编码
     */
    private String profileCode;

    /**
     * 档案名称
     */
    private String name;

    /**
     * 服务提供商标识
     */
    private String provider;

    /**
     * API基础地址
     */
    private String baseUrl;

    /**
     * API密钥掩码（如 sk-****1234），不含明文
     */
    private String apiKeyMasked;

    /**
     * 是否已配置 API 密钥
     */
    private Boolean hasApiKey;

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
     * 扩展参数
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

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
