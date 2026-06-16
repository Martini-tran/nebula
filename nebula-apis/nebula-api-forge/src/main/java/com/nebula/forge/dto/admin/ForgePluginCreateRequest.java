package com.nebula.forge.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 插件创建请求
 *
 * @author nebula
 */
@Data
public class ForgePluginCreateRequest {

    /**
     * 插件唯一标识，对应 plugin.json 的 id
     */
    @NotBlank(message = "插件标识不能为空")
    @Size(max = 100, message = "插件标识长度不能超过100")
    private String pluginKey;

    /**
     * 插件名称
     */
    @NotBlank(message = "插件名称不能为空")
    @Size(max = 100, message = "插件名称长度不能超过100")
    private String name;

    /**
     * 插件类型：inline/view
     */
    private String type;

    /**
     * 一句话简介
     */
    @Size(max = 255, message = "简介长度不能超过255")
    private String summary;

    /**
     * 插件详情，Markdown 或 HTML
     */
    private String description;

    /**
     * 搜索关键词，多个用逗号分隔
     */
    @Size(max = 500, message = "关键词长度不能超过500")
    private String keywords;

    /**
     * 图标文件ID，关联 sys_file
     */
    private Long iconFileId;

    /**
     * 封面文件ID，关联 sys_file
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
    private Integer pricingType = 1;

    /**
     * 展示价格，免费可为空
     */
    private BigDecimal price;

    /**
     * 原价/划线价
     */
    private BigDecimal originalPrice;

    /**
     * 币种
     */
    private String currency = "CNY";

    /**
     * 价格展示文案
     */
    private String priceText;

    /**
     * 外部购买地址
     */
    private String purchaseUrl;

    /**
     * 是否推荐：1是 0否
     */
    private Integer isFeatured = 0;

    /**
     * 排序
     */
    private Integer sortOrder = 0;

    /**
     * 状态：0草稿 1上架 2下架 3封禁
     */
    private Integer status = 0;

    /**
     * 备注
     */
    private String remark;

    /**
     * 关联分类ID列表，可选
     */
    private List<Long> categoryIds;
}
