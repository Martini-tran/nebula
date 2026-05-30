package com.nebula.manager.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.common.file.dto.FileBindRequest;
import com.nebula.common.file.dto.FilePageQuery;
import com.nebula.common.file.dto.FileUploadRequest;
import com.nebula.common.file.service.SysFileService;
import com.nebula.common.file.vo.FileInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 系统文件控制器
 * 提供公共文件上传、下载、查询、临时URL生成、绑定、删除等接口
 *
 * @author nebula
 */
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class SysFileController {

    private final SysFileService sysFileService;

    /**
     * 上传文件
     * 使用 multipart/form-data，业务侧可以通过表单字段携带 targetType/targetId/fileType 等元信息
     *
     * @param file    上传文件
     * @param request 上传请求参数
     * @return 文件元信息
     */
    @SaCheckLogin
    @PostMapping("/upload")
    public R<FileInfoVO> upload(@RequestParam("file") MultipartFile file,
                                @ModelAttribute FileUploadRequest request) {
        return R.success(sysFileService.upload(file, request));
    }

    /**
     * 获取文件元信息
     *
     * @param id 文件ID
     * @return 文件元信息
     */
    @SaCheckLogin
    @GetMapping("/{id}")
    public R<FileInfoVO> get(@PathVariable("id") Long id) {
        return R.success(sysFileService.getById(id));
    }

    /**
     * 分页查询文件列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @SaCheckPermission("system:file:list")
    @GetMapping("/page")
    public R<PageResult<FileInfoVO>> page(@ModelAttribute FilePageQuery query) {
        return R.success(sysFileService.page(query));
    }

    /**
     * 按业务关联查询文件列表
     *
     * @param targetType 业务类型
     * @param targetId   业务ID
     * @param fileType   文件用途（可选）
     * @return 文件列表
     */
    @SaCheckLogin
    @GetMapping("/list")
    public R<List<FileInfoVO>> list(@RequestParam("targetType") String targetType,
                                    @RequestParam("targetId") Long targetId,
                                    @RequestParam(value = "fileType", required = false) String fileType) {
        return R.success(sysFileService.listByTarget(targetType, targetId, fileType));
    }

    /**
     * 获取文件访问URL
     * 公开文件返回永久URL，私有文件返回默认有效期的临时签名URL
     *
     * @param id 文件ID
     * @return 文件访问URL
     */
    @SaCheckLogin
    @GetMapping("/{id}/url")
    public R<String> url(@PathVariable("id") Long id) {
        return R.success(sysFileService.getAccessUrl(id));
    }

    /**
     * 获取文件临时签名URL
     * 不论文件是否公开，均返回签名URL
     *
     * @param id            文件ID
     * @param expirySeconds 有效期（秒），可选
     * @return 临时URL
     */
    @SaCheckLogin
    @GetMapping("/{id}/presigned-url")
    public R<String> presignedUrl(@PathVariable("id") Long id,
                                  @RequestParam(value = "expirySeconds", required = false) Integer expirySeconds) {
        return R.success(sysFileService.getPresignedUrl(id, expirySeconds));
    }

    /**
     * 下载文件
     *
     * @param id 文件ID
     * @return 文件流
     */
    @SaCheckLogin
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable("id") Long id) {
        FileInfoVO info = sysFileService.getById(id);
        InputStream inputStream = sysFileService.download(id);
        String filename = info.getOriginalFilename() != null ? info.getOriginalFilename() : ("file-" + id);
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        if (info.getSizeBytes() != null) {
            headers.setContentLength(info.getSizeBytes());
        }
        MediaType mediaType = info.getMimeType() != null
                ? MediaType.parseMediaType(info.getMimeType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(mediaType)
                .body(new InputStreamResource(inputStream));
    }

    /**
     * 将文件绑定到业务实体
     * 用于上传时未确定 targetId 的场景，业务实体创建后再回填
     *
     * @param id      文件ID
     * @param request 绑定参数
     * @return 操作结果
     */
    @SaCheckLogin
    @PutMapping("/{id}/bind")
    public R<Void> bind(@PathVariable("id") Long id, @RequestBody FileBindRequest request) {
        sysFileService.bind(id, request);
        return R.success("bind success", null);
    }

    /**
     * 删除文件
     *
     * @param id            文件ID
     * @param removeStorage 是否同时从对象存储中删除（默认 false）
     * @return 操作结果
     */
    @SaCheckPermission("system:file:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id,
                          @RequestParam(value = "removeStorage", required = false, defaultValue = "false")
                          Boolean removeStorage) {
        sysFileService.delete(id, Boolean.TRUE.equals(removeStorage));
        return R.success("delete success", null);
    }
}
