package com.nebula.forge.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 插件管理VO（管理员端）
 *
 * @author nebula
 */
@Data
public class ForgePluginAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 插件ID
     */
    private Long id;

    /**
     * 插件唯一标识
     */
    private String pluginKey;

    /**
     * 插件名称
     */
    private String name;

    /**
     * 插件类型：inline/view
     */
    private String type;

    /**
     * 一句话简介
     */
    private String summary;

    /**
     * 插件详情
     */
    private String description;

    /**
     * 搜索关键词
     */
    private String keywords;

    /**
     * 图标文件ID
     */
    private Long iconFileId;

    /**
     * 封面文件ID
     */
    private Long coverFileId;

    /**
     * 作者用户ID
     */
    private Long authorUserId;

    /**
     * 作者展示名
     */
    private String authorName;

    /**
     * 主页地址
     */
    private String homepageUrl;

    /**
     * 源码仓库地址
     */
    private String repoUrl;

    /**
     * 许可证
     */
    private String license;

    /**
     * 定价类型：1免费 2付费 3订阅 4外部购买
     */
    private Integer pricingType;

    /**
     * 展示价格
     */
    private BigDecimal price;

    /**
     * 原价/划线价
     */
    private BigDecimal originalPrice;

    /**
     * 币种
     */
    private String currency;

    /**
     * 价格展示文案
     */
    private String priceText;

    /**
     * 外部购买地址
     */
    private String purchaseUrl;

    /**
     * 最新版本ID
     */
    private Long latestVersionId;

    /**
     * 最新版本号
     */
    private String latestVersion;

    /**
     * 下载次数
     */
    private Long downloadCount;

    /**
     * 安装次数
     */
    private Long installCount;

    /**
     * 收藏次数
     */
    private Long favoriteCount;

    /**
     * 评分
     */
    private BigDecimal ratingScore;

    /**
     * 评分人数
     */
    private Integer ratingCount;

    /**
     * 是否推荐：1是 0否
     */
    private Integer isFeatured;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 状态：0草稿 1上架 2下架 3封禁
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

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 关联分类ID列表
     */
    private List<Long> categoryIds;

    /**
     * 关联分类名称列表
     */
    private List<String> categoryNames;
}
