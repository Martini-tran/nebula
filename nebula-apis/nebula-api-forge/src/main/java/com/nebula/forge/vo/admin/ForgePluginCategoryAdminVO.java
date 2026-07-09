package com.nebula.forge.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 插件分类管理VO（管理员端）
 *
 * @author nebula
 */
@Data
public class ForgePluginCategoryAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分类ID
     */
    private Long id;

    /**
     * 分类编码
     */
    private String code;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 分类说明
     */
    private String description;

    /**
     * 分类图标文件ID
     */
    private Long iconFileId;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 状态：1启用 0停用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
