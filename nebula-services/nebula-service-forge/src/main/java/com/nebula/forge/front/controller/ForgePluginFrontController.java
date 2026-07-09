package com.nebula.forge.front.controller;

import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.forge.controller.AbstractFrontController;
import com.nebula.forge.dto.front.ForgePluginDownloadRequest;
import com.nebula.forge.dto.front.ForgePluginFrontPageQuery;
import com.nebula.forge.service.ForgePluginFrontService;
import com.nebula.forge.vo.front.ForgePluginDetailFrontVO;
import com.nebula.forge.vo.front.ForgePluginDownloadResultVO;
import com.nebula.forge.vo.front.ForgePluginFrontVO;
import com.nebula.forge.vo.front.ForgePluginPermissionFrontVO;
import com.nebula.forge.vo.front.ForgePluginVersionFrontVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 插件商城前台控制器（公开访问）
 * <p>仅暴露已上架插件与已发布、审核通过的版本，支持查询与下载。</p>
 *
 * @author nebula
 */
@RestController
@RequestMapping("/front/plugins")
@RequiredArgsConstructor
public class ForgePluginFrontController extends AbstractFrontController {

    private final ForgePluginFrontService pluginFrontService;

    /**
     * 分页查询上架插件
     */
    @GetMapping({"", "/page"})
    public R<PageResult<ForgePluginFrontVO>> page(@ModelAttribute ForgePluginFrontPageQuery query) {
        return R.success(pluginFrontService.page(query));
    }

    /**
     * 插件详情
     */
    @GetMapping("/{id}")
    public R<ForgePluginDetailFrontVO> detail(@PathVariable Long id) {
        return R.success(pluginFrontService.detail(id));
    }

    /**
     * 查询插件的已发布、审核通过版本列表
     */
    @GetMapping("/{id}/versions")
    public R<List<ForgePluginVersionFrontVO>> versions(@PathVariable Long id) {
        return R.success(pluginFrontService.listVersions(id));
    }

    /**
     * 查询指定版本的权限声明
     */
    @GetMapping("/{id}/versions/{versionId}/permissions")
    public R<List<ForgePluginPermissionFrontVO>> permissions(@PathVariable Long id,
                                                             @PathVariable Long versionId) {
        return R.success(pluginFrontService.listPermissions(id, versionId));
    }

    /**
     * 下载插件最新版本
     */
    @PostMapping("/{id}/download")
    public R<ForgePluginDownloadResultVO> downloadLatest(@PathVariable Long id,
                                                         @RequestBody(required = false) ForgePluginDownloadRequest req,
                                                         HttpServletRequest http) {
        return R.success(pluginFrontService.download(id, null, req, clientIp(http), http.getHeader("User-Agent")));
    }

    /**
     * 下载插件指定版本
     */
    @PostMapping("/{id}/versions/{versionId}/download")
    public R<ForgePluginDownloadResultVO> download(@PathVariable Long id,
                                                   @PathVariable Long versionId,
                                                   @RequestBody(required = false) ForgePluginDownloadRequest req,
                                                   HttpServletRequest http) {
        return R.success(pluginFrontService.download(id, versionId, req, clientIp(http), http.getHeader("User-Agent")));
    }

    private String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip.trim();
        }
        return req.getRemoteAddr();
    }
}
