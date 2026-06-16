package com.nebula.forge.dto.admin;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

/**
 * 插件版本权限声明全量替换请求
 *
 * @author nebula
 */
@Data
public class ForgePluginPermissionBindRequest {

    /**
     * 权限声明列表，传空列表表示清空
     */
    @Valid
    private List<ForgePluginPermissionItem> permissions;
}
