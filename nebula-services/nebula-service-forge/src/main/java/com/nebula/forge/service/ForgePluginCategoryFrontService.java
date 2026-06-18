package com.nebula.forge.service;

import com.nebula.forge.vo.front.ForgePluginCategoryFrontVO;

import java.util.List;

/**
 * 插件分类前台服务（只读）
 *
 * @author nebula
 */
public interface ForgePluginCategoryFrontService {

    /**
     * 查询启用中的分类列表（按排序升序），供前台筛选。
     */
    List<ForgePluginCategoryFrontVO> list();
}
