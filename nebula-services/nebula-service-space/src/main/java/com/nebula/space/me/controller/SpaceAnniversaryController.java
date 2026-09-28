package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.dto.me.AnniversarySaveRequest;
import com.nebula.space.service.SpaceAnniversaryService;
import com.nebula.space.vo.me.AnniversaryVO;
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
 * 前台纪念日控制器
 *
 * <p>/me/** 只校验登录，数据按当前用户隔离，不走后台权限码。</p>
 */
@RestController
@RequestMapping("/me/anniversaries")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceAnniversaryController {

    private final SpaceAnniversaryService annivService;

    /**
     * 全部纪念日（排序由前端按还有几天算）
     */
    @GetMapping
    public R<List<AnniversaryVO>> list() {
        return R.success(annivService.list());
    }

    /**
     * 新建纪念日：名称、日期必填
     */
    @PostMapping
    public R<AnniversaryVO> create(@RequestBody @Valid AnniversarySaveRequest req) {
        return R.success(annivService.create(req));
    }

    /**
     * 局部保存：请求里出现的字段才修改，可空字段传 null 表示清空
     */
    @PutMapping("/{id}")
    public R<AnniversaryVO> update(@PathVariable Long id, @RequestBody @Valid AnniversarySaveRequest req) {
        return R.success(annivService.update(id, req));
    }

    /**
     * 删除纪念日（已生成的任务保留）
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        annivService.delete(id);
        return R.success();
    }
}
