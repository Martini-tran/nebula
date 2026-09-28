package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.service.SpaceSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 前台个人偏好控制器
 *
 * <p>/me/** 只校验登录，数据按当前用户隔离，不走后台权限码。</p>
 */
@RestController
@RequestMapping("/me/settings")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceSettingController {

    private final SpaceSettingService settingService;

    /**
     * 当前用户的偏好；从没保存过返回空对象
     */
    @GetMapping
    public R<Map<String, Object>> get() {
        return R.success(settingService.get());
    }

    /**
     * 整份覆盖保存
     */
    @PutMapping
    public R<Void> save(@RequestBody Map<String, Object> settings) {
        settingService.save(settings);
        return R.success();
    }
}
