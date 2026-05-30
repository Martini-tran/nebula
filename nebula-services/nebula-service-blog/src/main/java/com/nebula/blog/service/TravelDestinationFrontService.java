package com.nebula.blog.service;

import com.nebula.blog.vo.front.TravelDestinationSummaryVO;

import java.util.List;

/**
 * 旅游目的地前台服务接口
 */
public interface TravelDestinationFrontService {

    /**
     * 用于前台筛选的目的地列表（启用项，按 sortOrder + visitCount，限制数量）
     */
    List<TravelDestinationSummaryVO> listChips(int limit);
}
