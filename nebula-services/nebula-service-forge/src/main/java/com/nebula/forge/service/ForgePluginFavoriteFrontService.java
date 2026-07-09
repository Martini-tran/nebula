package com.nebula.forge.service;

import com.nebula.common.core.domain.PageQuery;
import com.nebula.common.core.domain.PageResult;
import com.nebula.forge.vo.front.ForgePluginFrontVO;

/**
 * 插件收藏前台服务（需登录）
 *
 * @author nebula
 */
public interface ForgePluginFavoriteFrontService {

    /**
     * 收藏插件（幂等：已收藏则不重复添加）
     */
    void add(Long pluginId);

    /**
     * 取消收藏（幂等：未收藏则忽略）
     */
    void remove(Long pluginId);

    /**
     * 我的收藏列表（分页，仅含已上架插件）
     */
    PageResult<ForgePluginFrontVO> myFavorites(PageQuery query);

    /**
     * 当前用户是否已收藏该插件
     */
    boolean isFavorited(Long pluginId);
}
