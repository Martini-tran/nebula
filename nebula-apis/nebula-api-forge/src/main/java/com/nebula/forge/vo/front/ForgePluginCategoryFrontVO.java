package com.nebula.forge.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 插件分类VO（前台）
 *
 * @author nebula
 */
@Data
public class ForgePluginCategoryFrontVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分类ID */
    private Long id;

    /** 分类编码 */
    private String code;

    /** 分类名称 */
    private String name;

    /** 分类描述 */
    private String description;

    /** 图标文件ID */
    private Long iconFileId;

    /** 图标访问地址（由文件ID解析） */
    private String iconUrl;

    /** 排序 */
    private Integer sortOrder;
}
