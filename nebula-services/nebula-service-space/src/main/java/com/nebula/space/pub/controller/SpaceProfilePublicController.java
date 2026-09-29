package com.nebula.space.pub.controller;

import com.nebula.common.core.domain.R;
import com.nebula.space.service.SpaceProfileService;
import com.nebula.space.vo.me.ProfilePublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开主页接口（/public/**，不需要登录，网关白名单放行）
 *
 * <p>访客打开 /@{handle} 用。主页没开或不存在一律 404，不区分，免得被用来探测谁注册了空间。</p>
 */
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class SpaceProfilePublicController {

    private final SpaceProfileService profileService;

    /**
     * 访客看到的公开主页，只有白名单字段
     */
    @GetMapping("/@{handle}")
    public R<ProfilePublicVO> page(@PathVariable String handle) {
        return R.success(profileService.publicPage(handle));
    }

    /**
     * 访客展开了一个合集
     */
    @PostMapping("/@{handle}/collections/{collectionId}/viewed")
    public R<Void> viewed(@PathVariable String handle, @PathVariable String collectionId) {
        profileService.collectionViewed(handle, collectionId);
        return R.success();
    }

    /**
     * 访客把一个合集导入了自己的书签
     */
    @PostMapping("/@{handle}/collections/{collectionId}/imported")
    public R<Void> imported(@PathVariable String handle, @PathVariable String collectionId) {
        profileService.collectionImported(handle, collectionId);
        return R.success();
    }
}
