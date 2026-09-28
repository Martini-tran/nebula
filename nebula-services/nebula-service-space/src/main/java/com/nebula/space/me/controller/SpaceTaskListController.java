package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.dto.me.TaskListSaveRequest;
import com.nebula.space.service.SpaceTaskListService;
import com.nebula.space.vo.me.TaskListVO;
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
 * 前台任务清单控制器
 */
@RestController
@RequestMapping("/me/task-lists")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceTaskListController {

    private final SpaceTaskListService listService;

    /**
     * 清单列表
     */
    @GetMapping
    public R<List<TaskListVO>> list() {
        return R.success(listService.list());
    }

    /**
     * 新建清单：同名返回 409
     */
    @PostMapping
    public R<TaskListVO> create(@RequestBody @Valid TaskListSaveRequest req) {
        return R.success(listService.create(req));
    }

    /**
     * 改名 / 改色
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid TaskListSaveRequest req) {
        listService.update(id, req);
        return R.success();
    }

    /**
     * 删除清单，其中的任务回到「无清单」
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        listService.delete(id);
        return R.success();
    }
}
