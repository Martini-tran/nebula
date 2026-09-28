package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.dto.me.NoteQuery;
import com.nebula.space.dto.me.NoteSaveRequest;
import com.nebula.space.service.SpaceNoteService;
import com.nebula.space.vo.me.NoteStatsVO;
import com.nebula.space.vo.me.NoteVO;
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
 * 前台随手记控制器
 *
 * <p>/me/** 只校验登录，数据按当前用户隔离，不走后台权限码。</p>
 */
@RestController
@RequestMapping("/me/notes")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceNoteController {

    private final SpaceNoteService noteService;

    /**
     * 笔记列表（按更新时间倒序，不分页）
     */
    @GetMapping
    public R<List<NoteVO>> list(NoteQuery query) {
        return R.success(noteService.list(query));
    }

    /**
     * 侧栏计数与标签
     */
    @GetMapping("/stats")
    public R<NoteStatsVO> stats() {
        return R.success(noteService.stats());
    }

    /**
     * 笔记详情
     */
    @GetMapping("/{id}")
    public R<NoteVO> detail(@PathVariable Long id) {
        return R.success(noteService.detail(id));
    }

    /**
     * 新建笔记
     */
    @PostMapping
    public R<NoteVO> create(@RequestBody @Valid NoteSaveRequest req) {
        return R.success(noteService.create(req));
    }

    /**
     * 局部保存：正文、底色、置顶、标签、归档
     */
    @PutMapping("/{id}")
    public R<NoteVO> update(@PathVariable Long id, @RequestBody @Valid NoteSaveRequest req) {
        return R.success(noteService.update(id, req));
    }

    /**
     * 删除笔记
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        noteService.delete(id);
        return R.success();
    }
}
