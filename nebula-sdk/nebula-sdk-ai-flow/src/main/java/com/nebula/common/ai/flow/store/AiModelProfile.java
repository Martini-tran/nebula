package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI模型档案表
 * 集中维护一组「模型连接 + 默认调用参数」，由 {@code profile_code} 被流程/节点引用复用。
 * 对应 SDK 的 {@code com.nebula.common.ai.flow.ModelProfile}；其中 {@code apiKey} 以密文形式落库
 * （加解密由 {@link DatabaseModelProfileRepository} 负责，实体本身只承载原始列值）。
 *
 * @author nebula
 */
@Data
@TableName("ai_model_profile")
public class AiModelProfile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 档案ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 档案编码，全局唯一，被流程/节点引用
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
     * API密钥（AES加密密文存储）
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
     * 扩展参数，透传厂商私有参数（JSON对象字符串；序列化在仓储层处理）
     */
    private String options;

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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
