package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.dto.me.ShareCreateRequest;
import com.nebula.space.service.SpaceShareService;
import com.nebula.space.vo.me.ShareVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台文件分享控制器：生成、查看、取消自己的分享链接
 */
@RestController
@RequestMapping("/me/shares")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceShareController {

    private final SpaceShareService shareService;

    /**
     * 我分享的，还能用的在前
     */
    @GetMapping
    public R<List<ShareVO>> list() {
        return R.success(shareService.list());
    }

    /**
     * 生成链接：有效天数（空为永久）、要不要提取码、最多下载次数（空为不限）
     */
    @PostMapping
    public R<ShareVO> create(@RequestBody @Valid ShareCreateRequest req) {
        return R.success(shareService.create(req));
    }

    /**
     * 让链接失效
     */
    @PutMapping("/{id}/revoke")
    public R<Void> revoke(@PathVariable Long id) {
        shareService.revoke(id);
        return R.success();
    }
}
