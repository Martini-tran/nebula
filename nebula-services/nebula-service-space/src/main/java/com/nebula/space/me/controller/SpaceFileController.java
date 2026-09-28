package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.dto.me.FileFolderCreateRequest;
import com.nebula.space.dto.me.FileQuery;
import com.nebula.space.dto.me.FileUpdateRequest;
import com.nebula.space.files.FileResponses;
import com.nebula.space.service.SpaceFileService;
import com.nebula.space.vo.me.FileUsageVO;
import com.nebula.space.vo.me.SpaceFileVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 前台文件柜控制器
 *
 * <p>/me/** 只校验登录，数据按当前用户隔离。文件内容存在公共文件组件里（私有桶），
 * 下载经这里转发，不给浏览器对象存储地址。</p>
 */
@RestController
@RequestMapping("/me/files")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceFileController {

    private final SpaceFileService fileService;

    /**
     * 按视图列文件：folder（默认，配 folderId）/ recent / trash / kind（配 kind）/ source（配 source）/ all
     */
    @GetMapping
    public R<List<SpaceFileVO>> list(@Valid FileQuery query) {
        return R.success(fileService.list(query));
    }

    /**
     * 容量：已用、总量、单个文件上限、按类型分
     */
    @GetMapping("/usage")
    public R<FileUsageVO> usage() {
        return R.success(fileService.usage());
    }

    @GetMapping("/{id}")
    public R<SpaceFileVO> get(@PathVariable Long id) {
        return R.success(fileService.get(id));
    }

    /**
     * 新建文件夹
     */
    @PostMapping("/folders")
    public R<SpaceFileVO> createFolder(@RequestBody @Valid FileFolderCreateRequest req) {
        return R.success(fileService.createFolder(req));
    }

    /**
     * 上传文件；folderId 不传为根目录，同名自动改名
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<SpaceFileVO> upload(@RequestPart("file") MultipartFile file,
                                 @RequestParam(value = "folderId", required = false) Long folderId) {
        return R.success(fileService.upload(file, folderId));
    }

    /**
     * 重命名 / 移动：不传的字段不动，folderId 传 null 为移到根目录
     */
    @PutMapping("/{id}")
    public R<SpaceFileVO> update(@PathVariable Long id, @RequestBody @Valid FileUpdateRequest req) {
        return R.success(fileService.update(id, req));
    }

    /**
     * 删除：默认移进最近删除（30 天后自动彻底删除），purge=true 为彻底删除
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean purge) {
        if (purge) {
            fileService.purge(id);
        } else {
            fileService.trash(id);
        }
        return R.success();
    }

    /**
     * 从最近删除恢复
     */
    @PutMapping("/{id}/restore")
    public R<Void> restore(@PathVariable Long id) {
        fileService.restore(id);
        return R.success();
    }

    /**
     * 文件内容（预览和下载共用）；出错时回的是 JSON
     */
    @GetMapping("/{id}/content")
    public void content(@PathVariable Long id, HttpServletResponse response) throws IOException {
        FileResponses.send(fileService.download(id), response);
    }
}
