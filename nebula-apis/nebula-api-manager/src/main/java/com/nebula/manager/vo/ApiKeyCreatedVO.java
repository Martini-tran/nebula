package com.nebula.manager.vo;

import lombok.Data;

/**
 * API Key 创建结果VO
 * 唯一一次返回 key 明文的地方，之后任何接口都只给掩码。
 *
 * @author nebula
 */
@Data
public class ApiKeyCreatedVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * key 明文
     */
    private String apiKey;
}
