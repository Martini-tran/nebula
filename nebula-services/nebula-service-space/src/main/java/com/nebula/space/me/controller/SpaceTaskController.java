package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.dto.me.TaskDoneRequest;
import com.nebula.space.dto.me.TaskQuery;
import com.nebula.space.dto.me.TaskSaveRequest;
import com.nebula.space.service.SpaceTaskService;
import com.nebula.space.vo.me.TaskCompleteVO;
import com.nebula.space.vo.me.TaskStatsVO;
import com.nebula.space.vo.me.TaskVO;
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
 * 前台任务控制器
 *
 * <p>/me/** 只校验登录，数据按当前用户隔离，不走后台权限码。</p>
 */
@RestController
@RequestMapping("/me/tasks")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceTaskController {

    private final SpaceTaskService taskService;

    /**
     * 任务列表：按视图、清单、来源筛选，不分页（已完成视图最多 200 条）
     */
    @GetMapping
    public R<List<TaskVO>> list(TaskQuery query) {
        return R.success(taskService.list(query));
    }

    /**
     * 侧栏计数
     */
    @GetMapping("/stats")
    public R<TaskStatsVO> stats() {
        return R.success(taskService.stats());
    }

    /**
     * 任务详情
     */
    @GetMapping("/{id}")
    public R<TaskVO> detail(@PathVariable Long id) {
        return R.success(taskService.detail(id));
    }

    /**
     * 新建任务
     */
    @PostMapping
    public R<TaskVO> create(@RequestBody @Valid TaskSaveRequest req) {
        return R.success(taskService.create(req));
    }

    /**
     * 局部保存：请求里出现的字段才修改，传 null 表示清空
     */
    @PutMapping("/{id}")
    public R<TaskVO> update(@PathVariable Long id, @RequestBody @Valid TaskSaveRequest req) {
        return R.success(taskService.update(id, req));
    }

    /**
     * 完成 / 撤销完成
     */
    @PutMapping("/{id}/done")
    public R<TaskCompleteVO> complete(@PathVariable Long id, @RequestBody @Valid TaskDoneRequest req) {
        return R.success(taskService.complete(id, req.getDone()));
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return R.success();
    }
}
