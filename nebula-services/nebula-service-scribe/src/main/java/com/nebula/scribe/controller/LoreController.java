package com.nebula.scribe.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.scribe.dto.LoreSaveRequest;
import com.nebula.scribe.service.ScribeLoreService;
import com.nebula.scribe.vo.LoreEntryVO;
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
 * 作者端设定库控制器
 */
@RestController
@RequestMapping("/works/{workId}/lore")
@SaCheckLogin
@RequiredArgsConstructor
public class LoreController {

    private final ScribeLoreService loreService;

    /**
     * 作品的设定条目，可按类型过滤；不带详细设定
     */
    @GetMapping
    public R<List<LoreEntryVO>> list(@PathVariable Long workId, @RequestParam(required = false) String kind) {
        return R.success(loreService.list(workId, kind));
    }

    /**
     * 设定详情
     */
    @GetMapping("/{entryId}")
    public R<LoreEntryVO> detail(@PathVariable Long workId, @PathVariable Long entryId) {
        return R.success(loreService.detail(workId, entryId));
    }

    /**
     * 新建设定
     */
    @PostMapping
    public R<LoreEntryVO> create(@PathVariable Long workId, @RequestBody @Valid LoreSaveRequest req) {
        return R.success(loreService.create(workId, req));
    }

    /**
     * 修改设定（整表单覆盖）
     */
    @PutMapping("/{entryId}")
    public R<LoreEntryVO> update(@PathVariable Long workId, @PathVariable Long entryId,
                                 @RequestBody @Valid LoreSaveRequest req) {
        return R.success(loreService.update(workId, entryId, req));
    }

    /**
     * 删除设定（移入回收站）
     */
    @DeleteMapping("/{entryId}")
    public R<Void> delete(@PathVariable Long workId, @PathVariable Long entryId) {
        loreService.delete(workId, entryId);
        return R.success();
    }
}
