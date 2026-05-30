package com.nebula.space.service;

import com.nebula.space.dto.admin.FolderCreateRequest;
import com.nebula.space.dto.admin.FolderMoveRequest;
import com.nebula.space.dto.admin.FolderUpdateRequest;
import com.nebula.space.vo.admin.FolderAdminVO;

import java.util.List;

/**
 * 后台书签目录管理服务
 */
public interface SpaceBookmarkFolderAdminService {

    /**
     * 查询当前用户的目录树
     */
    List<FolderAdminVO> tree();

    /**
     * 查询目录详情
     */
    FolderAdminVO detail(Long id);

    /**
     * 创建目录
     */
    Long create(FolderCreateRequest req);

    /**
     * 更新目录
     */
    void update(Long id, FolderUpdateRequest req);

    /**
     * 删除目录
     * 删除前会校验目录下不存在子目录或书签，避免数据残留
     */
    void delete(Long id);

    /**
     * 移动目录到新的父目录下
     */
    void move(Long id, FolderMoveRequest req);
}
