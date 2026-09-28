package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.dto.me.MeetingQuery;
import com.nebula.space.dto.me.MeetingSaveRequest;
import com.nebula.space.service.SpaceMeetingService;
import com.nebula.space.vo.me.MeetingVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台会议记录控制器
 *
 * <p>/me/** 只校验登录，数据按当前用户隔离，不走后台权限码。</p>
 */
@RestController
@RequestMapping("/me/meetings")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceMeetingController {

    private final SpaceMeetingService meetingService;

    /**
     * 会议列表：按日期、开始时间升序，可按起止日期筛选
     */
    @GetMapping
    public R<List<MeetingVO>> list(MeetingQuery query) {
        return R.success(meetingService.list(query));
    }

    /**
     * 会议详情
     */
    @GetMapping("/{id}")
    public R<MeetingVO> detail(@PathVariable Long id) {
        return R.success(meetingService.detail(id));
    }

    /**
     * 新建会议：标题、日期、开始时间必填
     */
    @PostMapping
    public R<MeetingVO> create(@RequestBody @Valid MeetingSaveRequest req) {
        return R.success(meetingService.create(req));
    }

    /**
     * 局部保存：请求里出现的字段才修改，可空字段传 null 表示清空
     */
    @PutMapping("/{id}")
    public R<MeetingVO> update(@PathVariable Long id, @RequestBody @Valid MeetingSaveRequest req) {
        return R.success(meetingService.update(id, req));
    }

    /**
     * 删除会议（从会议转出的任务保留）
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        meetingService.delete(id);
        return R.success();
    }
}
