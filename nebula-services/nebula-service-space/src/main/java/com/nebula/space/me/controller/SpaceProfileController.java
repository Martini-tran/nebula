package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.dto.me.ProfileSaveRequest;
import com.nebula.space.service.SpaceProfileService;
import com.nebula.space.vo.me.HandleCheckVO;
import com.nebula.space.vo.me.ProfilePublicVO;
import com.nebula.space.vo.me.ProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台公开主页设置控制器
 *
 * <p>/me/** 只校验登录，数据按当前用户隔离，不走后台权限码。访客看的页面在 /public/@{handle}。</p>
 */
@RestController
@RequestMapping("/me/profile")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceProfileController {

    private final SpaceProfileService profileService;

    /**
     * 当前设置与访问统计；从没保存过返回默认值
     */
    @GetMapping
    public R<ProfileVO> get() {
        return R.success(profileService.get());
    }

    /**
     * 整份覆盖保存
     */
    @PutMapping
    public R<ProfileVO> save(@RequestBody @Valid ProfileSaveRequest req) {
        return R.success(profileService.save(req));
    }

    /**
     * 短名是否可用
     */
    @GetMapping("/handle")
    public R<HandleCheckVO> checkHandle(@RequestParam String handle) {
        return R.success(profileService.checkHandle(handle));
    }

    /**
     * 以访客身份预览还没保存的草稿
     */
    @PostMapping("/preview")
    public R<ProfilePublicVO> preview(@RequestBody @Valid ProfileSaveRequest req) {
        return R.success(profileService.preview(req));
    }
}
