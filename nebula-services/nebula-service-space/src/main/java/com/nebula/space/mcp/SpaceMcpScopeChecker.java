package com.nebula.space.mcp;

import cn.dev33.satoken.apikey.template.SaApiKeyUtil;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

/**
 * 按工具校验授权范围
 *
 * <p>MCP 只有一个地址，API Key 守卫在那里只能判断「key 至少有其中一个范围」；某个工具具体要哪个范围，
 * 在调用工具时由这里再判一次。</p>
 */
@Component
public class SpaceMcpScopeChecker {

    /**
     * 当前请求能否调用需要该授权范围的工具
     *
     * <p>凭 key（且未登录）的请求要求 key 具备该范围；登录态与其它 /me 接口一样只看登录，不另设限。
     * key 本身是否有效，网关和守卫已经验过。</p>
     */
    public boolean granted(String scope) {
        String apiKey = SaApiKeyUtil.readApiKeyValue(SaHolder.getRequest());
        return apiKey == null || StpUtil.isLogin() || SaApiKeyUtil.hasApiKeyScope(apiKey, scope);
    }
}
