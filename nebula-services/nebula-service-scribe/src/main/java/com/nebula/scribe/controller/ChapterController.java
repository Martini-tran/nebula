package com.nebula.scribe.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.scribe.dto.ChapterCreateRequest;
import com.nebula.scribe.dto.ChapterSaveRequest;
import com.nebula.scribe.service.ScribeChapterService;
import com.nebula.scribe.vo.ChapterDetailVO;
import com.nebula.scribe.vo.ChapterListVO;
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
 * 作者端章节控制器
 *
 * <p>章节挂在作品路径下，归属校验由服务层先校验作品再限定章节完成。</p>
 */
@RestController
@RequestMapping("/works/{workId}/chapters")
@SaCheckLogin
@RequiredArgsConstructor
public class ChapterController {

    private final ScribeChapterService chapterService;

    /**
     * 章节目录（不含正文）
     */
    @GetMapping
    public R<List<ChapterListVO>> list(@PathVariable Long workId) {
        return R.success(chapterService.list(workId));
    }

    /**
     * 新建章节：可指定卷，不指定则放进最后一卷
     */
    @PostMapping
    public R<ChapterDetailVO> create(@PathVariable Long workId, @RequestBody(required = false) @Valid ChapterCreateRequest req) {
        return R.success(chapterService.create(workId, req));
    }

    /**
     * 章节详情（含正文）
     */
    @GetMapping("/{chapterId}")
    public R<ChapterDetailVO> detail(@PathVariable Long workId, @PathVariable Long chapterId) {
        return R.success(chapterService.detail(workId, chapterId));
    }

    /**
     * 保存章节（局部更新，修订号不一致返回 409）
     */
    @PutMapping("/{chapterId}")
    public R<ChapterDetailVO> save(@PathVariable Long workId, @PathVariable Long chapterId,
                                   @RequestBody @Valid ChapterSaveRequest req) {
        return R.success(chapterService.save(workId, chapterId, req));
    }

    /**
     * 删除章节（移入回收站）
     */
    @DeleteMapping("/{chapterId}")
    public R<Void> delete(@PathVariable Long workId, @PathVariable Long chapterId) {
        chapterService.delete(workId, chapterId);
        return R.success();
    }
}
