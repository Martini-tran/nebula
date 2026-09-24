package com.nebula.scribe.service;

import com.nebula.scribe.dto.ChapterCreateRequest;
import com.nebula.scribe.dto.ChapterSaveRequest;
import com.nebula.scribe.vo.ChapterDetailVO;
import com.nebula.scribe.vo.ChapterListVO;

import java.util.List;

/**
 * 章节服务，所有操作都先校验所属作品归当前作者
 */
public interface ScribeChapterService {

    /**
     * 作品的章节目录（不含正文），按排序值升序
     */
    List<ChapterListVO> list(Long workId);

    /**
     * 在所属卷（未指定则最后一卷，无卷则整部作品）末尾新建章节，同步作品章节数
     */
    ChapterDetailVO create(Long workId, ChapterCreateRequest req);

    /**
     * 章节详情（含正文）
     */
    ChapterDetailVO detail(Long workId, Long chapterId);

    /**
     * 保存章节（局部更新 + 修订号比对），正文变化时同步作品累计字数
     */
    ChapterDetailVO save(Long workId, Long chapterId, ChapterSaveRequest req);

    /**
     * 删除章节：软删除，同步作品章节数与字数
     */
    void delete(Long workId, Long chapterId);
}
