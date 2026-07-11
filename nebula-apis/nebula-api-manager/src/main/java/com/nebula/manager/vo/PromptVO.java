package com.nebula.manager.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI提示词行（管理员端）
 *
 * @author nebula
 */
@Data
public class PromptVO {

    /**
     * 提示词ID
     */
    private Long id;

    /**
     * 提示词编码
     */
    private String promptCode;

    /**
     * 提示词名称
     */
    private String name;

    /**
     * 消息角色：system/user/assistant
     */
    private String role;

    /**
     * 提示词正文
     */
    private String content;

    /**
     * 变量声明数组
     */
    private List<Map<String, Object>> variables;

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
