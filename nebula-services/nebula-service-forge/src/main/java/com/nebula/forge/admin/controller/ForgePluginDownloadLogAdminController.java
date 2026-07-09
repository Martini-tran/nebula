package com.nebula.forge.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.forge.controller.AbstractAdminController;
import com.nebula.forge.dto.admin.ForgePluginDownloadLogPageQuery;
import com.nebula.forge.service.ForgePluginDownloadLogAdminService;
import com.nebula.forge.vo.admin.ForgePluginDownloadLogAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 插件下载日志控制器（管理员端，只读）
 *
 * @author nebula
 */
@RestController
@RequestMapping("/admin/plugin-download-logs")
@RequiredArgsConstructor
public class ForgePluginDownloadLogAdminController extends AbstractAdminController {

    private final ForgePluginDownloadLogAdminService downloadLogAdminService;

    /**
     * 分页查询下载日志
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("forge:download-log:list")
    public R<PageResult<ForgePluginDownloadLogAdminVO>> page(@ModelAttribute ForgePluginDownloadLogPageQuery query) {
        return R.success(downloadLogAdminService.page(query));
    }
}
