package com.nebula.forge.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.forge.dto.admin.ForgePluginReviewAuditRequest;
import com.nebula.forge.dto.admin.ForgePluginReviewPageQuery;
import com.nebula.forge.dto.admin.ForgePluginReviewReplyRequest;
import com.nebula.forge.vo.admin.ForgePluginReviewAdminVO;

/**
 * 插件评价管理服务（管理员端）
 *
 * @author nebula
 */
public interface ForgePluginReviewAdminService {

    /**
     * 分页查询评价
     */
    PageResult<ForgePluginReviewAdminVO> page(ForgePluginReviewPageQuery query);

    /**
     * 评价详情
     */
    ForgePluginReviewAdminVO detail(Long id);

    /**
     * 审核评价（设置状态与审核备注）
     */
    void audit(Long id, ForgePluginReviewAuditRequest req);

    /**
     * 回复评价
     */
    void reply(Long id, ForgePluginReviewReplyRequest req);

    /**
     * 删除评价（物理删除）
     */
    void delete(Long id);
}
