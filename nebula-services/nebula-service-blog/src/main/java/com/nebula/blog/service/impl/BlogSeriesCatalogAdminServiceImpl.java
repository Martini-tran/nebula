package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.dto.admin.SeriesCatalogCreateRequest;
import com.nebula.blog.dto.admin.SeriesCatalogPostBindRequest;
import com.nebula.blog.dto.admin.SeriesCatalogUpdateRequest;
import com.nebula.blog.entity.BlogPost;
import com.nebula.blog.entity.BlogSeries;
import com.nebula.blog.entity.BlogSeriesCatalog;
import com.nebula.blog.entity.BlogSeriesCatalogPost;
import com.nebula.blog.mapper.BlogPostMapper;
import com.nebula.blog.mapper.BlogSeriesCatalogMapper;
import com.nebula.blog.mapper.BlogSeriesCatalogPostMapper;
import com.nebula.blog.mapper.BlogSeriesMapper;
import com.nebula.blog.service.BlogSeriesCatalogAdminService;
import com.nebula.blog.vo.admin.SeriesCatalogAdminVO;
import com.nebula.blog.vo.admin.SeriesCatalogPostVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 博客系列目录管理服务实现（管理员端）
 *
 * <p>核心约束：
 * <ul>
 *   <li>node_type=2（链接）必须填写 link_url；非链接类型 link_url 强制清空</li>
 *   <li>移动节点时禁止环路（不能将节点挂到自己或其后代下）</li>
 *   <li>path 字段维护为 /id1/id2/.../idN/ 形式，level=父 level+1，根节点 level=0</li>
 *   <li>父节点的 children_count 在新增/移动/删除时维护</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogSeriesCatalogAdminServiceImpl implements BlogSeriesCatalogAdminService {

    private static final int NODE_TYPE_DIRECTORY = 0;
    private static final int NODE_TYPE_POST_GROUP = 1;
    private static final int NODE_TYPE_LINK = 2;
    private static final Set<Integer> NODE_TYPES = Set.of(NODE_TYPE_DIRECTORY, NODE_TYPE_POST_GROUP, NODE_TYPE_LINK);

    private static final String LINK_TARGET_BLANK = "_blank";
    private static final String LINK_TARGET_SELF = "_self";
    private static final Set<String> LINK_TARGETS = Set.of(LINK_TARGET_BLANK, LINK_TARGET_SELF);

    private static final int TITLE_MAX_LENGTH = 200;
    private static final int LINK_URL_MAX_LENGTH = 500;

    private final BlogSeriesMapper seriesMapper;
    private final BlogSeriesCatalogMapper catalogMapper;
    private final BlogSeriesCatalogPostMapper catalogPostMapper;
    private final BlogPostMapper postMapper;

    @Override
    public List<SeriesCatalogAdminVO> getCatalogTree(Long seriesId) {
        requireSeries(seriesId);
        List<BlogSeriesCatalog> all = catalogMapper.selectList(
                new LambdaQueryWrapper<BlogSeriesCatalog>()
                        .eq(BlogSeriesCatalog::getSeriesId, seriesId)
                        .orderByAsc(BlogSeriesCatalog::getSortOrder));
        Map<Long, List<BlogSeriesCatalog>> byParent = all.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? 0L : c.getParentId()));
        return buildTree(byParent, 0L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SeriesCatalogCreateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (req.getSeriesId() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "seriesId 不能为空");
        }
        requireSeries(req.getSeriesId());
        if (!StringUtils.hasText(req.getTitle())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "节点标题不能为空");
        }
        checkLength(req.getTitle(), TITLE_MAX_LENGTH, "title");

        Integer nodeType = req.getNodeType() == null ? NODE_TYPE_DIRECTORY : req.getNodeType();
        if (!NODE_TYPES.contains(nodeType)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "nodeType 非法");
        }
        String linkTarget = StringUtils.hasText(req.getLinkTarget()) ? req.getLinkTarget() : LINK_TARGET_BLANK;
        String linkUrl = req.getLinkUrl();
        if (nodeType == NODE_TYPE_LINK) {
            if (!StringUtils.hasText(linkUrl)) {
                throw new BizException(HttpStatus.BAD_REQUEST, "链接节点 linkUrl 不能为空");
            }
            checkLength(linkUrl, LINK_URL_MAX_LENGTH, "linkUrl");
            if (!LINK_TARGETS.contains(linkTarget)) {
                throw new BizException(HttpStatus.BAD_REQUEST, "linkTarget 非法");
            }
        } else {
            linkUrl = null;
        }

        BlogSeriesCatalog parent = null;
        if (req.getParentId() != null && req.getParentId() > 0) {
            parent = catalogMapper.selectById(req.getParentId());
            if (parent == null || !parent.getSeriesId().equals(req.getSeriesId())) {
                throw new BizException(HttpStatus.BAD_REQUEST, "父节点不存在或不属于该系列");
            }
        }

        BlogSeriesCatalog node = new BlogSeriesCatalog();
        node.setSeriesId(req.getSeriesId());
        node.setParentId(parent == null ? null : parent.getId());
        node.setTitle(req.getTitle());
        node.setNodeType(nodeType);
        node.setLinkUrl(linkUrl);
        node.setLinkTarget(nodeType == NODE_TYPE_LINK ? linkTarget : null);
        node.setLevel(parent == null ? 0 : parent.getLevel() + 1);
        node.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        node.setChildrenCount(0);
        // path 需要先 insert 拿到 id 后再回写
        catalogMapper.insert(node);
        node.setPath(buildPath(parent, node.getId()));
        catalogMapper.updateById(node);

        if (parent != null) {
            incrementChildrenCount(parent.getId(), 1);
        }
        return node.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SeriesCatalogUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        BlogSeriesCatalog node = requireCatalog(id);

        if (StringUtils.hasText(req.getTitle())) {
            checkLength(req.getTitle(), TITLE_MAX_LENGTH, "title");
            node.setTitle(req.getTitle());
        }
        if (req.getNodeType() != null) {
            if (!NODE_TYPES.contains(req.getNodeType())) {
                throw new BizException(HttpStatus.BAD_REQUEST, "nodeType 非法");
            }
            node.setNodeType(req.getNodeType());
        }
        // 链接字段的有效性以最终 nodeType 为准
        boolean isLinkNode = node.getNodeType() != null && node.getNodeType() == NODE_TYPE_LINK;
        if (isLinkNode) {
            String url = req.getLinkUrl() != null ? req.getLinkUrl() : node.getLinkUrl();
            if (!StringUtils.hasText(url)) {
                throw new BizException(HttpStatus.BAD_REQUEST, "链接节点 linkUrl 不能为空");
            }
            checkLength(url, LINK_URL_MAX_LENGTH, "linkUrl");
            node.setLinkUrl(url);

            String target = StringUtils.hasText(req.getLinkTarget()) ? req.getLinkTarget()
                    : (StringUtils.hasText(node.getLinkTarget()) ? node.getLinkTarget() : LINK_TARGET_BLANK);
            if (!LINK_TARGETS.contains(target)) {
                throw new BizException(HttpStatus.BAD_REQUEST, "linkTarget 非法");
            }
            node.setLinkTarget(target);
        } else {
            node.setLinkUrl(null);
            node.setLinkTarget(null);
        }
        if (req.getSortOrder() != null) {
            node.setSortOrder(req.getSortOrder());
        }

        // 处理父节点变更
        if (req.getParentId() != null) {
            Long oldParentId = node.getParentId();
            Long newParentId = req.getParentId() == 0L ? null : req.getParentId();

            if (!Objects.equals(oldParentId, newParentId)) {
                BlogSeriesCatalog newParent = null;
                if (newParentId != null) {
                    newParent = catalogMapper.selectById(newParentId);
                    if (newParent == null || !newParent.getSeriesId().equals(node.getSeriesId())) {
                        throw new BizException(HttpStatus.BAD_REQUEST, "目标父节点不存在或不属于该系列");
                    }
                    if (newParentId.equals(node.getId()) || isDescendant(node.getId(), newParent)) {
                        throw new BizException(HttpStatus.BAD_REQUEST, "不能移动到自身或其后代节点下");
                    }
                }
                String oldPath = node.getPath();
                int oldLevel = node.getLevel() == null ? 0 : node.getLevel();

                node.setParentId(newParentId);
                node.setLevel(newParent == null ? 0 : newParent.getLevel() + 1);
                node.setPath(buildPath(newParent, node.getId()));

                catalogMapper.updateById(node);

                // 同步迁移所有子孙的 path / level
                migrateDescendants(oldPath, node.getPath(), oldLevel, node.getLevel());

                // 维护父节点 children_count
                if (oldParentId != null) {
                    incrementChildrenCount(oldParentId, -1);
                }
                if (newParentId != null) {
                    incrementChildrenCount(newParentId, 1);
                }
                return;
            }
        }
        catalogMapper.updateById(node);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BlogSeriesCatalog node = requireCatalog(id);
        // 收集自身及全部后代
        List<Long> idsToDelete = collectSelfAndDescendants(node);

        catalogPostMapper.delete(new LambdaQueryWrapper<BlogSeriesCatalogPost>()
                .in(BlogSeriesCatalogPost::getCatalogId, idsToDelete));
        catalogMapper.delete(new LambdaQueryWrapper<BlogSeriesCatalog>()
                .in(BlogSeriesCatalog::getId, idsToDelete));

        if (node.getParentId() != null) {
            incrementChildrenCount(node.getParentId(), -1);
        }
    }

    @Override
    public List<SeriesCatalogPostVO> listPosts(Long catalogId) {
        requireCatalog(catalogId);
        List<BlogSeriesCatalogPost> rels = catalogPostMapper.selectList(
                new LambdaQueryWrapper<BlogSeriesCatalogPost>()
                        .eq(BlogSeriesCatalogPost::getCatalogId, catalogId)
                        .orderByAsc(BlogSeriesCatalogPost::getSortOrder)
                        .orderByAsc(BlogSeriesCatalogPost::getId));
        if (rels.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = rels.stream().map(BlogSeriesCatalogPost::getPostId).toList();
        Map<Long, BlogPost> postMap = postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(BlogPost::getId, p -> p));
        return rels.stream().map(rel -> {
            SeriesCatalogPostVO vo = new SeriesCatalogPostVO();
            vo.setId(rel.getId());
            vo.setCatalogId(rel.getCatalogId());
            vo.setPostId(rel.getPostId());
            vo.setSortOrder(rel.getSortOrder());
            vo.setIsPrimary(rel.getIsPrimary());
            vo.setCreateTime(rel.getCreateTime());
            BlogPost post = postMap.get(rel.getPostId());
            if (post != null) {
                vo.setPostTitle(post.getTitle());
                vo.setPostSlug(post.getSlug());
                vo.setPostStatus(post.getStatus());
            }
            return vo;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindPosts(Long catalogId, SeriesCatalogPostBindRequest req) {
        requireCatalog(catalogId);
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        List<Long> postIds = cleanIds(req.getPostIds());
        validatePostsExist(postIds);

        if (req.getPrimaryPostId() != null && !postIds.contains(req.getPrimaryPostId())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "primaryPostId 必须在 postIds 内");
        }

        catalogPostMapper.delete(new LambdaQueryWrapper<BlogSeriesCatalogPost>()
                .eq(BlogSeriesCatalogPost::getCatalogId, catalogId));

        int sort = 0;
        for (Long postId : postIds) {
            BlogSeriesCatalogPost rel = new BlogSeriesCatalogPost();
            rel.setCatalogId(catalogId);
            rel.setPostId(postId);
            rel.setSortOrder(sort++);
            rel.setIsPrimary(postId.equals(req.getPrimaryPostId()));
            catalogPostMapper.insert(rel);
        }
    }

    // -------------------- 辅助方法 --------------------

    private void requireSeries(Long seriesId) {
        if (seriesId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "seriesId 不能为空");
        }
        BlogSeries series = seriesMapper.selectById(seriesId);
        if (series == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "系列不存在");
        }
    }

    private BlogSeriesCatalog requireCatalog(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "目录ID不能为空");
        }
        BlogSeriesCatalog node = catalogMapper.selectById(id);
        if (node == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "目录节点不存在");
        }
        return node;
    }

    private void checkLength(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new BizException(HttpStatus.BAD_REQUEST, field + " 长度不能超过 " + max);
        }
    }

    private String buildPath(BlogSeriesCatalog parent, Long selfId) {
        if (parent == null || !StringUtils.hasText(parent.getPath())) {
            return "/" + selfId + "/";
        }
        return parent.getPath() + selfId + "/";
    }

    /**
     * 判断 candidate 是否是 ancestorId 的后代（含自身已在调用方排除）。
     */
    private boolean isDescendant(Long ancestorId, BlogSeriesCatalog candidate) {
        if (candidate == null || !StringUtils.hasText(candidate.getPath())) {
            return false;
        }
        return candidate.getPath().contains("/" + ancestorId + "/");
    }

    /**
     * 当节点 path 发生变化时，将其所有后代的 path 前缀替换、level 同步偏移。
     */
    private void migrateDescendants(String oldPath, String newPath, int oldLevel, int newLevel) {
        if (!StringUtils.hasText(oldPath) || !StringUtils.hasText(newPath) || oldPath.equals(newPath)) {
            return;
        }
        List<BlogSeriesCatalog> descendants = catalogMapper.selectList(
                new LambdaQueryWrapper<BlogSeriesCatalog>().likeRight(BlogSeriesCatalog::getPath, oldPath));
        int delta = newLevel - oldLevel;
        for (BlogSeriesCatalog d : descendants) {
            if (d.getPath() == null || d.getPath().equals(oldPath)) {
                continue;
            }
            d.setPath(newPath + d.getPath().substring(oldPath.length()));
            d.setLevel((d.getLevel() == null ? 0 : d.getLevel()) + delta);
            catalogMapper.updateById(d);
        }
    }

    private List<Long> collectSelfAndDescendants(BlogSeriesCatalog node) {
        List<Long> ids = new ArrayList<>();
        ids.add(node.getId());
        if (StringUtils.hasText(node.getPath())) {
            List<BlogSeriesCatalog> descendants = catalogMapper.selectList(
                    new LambdaQueryWrapper<BlogSeriesCatalog>()
                            .likeRight(BlogSeriesCatalog::getPath, node.getPath())
                            .ne(BlogSeriesCatalog::getId, node.getId()));
            descendants.forEach(d -> ids.add(d.getId()));
        }
        return ids;
    }

    private void incrementChildrenCount(Long parentId, int delta) {
        if (parentId == null || delta == 0) {
            return;
        }
        BlogSeriesCatalog parent = catalogMapper.selectById(parentId);
        if (parent == null) {
            return;
        }
        int current = parent.getChildrenCount() == null ? 0 : parent.getChildrenCount();
        int next = Math.max(0, current + delta);
        parent.setChildrenCount(next);
        catalogMapper.updateById(parent);
    }

    private List<Long> cleanIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().filter(id -> id != null && id > 0).distinct().toList();
    }

    private void validatePostsExist(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return;
        }
        if (postMapper.selectBatchIds(postIds).size() != postIds.size()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "存在不存在的文章");
        }
    }

    private List<SeriesCatalogAdminVO> buildTree(Map<Long, List<BlogSeriesCatalog>> byParent, long parentId) {
        List<BlogSeriesCatalog> children = byParent.getOrDefault(parentId, List.of()).stream()
                .sorted(Comparator.comparing(c -> c.getSortOrder() == null ? 0 : c.getSortOrder()))
                .toList();
        List<SeriesCatalogAdminVO> result = new ArrayList<>();
        for (BlogSeriesCatalog c : children) {
            SeriesCatalogAdminVO vo = toVO(c);
            vo.setChildren(buildTree(byParent, c.getId()));
            result.add(vo);
        }
        return result;
    }

    private SeriesCatalogAdminVO toVO(BlogSeriesCatalog c) {
        SeriesCatalogAdminVO vo = new SeriesCatalogAdminVO();
        vo.setId(c.getId());
        vo.setSeriesId(c.getSeriesId());
        vo.setParentId(c.getParentId());
        vo.setTitle(c.getTitle());
        vo.setNodeType(c.getNodeType());
        vo.setLinkUrl(c.getLinkUrl());
        vo.setLinkTarget(c.getLinkTarget());
        vo.setPath(c.getPath());
        vo.setLevel(c.getLevel());
        vo.setSortOrder(c.getSortOrder());
        vo.setChildrenCount(c.getChildrenCount());
        vo.setCreateTime(c.getCreateTime());
        vo.setUpdateTime(c.getUpdateTime());
        return vo;
    }
}
