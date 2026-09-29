package com.nebula.space.service;

import com.nebula.space.dto.admin.SpaceTagCreateRequest;
import com.nebula.space.dto.admin.SpaceTagUpdateRequest;
import com.nebula.space.vo.admin.SpaceTagAdminVO;

import java.util.List;

/**
 * 后台空间标签管理服务
 */
public interface SpaceTagAdminService {

    /**
     * 查询当前用户的标签列表
     */
    List<SpaceTagAdminVO> list();

    /**
     * 查询标签详情
     */
    SpaceTagAdminVO detail(Long id);

    /**
     * 创建标签（同名则复用，返回已存在标签ID）
     */
    Long create(SpaceTagCreateRequest req);

    /**
     * 更新标签
     */
    void update(Long id, SpaceTagUpdateRequest req);

    /**
     * 删除标签
     * 删除前会清理书签-标签关联
     */
    void delete(Long id);

    /**
     * 把标签 from 合并进 to：打了 from 的书签都改打 to（已经打了 to 的不重复），再删掉 from；一个事务里做完
     *
     * @return 新打上 to 的书签数
     */
    int merge(Long fromId, Long toId);
}
