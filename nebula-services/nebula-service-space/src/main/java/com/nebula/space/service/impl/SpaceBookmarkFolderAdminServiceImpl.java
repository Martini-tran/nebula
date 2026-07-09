package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.admin.FolderCreateRequest;
import com.nebula.space.dto.admin.FolderMoveRequest;
import com.nebula.space.dto.admin.FolderUpdateRequest;
import com.nebula.space.entity.SpaceBookmark;
import com.nebula.space.entity.SpaceBookmarkFolder;
import com.nebula.space.mapper.SpaceBookmarkFolderMapper;
import com.nebula.space.mapper.SpaceBookmarkMapper;
import com.nebula.space.service.SpaceBookmarkFolderAdminService;
import com.nebula.space.vo.admin.FolderAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台书签目录管理服务实现
 */
@Service
@RequiredArgsConstructor
public class SpaceBookmarkFolderAdminServiceImpl implements SpaceBookmarkFolderAdminService {

    /** 根目录 ID 约定（来自 SQL 中 parent_id 默认值 0） */
    private static final long ROOT_PARENT_ID = 0L;

    /** 已知来源枚举，越界时回退为 manual */
    private static final Set<String> SOURCE_VALUES = Set.of("manual", "chrome", "import");
    private static final String SOURCE_DEFAULT = "manual";

    private final SpaceBookmarkFolderMapper folderMapper;
    private final SpaceBookmarkMapper bookmarkMapper;

    /**
     * 查询当前用户的目录树
     * 一次拉全量后在内存里组装，避免 N+1
     */
    @Override
    public List<FolderAdminVO> tree() {
        Long userId = requireUserId();
        List<SpaceBookmarkFolder> folders = folderMapper.selectList(
                new LambdaQueryWrapper<SpaceBookmarkFolder>()
                        .eq(SpaceBookmarkFolder::getUserId, userId)
                        .orderByAsc(SpaceBookmarkFolder::getLevel)
                        .orderByAsc(SpaceBookmarkFolder::getSortOrder)
                        .orderByAsc(SpaceBookmarkFolder::getId)
        );
        return buildTree(folders);
    }

    @Override
    public FolderAdminVO detail(Long id) {
        return toVO(requireFolder(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(FolderCreateRequest req) {
        Long userId = requireUserId();
        long parentId = req.getParentId() == null ? ROOT_PARENT_ID : req.getParentId();
        SpaceBookmarkFolder parent = parentId == ROOT_PARENT_ID ? null : requireFolderOwned(parentId, userId);

        checkNameUniqueInParent(userId, parentId, req.getName(), null);

        SpaceBookmarkFolder folder = new SpaceBookmarkFolder();
        folder.setUserId(userId);
        folder.setParentId(parentId);
        folder.setName(req.getName().trim());
        folder.setLevel(parent == null ? 1 : parent.getLevel() + 1);
        folder.setAncestors(buildAncestors(parent));
        folder.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        folder.setSource(normalizeSource(req.getSource()));
        folder.setSourceKey(req.getSourceKey());
        folder.setRemark(req.getRemark());
        folderMapper.insert(folder);
        return folder.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, FolderUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        SpaceBookmarkFolder folder = requireFolder(id);

        if (StringUtils.hasText(req.getName()) && !req.getName().equals(folder.getName())) {
            checkNameUniqueInParent(folder.getUserId(), folder.getParentId(), req.getName(), id);
            folder.setName(req.getName().trim());
        }
        if (req.getSortOrder() != null) {
            folder.setSortOrder(req.getSortOrder());
        }
        if (req.getRemark() != null) {
            folder.setRemark(req.getRemark());
        }
        folderMapper.updateById(folder);
    }

    /**
     * 删除目录
     * 仅当目录下没有子目录和书签时允许删除，避免悬空数据
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SpaceBookmarkFolder folder = requireFolder(id);
        long childFolderCount = folderMapper.selectCount(
                new LambdaQueryWrapper<SpaceBookmarkFolder>().eq(SpaceBookmarkFolder::getParentId, id)
        );
        if (childFolderCount > 0) {
            throw new BizException(HttpStatus.CONFLICT, "目录下存在子目录，无法删除");
        }
        long bookmarkCount = bookmarkMapper.selectCount(
                new LambdaQueryWrapper<SpaceBookmark>().eq(SpaceBookmark::getFolderId, id)
        );
        if (bookmarkCount > 0) {
            throw new BizException(HttpStatus.CONFLICT, "目录下存在书签，请先迁移或清空");
        }
        folderMapper.deleteById(folder.getId());
    }

    /**
     * 移动目录到新父目录下
     * 同步刷新自身及所有后代的 level / ancestors，避免树形结构错乱
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(Long id, FolderMoveRequest req) {
        SpaceBookmarkFolder folder = requireFolder(id);
        long targetParentId = req.getTargetParentId();
        if (targetParentId == folder.getId()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "不能将目录移动到自身");
        }
        SpaceBookmarkFolder targetParent = targetParentId == ROOT_PARENT_ID
                ? null
                : requireFolderOwned(targetParentId, folder.getUserId());

        // 校验：目标父目录不能是自身的后代
        if (targetParent != null && isDescendantOf(targetParent, folder.getId())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "不能将目录移动到其后代目录下");
        }

        checkNameUniqueInParent(folder.getUserId(), targetParentId, folder.getName(), folder.getId());

        // 更新自身
        int oldLevel = folder.getLevel();
        String oldAncestorsPrefix = folder.getAncestors();
        folder.setParentId(targetParentId);
        folder.setLevel(targetParent == null ? 1 : targetParent.getLevel() + 1);
        folder.setAncestors(buildAncestors(targetParent));
        if (req.getSortOrder() != null) {
            folder.setSortOrder(req.getSortOrder());
        }
        folderMapper.updateById(folder);

        // 更新所有后代的 level/ancestors（祖级前缀替换）
        refreshDescendants(folder, oldLevel, oldAncestorsPrefix);
    }

    // ----------------------------------------------------------------- 内部工具

    private void refreshDescendants(SpaceBookmarkFolder current, int oldLevel, String oldAncestorsPrefix) {
        // 后代的 ancestors 形如 "oldAncestorsPrefix,currentId,..."；用 Like 拉全量后在内存中重写
        // 注意：必须用 and(...) 包裹两个 ancestors 分支，否则 OR 会与 user_id 的 AND 错位
        List<SpaceBookmarkFolder> descendants = folderMapper.selectList(
                new LambdaQueryWrapper<SpaceBookmarkFolder>()
                        .eq(SpaceBookmarkFolder::getUserId, current.getUserId())
                        .and(w -> w
                                .likeRight(SpaceBookmarkFolder::getAncestors, oldAncestorsPrefix + "," + current.getId() + ",")
                                .or()
                                .eq(SpaceBookmarkFolder::getAncestors, oldAncestorsPrefix + "," + current.getId()))
        );
        if (descendants.isEmpty()) {
            return;
        }
        int levelDelta = current.getLevel() - oldLevel;
        String oldPrefix = oldAncestorsPrefix + "," + current.getId();
        String newPrefix = current.getAncestors() + "," + current.getId();
        for (SpaceBookmarkFolder d : descendants) {
            d.setLevel(d.getLevel() + levelDelta);
            d.setAncestors(d.getAncestors().replaceFirst("^" + java.util.regex.Pattern.quote(oldPrefix), newPrefix));
            folderMapper.updateById(d);
        }
    }

    private boolean isDescendantOf(SpaceBookmarkFolder candidate, Long ancestorId) {
        if (candidate == null || candidate.getAncestors() == null) {
            return false;
        }
        for (String s : candidate.getAncestors().split(",")) {
            if (s.trim().equals(String.valueOf(ancestorId))) {
                return true;
            }
        }
        return false;
    }

    private String buildAncestors(SpaceBookmarkFolder parent) {
        if (parent == null) {
            return String.valueOf(ROOT_PARENT_ID);
        }
        return parent.getAncestors() + "," + parent.getId();
    }

    private void checkNameUniqueInParent(Long userId, Long parentId, String name, Long excludeId) {
        if (!StringUtils.hasText(name)) {
            return;
        }
        LambdaQueryWrapper<SpaceBookmarkFolder> wrapper = new LambdaQueryWrapper<SpaceBookmarkFolder>()
                .eq(SpaceBookmarkFolder::getUserId, userId)
                .eq(SpaceBookmarkFolder::getParentId, parentId)
                .eq(SpaceBookmarkFolder::getName, name.trim());
        if (excludeId != null) {
            wrapper.ne(SpaceBookmarkFolder::getId, excludeId);
        }
        if (folderMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.CONFLICT, "同一父目录下已存在同名目录");
        }
    }

    private SpaceBookmarkFolder requireFolder(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "目录ID不能为空");
        }
        SpaceBookmarkFolder folder = folderMapper.selectById(id);
        if (folder == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "目录不存在");
        }
        Long currentUserId = requireUserId();
        if (!UserContext.hasRole("super_admin") && !folder.getUserId().equals(currentUserId)) {
            throw new BizException(HttpStatus.FORBIDDEN, "无权操作该目录");
        }
        return folder;
    }

    private SpaceBookmarkFolder requireFolderOwned(Long id, Long userId) {
        SpaceBookmarkFolder folder = folderMapper.selectById(id);
        if (folder == null || !folder.getUserId().equals(userId)) {
            throw new BizException(HttpStatus.NOT_FOUND, "父目录不存在或无权访问");
        }
        return folder;
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }

    private String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            return SOURCE_DEFAULT;
        }
        return SOURCE_VALUES.contains(source) ? source : SOURCE_DEFAULT;
    }

    private List<FolderAdminVO> buildTree(List<SpaceBookmarkFolder> folders) {
        Map<Long, FolderAdminVO> indexed = new HashMap<>(folders.size());
        for (SpaceBookmarkFolder f : folders) {
            indexed.put(f.getId(), toVO(f));
        }
        List<FolderAdminVO> roots = new ArrayList<>();
        for (SpaceBookmarkFolder f : folders) {
            FolderAdminVO vo = indexed.get(f.getId());
            if (f.getParentId() == null || f.getParentId() == ROOT_PARENT_ID) {
                roots.add(vo);
                continue;
            }
            FolderAdminVO parent = indexed.get(f.getParentId());
            if (parent == null) {
                roots.add(vo);
                continue;
            }
            if (parent.getChildren() == null) {
                parent.setChildren(new ArrayList<>());
            }
            parent.getChildren().add(vo);
        }
        Comparator<FolderAdminVO> byOrder = Comparator
                .comparing((FolderAdminVO v) -> v.getSortOrder() == null ? 0 : v.getSortOrder())
                .thenComparing(FolderAdminVO::getId);
        sortRecursively(roots, byOrder);
        return roots;
    }

    private void sortRecursively(List<FolderAdminVO> list, Comparator<FolderAdminVO> cmp) {
        if (list == null || list.isEmpty()) {
            return;
        }
        list.sort(cmp);
        for (FolderAdminVO v : list) {
            sortRecursively(v.getChildren(), cmp);
        }
    }

    private FolderAdminVO toVO(SpaceBookmarkFolder folder) {
        FolderAdminVO vo = new FolderAdminVO();
        vo.setId(folder.getId());
        vo.setUserId(folder.getUserId());
        vo.setParentId(folder.getParentId());
        vo.setAncestors(folder.getAncestors());
        vo.setName(folder.getName());
        vo.setLevel(folder.getLevel());
        vo.setSortOrder(folder.getSortOrder());
        vo.setSource(folder.getSource());
        vo.setSourceKey(folder.getSourceKey());
        vo.setRemark(folder.getRemark());
        vo.setCreateTime(folder.getCreateTime());
        vo.setUpdateTime(folder.getUpdateTime());
        return vo;
    }
}
