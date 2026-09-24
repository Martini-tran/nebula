package com.nebula.scribe.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.scribe.dto.TocSortRequest;
import com.nebula.scribe.dto.VolumeSaveRequest;
import com.nebula.scribe.service.ScribeVolumeService;
import com.nebula.scribe.vo.TocVO;
import com.nebula.scribe.vo.VolumeVO;
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
 * 作者端卷与目录控制器
 *
 * <p>卷是可选的一层，规则见设计文档 4.3；会改动目录结构的操作（删卷、重排）返回整棵新目录。</p>
 */
@RestController
@RequestMapping("/works/{workId}")
@SaCheckLogin
@RequiredArgsConstructor
public class VolumeController {

    private final ScribeVolumeService volumeService;

    /**
     * 作品的卷
     */
    @GetMapping("/volumes")
    public R<List<VolumeVO>> list(@PathVariable Long workId) {
        return R.success(volumeService.list(workId));
    }

    /**
     * 新建卷：追加到最后；第一卷会收编现有章节
     */
    @PostMapping("/volumes")
    public R<VolumeVO> create(@PathVariable Long workId, @RequestBody(required = false) @Valid VolumeSaveRequest req) {
        return R.success(volumeService.create(workId, req));
    }

    /**
     * 修改卷名与梗概
     */
    @PutMapping("/volumes/{volumeId}")
    public R<VolumeVO> update(@PathVariable Long workId, @PathVariable Long volumeId,
                              @RequestBody @Valid VolumeSaveRequest req) {
        return R.success(volumeService.update(workId, volumeId, req));
    }

    /**
     * 删除卷：只删卷不删章节，章节并入相邻卷
     */
    @DeleteMapping("/volumes/{volumeId}")
    public R<TocVO> delete(@PathVariable Long workId, @PathVariable Long volumeId) {
        return R.success(volumeService.delete(workId, volumeId));
    }

    /**
     * 整棵目录树重排
     */
    @PutMapping("/toc")
    public R<TocVO> sort(@PathVariable Long workId, @RequestBody TocSortRequest req) {
        return R.success(volumeService.sort(workId, req));
    }
}
