package com.nebula.space.service;

import com.nebula.space.dto.me.MeetingQuery;
import com.nebula.space.dto.me.MeetingSaveRequest;
import com.nebula.space.vo.me.MeetingVO;

import java.util.List;

/**
 * 会议记录服务：只操作当前登录用户自己的会议
 *
 * <p>决议与待办不单独建表，由前端按规则从正文识别（utils/meetingItems.ts）。</p>
 */
public interface SpaceMeetingService {

    List<MeetingVO> list(MeetingQuery query);

    MeetingVO detail(Long id);

    MeetingVO create(MeetingSaveRequest req);

    MeetingVO update(Long id, MeetingSaveRequest req);

    void delete(Long id);
}
