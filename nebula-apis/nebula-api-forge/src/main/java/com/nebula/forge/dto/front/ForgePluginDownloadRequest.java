package com.nebula.forge.dto.front;

import lombok.Data;

/**
 * 插件下载请求（前台）
 * <p>客户端信息由请求方提供，IP、UA、用户ID 由服务端从上下文补全。</p>
 *
 * @author nebula
 */
@Data
public class ForgePluginDownloadRequest {

    /**
     * 客户端版本
     */
    private String clientVersion;

    /**
     * 客户端系统：windows/macos/linux 等
     */
    private String clientOs;
}
