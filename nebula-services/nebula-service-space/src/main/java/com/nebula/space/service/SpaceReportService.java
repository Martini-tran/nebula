package com.nebula.space.service;

import com.nebula.space.dto.me.ReportQuery;
import com.nebula.space.dto.me.ReportSaveRequest;
import com.nebula.space.search.SearchCriteria;
import com.nebula.space.vo.me.ReportVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 日报周报服务：只操作当前登录用户自己的报告
 *
 * <p>正文就是 Markdown，不分模板；草稿生成、周报按日报汇总都在前端做。</p>
 */
public interface SpaceReportService {

    List<ReportVO> list(ReportQuery query);

    /**
     * 全局搜索召回：正文命中，日期按日报当天 / 周报周一，近的在前
     */
    List<ReportVO> search(SearchCriteria q, int limit);

    /**
     * 没写过返回 null
     */
    ReportVO get(String type, LocalDate date);

    /**
     * 整份覆盖；正文为空时删除并返回 null
     */
    ReportVO save(String type, LocalDate date, ReportSaveRequest req);
}
