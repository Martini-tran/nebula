package com.nebula.scribe.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.scribe.dto.WorkCreateRequest;
import com.nebula.scribe.dto.WorkPageQuery;
import com.nebula.scribe.dto.WorkUpdateRequest;
import com.nebula.scribe.service.ScribeWorkService;
import com.nebula.scribe.vo.WorkDetailVO;
import com.nebula.scribe.vo.WorkListVO;
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

/**
 * 作者端作品控制器
 *
 * <p>C 端接口只要求登录，不挂权限点：注册用户默认无角色，数据隔离靠服务层按 user_id 过滤。</p>
 */
@RestController
@RequestMapping("/works")
@SaCheckLogin
@RequiredArgsConstructor
public class WorkController {

    private final ScribeWorkService workService;

    /**
     * 分页查询我的作品
     */
    @GetMapping
    public R<PageResult<WorkListVO>> page(WorkPageQuery query) {
        return R.success(workService.page(query));
    }

    /**
     * 新建作品
     */
    @PostMapping
    public R<WorkListVO> create(@RequestBody @Valid WorkCreateRequest req) {
        return R.success(workService.create(req));
    }

    /**
     * 作品详情
     */
    @GetMapping("/{id}")
    public R<WorkDetailVO> detail(@PathVariable Long id) {
        return R.success(workService.detail(id));
    }

    /**
     * 修改作品
     */
    @PutMapping("/{id}")
    public R<WorkDetailVO> update(@PathVariable Long id, @RequestBody @Valid WorkUpdateRequest req) {
        return R.success(workService.update(id, req));
    }

    /**
     * 删除作品（移入回收站）
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        workService.delete(id);
        return R.success();
    }
}
