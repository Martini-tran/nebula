package com.nebula.space.pub.controller;

import com.nebula.common.core.domain.R;
import com.nebula.space.dto.me.ShareDownloadRequest;
import com.nebula.space.files.FileResponses;
import com.nebula.space.service.SpaceShareService;
import com.nebula.space.vo.me.SharePublicVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 分享页接口（/public/**，不需要登录，网关白名单放行）
 *
 * <p>拿到 /s/{code} 链接的人用：先看文件信息，再带提取码下载。下载用 POST，提取码不进地址栏和访问日志。</p>
 */
@RestController
@RequestMapping("/public/shares")
@RequiredArgsConstructor
public class SpaceSharePublicController {

    private final SpaceShareService shareService;

    /**
     * 分享的文件信息；过期、取消、次数用完、文件已删时 unavailable 写明原因
     */
    @GetMapping("/{code}")
    public R<SharePublicVO> info(@PathVariable String code) {
        return R.success(shareService.publicInfo(code));
    }

    /**
     * 下载；文件夹打成 zip。出错时回的是 JSON
     */
    @PostMapping("/{code}/download")
    public void download(@PathVariable String code, @RequestBody(required = false) @Valid ShareDownloadRequest req,
                         HttpServletResponse response) throws IOException {
        FileResponses.send(shareService.download(code, req), response);
    }
}
