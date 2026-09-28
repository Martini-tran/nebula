package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.dto.me.FocusSessionCreateRequest;
import com.nebula.space.dto.me.FocusSessionQuery;
import com.nebula.space.service.SpaceFocusService;
import com.nebula.space.vo.me.FocusSessionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台专注记录控制器
 *
 * <p>/me/** 只校验登录，数据按当前用户隔离，不走后台权限码。</p>
 */
@RestController
@RequestMapping("/me/focus-sessions")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceFocusController {

    private final SpaceFocusService focusService;

    /**
     * 专注记录列表：按开始时间升序，可按开始日期、任务筛选
     */
    @GetMapping
    public R<List<FocusSessionVO>> list(FocusSessionQuery query) {
        return R.success(focusService.list(query));
    }

    /**
     * 上报一轮专注（完成或放弃）
     */
    @PostMapping
    public R<FocusSessionVO> create(@RequestBody @Valid FocusSessionCreateRequest req) {
        return R.success(focusService.create(req));
    }
}
