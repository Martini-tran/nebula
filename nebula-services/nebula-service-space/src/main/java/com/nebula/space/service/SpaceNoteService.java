package com.nebula.space.service;

import com.nebula.space.dto.me.NoteQuery;
import com.nebula.space.dto.me.NoteSaveRequest;
import com.nebula.space.search.SearchCriteria;
import com.nebula.space.vo.me.NoteStatsVO;
import com.nebula.space.vo.me.NoteVO;

import java.util.List;

/**
 * 随手记服务：只操作当前登录用户自己的笔记
 */
public interface SpaceNoteService {

    List<NoteVO> list(NoteQuery query);

    /**
     * 全局搜索召回：正文命中，含归档的；#标签、日期（创建日）先在库里筛，最近改过的在前
     */
    List<NoteVO> search(SearchCriteria q, int limit);

    NoteVO detail(Long id);

    NoteStatsVO stats();

    NoteVO create(NoteSaveRequest req);

    NoteVO update(Long id, NoteSaveRequest req);

    void delete(Long id);
}
