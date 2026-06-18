package com.nebula.forge.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.forge.dto.front.ForgePluginDownloadRequest;
import com.nebula.forge.dto.front.ForgePluginFrontPageQuery;
import com.nebula.forge.vo.front.ForgePluginDetailFrontVO;
import com.nebula.forge.vo.front.ForgePluginDownloadResultVO;
import com.nebula.forge.vo.front.ForgePluginFrontVO;
import com.nebula.forge.vo.front.ForgePluginPermissionFrontVO;
import com.nebula.forge.vo.front.ForgePluginVersionFrontVO;

import java.util.List;

/**
 * 插件商城前台服务（只读 + 下载）
 * <p>仅暴露已上架插件与已发布、审核通过的版本。</p>
 *
 * @author nebula
 */
public interface ForgePluginFrontService {

    /**
     * 分页查询上架插件
     */
    PageResult<ForgePluginFrontVO> page(ForgePluginFrontPageQuery query);

    /**
     * 获取上架插件详情
     */
    ForgePluginDetailFrontVO detail(Long pluginId);

    /**
     * 查询插件的已发布、审核通过版本列表（按发布时间倒序）
     */
    List<ForgePluginVersionFrontVO> listVersions(Long pluginId);

    /**
     * 查询指定版本的权限声明列表
     */
    List<ForgePluginPermissionFrontVO> listPermissions(Long pluginId, Long versionId);

    /**
     * 下载插件版本。
     *
     * @param pluginId  插件ID
     * @param versionId 版本ID，为空时下载插件最新版本
     * @param req       客户端信息（版本、系统）
     * @param ip        客户端IP（由控制器从请求上下文提取）
     * @param userAgent 客户端 User-Agent
     * @return 下载结果（含解析后的下载地址与校验信息）
     */
    ForgePluginDownloadResultVO download(Long pluginId, Long versionId,
                                         ForgePluginDownloadRequest req, String ip, String userAgent);
}
