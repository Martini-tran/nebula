package com.nebula.manager.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key 列表/详情VO
 * 不含 key 明文：明文只在创建成功时通过 {@link ApiKeyCreatedVO} 返回一次。
 *
 * @author nebula
 */
@Data
public class ApiKeyVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 名称
     */
    private String name;

    /**
     * key 掩码
     */
    private String keyMask;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 所属用户名
     */
    private String username;

    /**
     * 授权范围
     */
    private List<String> scopes;

    /**
     * 过期时间，为空表示永不过期
     */
    private LocalDateTime expireTime;

    /**
     * 是否已过期
     */
    private Boolean expired;

    /**
     * 状态：0=禁用 1=启用
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
}
