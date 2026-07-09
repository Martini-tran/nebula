package com.nebula.forge.vo.front;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 插件详情VO（前台）
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForgePluginDetailFrontVO extends ForgePluginFrontVO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 插件详情，Markdown 或 HTML */
    private String description;

    /** 搜索关键词 */
    private String keywords;

    /** 主页地址 */
    private String homepageUrl;

    /** 源码仓库地址 */
    private String repoUrl;

    /** 许可证 */
    private String license;

    /** 外部购买地址 */
    private String purchaseUrl;

    /** 最新版本ID */
    private Long latestVersionId;

    /** 安装次数 */
    private Long installCount;

    /** 收藏次数 */
    private Long favoriteCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 所属分类ID */
    private List<Long> categoryIds;
}
