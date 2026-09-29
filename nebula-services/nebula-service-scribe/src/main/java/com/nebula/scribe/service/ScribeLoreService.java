package com.nebula.scribe.service;

import com.nebula.scribe.dto.LoreSaveRequest;
import com.nebula.scribe.vo.LoreEntryVO;

import java.util.List;

/**
 * 设定库服务
 *
 * <p>名称在同一作品内唯一（不分类型）：正文高亮与 AI 引用都按名字匹配，同名会指代不清。</p>
 */
public interface ScribeLoreService {

    /**
     * 作品的设定条目，可按类型过滤；固定的在前，其余按更新时间倒序。不带 detail
     */
    List<LoreEntryVO> list(Long workId, String kind);

    /**
     * 单条设定详情
     */
    LoreEntryVO detail(Long workId, Long entryId);

    /**
     * 新建设定
     */
    LoreEntryVO create(Long workId, LoreSaveRequest req);

    /**
     * 修改设定（整表单覆盖）
     */
    LoreEntryVO update(Long workId, Long entryId, LoreSaveRequest req);

    /**
     * 删除设定（移入回收站）
     */
    void delete(Long workId, Long entryId);
}
