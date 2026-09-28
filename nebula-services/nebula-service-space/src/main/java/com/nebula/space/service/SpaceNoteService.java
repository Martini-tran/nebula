package com.nebula.space.service;

import com.nebula.space.dto.me.NoteQuery;
import com.nebula.space.dto.me.NoteSaveRequest;
import com.nebula.space.vo.me.NoteStatsVO;
import com.nebula.space.vo.me.NoteVO;

import java.util.List;

/**
 * 随手记服务：只操作当前登录用户自己的笔记
 */
public interface SpaceNoteService {

    List<NoteVO> list(NoteQuery query);

    NoteVO detail(Long id);

    NoteStatsVO stats();

    NoteVO create(NoteSaveRequest req);

    NoteVO update(Long id, NoteSaveRequest req);

    void delete(Long id);
}
