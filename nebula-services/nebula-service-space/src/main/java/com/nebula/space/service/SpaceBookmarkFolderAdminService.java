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
     * 删除非空目录，一个事务里做完：
     * <ul>
     *     <li>moveUp：子目录上移一层，本目录的书签放进上级目录（上级是根时放进未分类）</li>
     *     <li>uncategorize：整棵子树的书签放进未分类，目录全删</li>
     *     <li>cascade：整棵子树的目录与书签一起删</li>
     * </ul>
     * 子目录上移时与上级里的目录重名会拒绝（409），什么都不动。
     *
     * @return 受影响的书签数（移走或删掉的）
     */
    int delete(Long id, String strategy);

    /**
     * 移动目录到新的父目录下
     */
    void move(Long id, FolderMoveRequest req);
}
