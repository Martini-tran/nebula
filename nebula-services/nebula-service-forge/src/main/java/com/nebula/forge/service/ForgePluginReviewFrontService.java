package com.nebula.forge.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.forge.dto.front.ForgePluginReviewCreateRequest;
import com.nebula.forge.dto.front.ForgePluginReviewFrontPageQuery;
import com.nebula.forge.vo.front.ForgePluginReviewFrontVO;

/**
 * 插件评价前台服务
 * <p>查看为公开能力；发表/删除/点赞需登录。</p>
 *
 * @author nebula
 */
public interface ForgePluginReviewFrontService {

    /**
     * 查看插件的展示中评价（分页，公开）
     */
    PageResult<ForgePluginReviewFrontVO> page(Long pluginId, ForgePluginReviewFrontPageQuery query);

    /**
     * 发表或更新当前用户对插件的评价（按 用户+插件 唯一，存在则覆盖）。
     * <p>提交后重算插件的评分与评分人数。</p>
     */
    ForgePluginReviewFrontVO submit(Long pluginId, ForgePluginReviewCreateRequest req);

    /**
     * 删除当前用户对该插件的评价，并重算评分。
     */
    void deleteMine(Long pluginId);

    /**
     * 给评价点赞（点赞数 +1）。
     */
    void like(Long reviewId);
}
