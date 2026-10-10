package com.nebula.manager.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.manager.dto.ApiKeyPageQuery;
import com.nebula.manager.dto.ApiKeySaveRequest;
import com.nebula.manager.dto.ApiKeyStatusUpdateRequest;
import com.nebula.manager.service.SysApiKeyService;
import com.nebula.manager.vo.ApiKeyCreatedVO;
import com.nebula.manager.vo.ApiKeyVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API Key（SK）管理控制器
 * 这里的接口一律只认登录态、不标 {@code @SaCheckApiKey}：key 不能用来给自己签发或修改 key。
 *
 * @author nebula
 */
@RestController
@RequestMapping("/api-key")
public class SysApiKeyController {

    private final SysApiKeyService apiKeyService;

    public SysApiKeyController(SysApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    /**
     * 分页查询
     */
    @SaCheckPermission("system:api-key:list")
    @GetMapping("/page")
    public R<PageResult<ApiKeyVO>> page(@ModelAttribute ApiKeyPageQuery query) {
        return R.success(apiKeyService.page(query));
    }

    /**
     * 创建 key。响应里带 key 明文，仅此一次，之后只能看到掩码。
     */
    @SaCheckPermission("system:api-key:add")
    @PostMapping
    public R<ApiKeyCreatedVO> create(@RequestBody ApiKeySaveRequest request) {
        return R.success("create success", apiKeyService.create(request));
    }

    /**
     * 更新名称/授权范围/过期时间/备注
     */
    @SaCheckPermission("system:api-key:edit")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody ApiKeySaveRequest request) {
        apiKeyService.update(id, request);
        return R.success("update success", null);
    }

    /**
     * 启用/禁用
     */
    @SaCheckPermission("system:api-key:edit")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable("id") Long id, @RequestBody ApiKeyStatusUpdateRequest request) {
        apiKeyService.updateStatus(id, request.getStatus());
        return R.success("status updated", null);
    }

    /**
     * 删除（立即失效）
     */
    @SaCheckPermission("system:api-key:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        apiKeyService.delete(id);
        return R.success("delete success", null);
    }
}
