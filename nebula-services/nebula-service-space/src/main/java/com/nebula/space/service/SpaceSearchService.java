package com.nebula.space.service;

import com.nebula.space.vo.me.SearchResultVO;

/**
 * 全局搜索：只查当前登录用户自己的数据
 */
public interface SpaceSearchService {

    /**
     * 按前端的搜索语法（b: #标签 @某人 is: after: before: "短语"）从各模块召回候选
     */
    SearchResultVO search(String q);
}
