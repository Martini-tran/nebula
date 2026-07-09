package com.nebula.forge.dto.admin;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 插件分类更新请求（选择性更新，非空字段才更新）
 *
 * @author nebula
 */
@Data
public class ForgePluginCategoryUpdateRequest {

    /**
     * 分类编码
     */
    @Size(max = 50, message = "分类编码长度不能超过50")
    private String code;

    /**
     * 分类名称
     */
    @Size(max = 100, message = "分类名称长度不能超过100")
    private String name;

    /**
     * 分类说明
     */
    @Size(max = 500, message = "分类说明长度不能超过500")
    private String description;

    /**
     * 分类图标文件ID，关联 sys_file
     */
    private Long iconFileId;

    /**
     * 排序，越小越靠前
     */
    private Integer sortOrder;

    /**
     * 状态：1启用 0停用
     */
    private Integer status;
}
