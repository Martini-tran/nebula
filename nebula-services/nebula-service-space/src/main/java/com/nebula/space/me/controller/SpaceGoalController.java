package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.dto.me.GoalSaveRequest;
import com.nebula.space.service.SpaceGoalService;
import com.nebula.space.vo.me.GoalVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台年度目标控制器
 *
 * <p>/me/** 只校验登录，数据按当前用户隔离，不走后台权限码。</p>
 */
@RestController
@RequestMapping("/me/goals")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceGoalController {

    private final SpaceGoalService goalService;

    /**
     * 某一年的目标（不传为今年），按排序号
     */
    @GetMapping
    public R<List<GoalVO>> list(@RequestParam(required = false) Integer year) {
        return R.success(goalService.list(year));
    }

    /**
     * 新建目标：目标、年份必填
     */
    @PostMapping
    public R<GoalVO> create(@RequestBody @Valid GoalSaveRequest req) {
        return R.success(goalService.create(req));
    }

    /**
     * 局部保存：请求里出现的字段才修改
     */
    @PutMapping("/{id}")
    public R<GoalVO> update(@PathVariable Long id, @RequestBody @Valid GoalSaveRequest req) {
        return R.success(goalService.update(id, req));
    }

    /**
     * 删除目标（来源数据不受影响）
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        goalService.delete(id);
        return R.success();
    }
}
