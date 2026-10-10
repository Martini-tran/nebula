package com.nebula.manager.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key 创建/更新请求DTO
 * key 本身由服务端生成，不接受客户端传入；所属用户固定为创建人，创建后不可变更。
 *
 * @author nebula
 */
@Data
public class ApiKeySaveRequest {

    /**
     * 名称
     */
    private String name;

    /**
     * 授权范围（与接口上 {@code @SaCheckApiKey} 的 scope 对应，取值为权限码）
     */
    private List<String> scopes;

    /**
     * 过期时间，为空表示永不过期
     */
    private LocalDateTime expireTime;

    /**
     * 备注
     */
    private String remark;
}
