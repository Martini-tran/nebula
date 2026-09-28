package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.dto.me.HighlightCreateRequest;
import com.nebula.space.dto.me.HighlightQuery;
import com.nebula.space.dto.me.HighlightSaveRequest;
import com.nebula.space.dto.me.ReadingCreateRequest;
import com.nebula.space.dto.me.ReadingQuery;
import com.nebula.space.dto.me.ReadingSaveRequest;
import com.nebula.space.service.SpaceReadingService;
import com.nebula.space.vo.me.HighlightVO;
import com.nebula.space.vo.me.ReadingItemVO;
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
 * 前台稍后读控制器：文章与划线
 *
 * <p>/me/** 只校验登录，数据按当前用户隔离，不走后台权限码。</p>
 */
@RestController
@RequestMapping("/me")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceReadingController {

    private final SpaceReadingService readingService;

    /**
     * 文章列表，新加入的在前；默认只看队列里的（不含归档），不带正文
     */
    @GetMapping("/reading")
    public R<List<ReadingItemVO>> list(@Valid ReadingQuery query) {
        return R.success(readingService.list(query));
    }

    /**
     * 单篇，带存档的正文
     */
    @GetMapping("/reading/{id}")
    public R<ReadingItemVO> get(@PathVariable Long id) {
        return R.success(readingService.get(id));
    }

    /**
     * 加入稍后读：抓取正文存档；同一网址已经在了就返回原来那条
     */
    @PostMapping("/reading")
    public R<ReadingItemVO> create(@RequestBody @Valid ReadingCreateRequest req) {
        return R.success(readingService.create(req));
    }

    /**
     * 之前没抓到正文的再抓一次
     */
    @PostMapping("/reading/{id}/fetch")
    public R<ReadingItemVO> refetch(@PathVariable Long id) {
        return R.success(readingService.refetch(id));
    }

    /**
     * 改阅读状态、进度、位置、读后感、归档：不传的字段不动
     */
    @PutMapping("/reading/{id}")
    public R<ReadingItemVO> update(@PathVariable Long id, @RequestBody @Valid ReadingSaveRequest req) {
        return R.success(readingService.update(id, req));
    }

    /**
     * 删除文章和它的划线
     */
    @DeleteMapping("/reading/{id}")
    public R<Void> delete(@PathVariable Long id) {
        readingService.delete(id);
        return R.success();
    }

    /**
     * 划线，新的在前；可按文章筛
     */
    @GetMapping("/highlights")
    public R<List<HighlightVO>> highlights(HighlightQuery query) {
        return R.success(readingService.listHighlights(query));
    }

    /**
     * 新建划线
     */
    @PostMapping("/highlights")
    public R<HighlightVO> createHighlight(@RequestBody @Valid HighlightCreateRequest req) {
        return R.success(readingService.createHighlight(req));
    }

    /**
     * 改颜色、批注，记下转出的随手记 / 任务
     */
    @PutMapping("/highlights/{id}")
    public R<HighlightVO> updateHighlight(@PathVariable Long id, @RequestBody @Valid HighlightSaveRequest req) {
        return R.success(readingService.updateHighlight(id, req));
    }

    /**
     * 删除划线
     */
    @DeleteMapping("/highlights/{id}")
    public R<Void> deleteHighlight(@PathVariable Long id) {
        readingService.deleteHighlight(id);
        return R.success();
    }
}
