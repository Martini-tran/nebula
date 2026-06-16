package com.nebula.forge.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 插件列表VO（前台）
 *
 * @author nebula
 */
@Data
public class ForgePluginFrontVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 插件ID */
    private Long id;

    /** 插件唯一标识 */
    private String pluginKey;

    /** 插件名称 */
    private String name;

    /** 一句话简介 */
    private String summary;

    /** 插件类型：inline/view */
    private String type;

    /** 图标文件ID */
    private Long iconFileId;

    /** 封面文件ID */
    private Long coverFileId;

    /** 作者展示名 */
    private String authorName;

    /** 定价类型：1免费 2付费 3订阅 4外部购买 */
    private Integer pricingType;

    /** 展示价格 */
    private BigDecimal price;

    /** 原价/划线价 */
    private BigDecimal originalPrice;

    /** 币种 */
    private String currency;

    /** 价格展示文案 */
    private String priceText;

    /** 最新版本号 */
    private String latestVersion;

    /** 下载次数 */
    private Long downloadCount;

    /** 评分 */
    private BigDecimal ratingScore;

    /** 评分人数 */
    private Integer ratingCount;

    /** 是否推荐：1是 0否 */
    private Integer isFeatured;

    /** 所属分类名称 */
    private List<String> categoryNames;
}
