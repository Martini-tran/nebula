package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.bookmark.AiCallLimiter;
import com.nebula.space.bookmark.BookmarkAiPrompts;
import com.nebula.space.bookmark.FolderPlanner;
import com.nebula.space.dto.admin.BookmarkAiSuggestRequest;
import com.nebula.space.dto.admin.FolderAiPlanRequest;
import com.nebula.space.entity.SpaceBookmark;
import com.nebula.space.entity.SpaceBookmarkFolder;
import com.nebula.space.entity.SpaceBookmarkTag;
import com.nebula.space.entity.SpaceTag;
import com.nebula.space.mapper.SpaceBookmarkFolderMapper;
import com.nebula.space.mapper.SpaceBookmarkMapper;
import com.nebula.space.mapper.SpaceBookmarkTagMapper;
import com.nebula.space.mapper.SpaceTagMapper;
import com.nebula.space.service.SpaceBookmarkAiService;
import com.nebula.space.vo.admin.BookmarkAiSuggestionVO;
import com.nebula.space.vo.admin.FolderAiPlanVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 书签 AI 整理服务实现
 *
 * <p>模型只看得到标题、网址、描述和目录 / 标签的名字；它的回复逐项校验后才返回：
 * 目录路径对不上现有目录的算新建，名称不合规的丢掉；标签只加不删；标题与原来一样的不算改动；
 * 描述只补给原本没有描述的书签。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpaceBookmarkAiServiceImpl implements SpaceBookmarkAiService {

    static final String AGENT_CODE = "space-bookmark-organize";
    static final int TIMEOUT_MS = 120_000;
    static final String UNCATEGORIZED = "未分类";

    /** 目录重排一次最多看这么多目录 */
    static final int PLAN_MAX_FOLDERS = 300;
    /** 目录重排一次最多执行这么多操作 */
    static final int PLAN_MAX_OPS = 40;
    /** 每个目录给 AI 看几条书签标题 */
    static final int PLAN_SAMPLE_TITLES = 4;

    static final int TAGS_PER_BOOKMARK = 3;
    static final int TAG_NAME_MAX = 20;
    static final int TITLE_MAX = 200;
    static final int DESCRIPTION_MAX = 200;
    /** 新建目录的路径最多几层 */
    static final int NEW_FOLDER_DEPTH_MAX = 4;
    /** 表示个人使用情况的标签：模型从网页内容判断不了，提示词里不让用，它仍会给，这里再挡一道 */
    static final Set<String> USAGE_TAGS = Set.of("常用", "待读", "稍后读", "收藏", "重要", "置顶");

    private static final int STATUS_NORMAL = 0;

    private final SpaceBookmarkMapper bookmarkMapper;
    private final SpaceBookmarkFolderMapper folderMapper;
    private final SpaceBookmarkTagMapper bookmarkTagMapper;
    private final SpaceTagMapper tagMapper;
    private final ObjectProvider<AiService> aiService;
    private final AiCallLimiter limiter;

    // ----------------------------------------------------------------- 书签建议

    @Override
    public List<BookmarkAiSuggestionVO> suggest(BookmarkAiSuggestRequest req) {
        Long userId = requireUserId();
        Set<String> actions = req.getActions() == null ? Set.of() : req.getActions().stream()
                .filter(Objects::nonNull)
                .map(a -> a.trim().toLowerCase(Locale.ROOT))
                .filter(BookmarkAiPrompts.ACTIONS::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (actions.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请至少选择一项整理内容");
        }
        List<Long> ids = req.getBookmarkIds() == null ? List.of()
                : req.getBookmarkIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, SpaceBookmark> owned = bookmarkMapper.selectList(new LambdaQueryWrapper<SpaceBookmark>()
                        .eq(SpaceBookmark::getUserId, userId)
                        .in(SpaceBookmark::getId, ids))
                .stream()
                .collect(Collectors.toMap(SpaceBookmark::getId, Function.identity()));
        List<SpaceBookmark> bookmarks = ids.stream().map(owned::get).filter(Objects::nonNull).toList();
        if (bookmarks.isEmpty()) {
            return List.of();
        }

        Map<Long, String> pathById = folderPaths(folderMapper.selectList(
                new LambdaQueryWrapper<SpaceBookmarkFolder>().eq(SpaceBookmarkFolder::getUserId, userId)));
        Map<String, Long> folderIdByPath = new HashMap<>();
        pathById.forEach((id, path) -> folderIdByPath.putIfAbsent(pathKey(path), id));

        List<SpaceTag> tags = tagMapper.selectList(new LambdaQueryWrapper<SpaceTag>()
                .eq(SpaceTag::getUserId, userId)
                .orderByAsc(SpaceTag::getSortOrder)
                .orderByAsc(SpaceTag::getId));
        Map<Long, SpaceTag> tagById = tags.stream().collect(Collectors.toMap(SpaceTag::getId, Function.identity()));
        Map<String, SpaceTag> tagByName = new HashMap<>();
        tags.forEach(t -> tagByName.putIfAbsent(t.getName().trim().toLowerCase(Locale.ROOT), t));

        Map<Long, Set<Long>> tagIdsByBookmark = new HashMap<>();
        bookmarkTagMapper.selectList(new LambdaQueryWrapper<SpaceBookmarkTag>()
                        .in(SpaceBookmarkTag::getBookmarkId, bookmarks.stream().map(SpaceBookmark::getId).toList()))
                .forEach(rel -> tagIdsByBookmark.computeIfAbsent(rel.getBookmarkId(), k -> new LinkedHashSet<>()).add(rel.getTagId()));

        List<BookmarkAiPrompts.Item> items = bookmarks.stream().map(b -> new BookmarkAiPrompts.Item(
                b.getTitle(),
                b.getUrl(),
                b.getDescription(),
                pathById.getOrDefault(b.getFolderId(), UNCATEGORIZED),
                tagIdsByBookmark.getOrDefault(b.getId(), Set.of()).stream()
                        .map(tagById::get).filter(Objects::nonNull).map(SpaceTag::getName).toList()
        )).toList();
        String prompt = BookmarkAiPrompts.suggest(
                pathById.values().stream().sorted().toList(),
                tags.stream().map(SpaceTag::getName).toList(),
                items,
                actions);
        JsonNode root = call(userId, prompt, 6000);
        JsonNode entries = root.get("items");
        if (entries == null || !entries.isArray()) {
            throw new BizException(HttpStatus.BAD_GATEWAY, "AI 返回的内容无法识别，请重试");
        }

        BookmarkAiSuggestionVO[] byIndex = new BookmarkAiSuggestionVO[bookmarks.size()];
        for (JsonNode entry : entries) {
            int index = entry.path("i").asInt(0) - 1;
            if (index < 0 || index >= bookmarks.size() || byIndex[index] != null) {
                continue;
            }
            SpaceBookmark bookmark = bookmarks.get(index);
            BookmarkAiSuggestionVO vo = new BookmarkAiSuggestionVO();
            vo.setBookmarkId(bookmark.getId());
            if (actions.contains(BookmarkAiPrompts.ACTION_FOLDER)) {
                vo.setFolder(folderTarget(BookmarkAiPrompts.text(entry, "folder"), bookmark.getFolderId(), pathById, folderIdByPath));
            }
            if (actions.contains(BookmarkAiPrompts.ACTION_TAGS)) {
                vo.setTags(tagTargets(entry.get("tags"), tagIdsByBookmark.getOrDefault(bookmark.getId(), Set.of()), tagByName));
            }
            if (actions.contains(BookmarkAiPrompts.ACTION_TITLE)) {
                vo.setTitle(changedTitle(BookmarkAiPrompts.text(entry, "title"), bookmark.getTitle()));
            }
            if (actions.contains(BookmarkAiPrompts.ACTION_DESCRIPTION)
                    && (bookmark.getDescription() == null || bookmark.getDescription().isBlank())) {
                vo.setDescription(clean(BookmarkAiPrompts.text(entry, "description"), DESCRIPTION_MAX));
            }
            if (vo.getFolder() != null || vo.getTags() != null || vo.getTitle() != null || vo.getDescription() != null) {
                byIndex[index] = vo;
            }
        }
        return Arrays.stream(byIndex).filter(Objects::nonNull).toList();
    }

    /**
     * AI 给的目录路径 → 目标目录；与当前目录相同、是「未分类」、名称不合规时返回 null
     */
    static BookmarkAiSuggestionVO.FolderTarget folderTarget(String raw, Long currentFolderId,
                                                           Map<Long, String> pathById, Map<String, Long> folderIdByPath) {
        List<String> segments = splitPath(raw);
        if (segments.isEmpty() || UNCATEGORIZED.equals(segments.get(0))) {
            return null;
        }
        String path = String.join(" / ", segments);
        Long existing = folderIdByPath.get(pathKey(path));
        BookmarkAiSuggestionVO.FolderTarget target = new BookmarkAiSuggestionVO.FolderTarget();
        if (existing != null) {
            if (existing.equals(currentFolderId)) {
                return null;
            }
            target.setId(existing);
            target.setPath(pathById.get(existing));
            return target;
        }
        if (segments.size() > NEW_FOLDER_DEPTH_MAX || segments.stream().anyMatch(s -> FolderPlanner.cleanName(s) == null)) {
            return null;
        }
        target.setPath(path);
        return target;
    }

    /**
     * AI 给的标签名 → 要加上的标签（去掉书签已有的），最多 3 个；一个都没有返回 null
     */
    static List<BookmarkAiSuggestionVO.TagTarget> tagTargets(JsonNode raw, Set<Long> current, Map<String, SpaceTag> tagByName) {
        if (raw == null || !raw.isArray()) {
            return null;
        }
        Map<String, BookmarkAiSuggestionVO.TagTarget> picked = new LinkedHashMap<>();
        for (JsonNode node : raw) {
            if (picked.size() >= TAGS_PER_BOOKMARK) {
                break;
            }
            String name = node.isTextual() ? clean(node.asText().replaceFirst("^#+", ""), TAG_NAME_MAX) : null;
            if (name == null || name.contains(",") || name.contains("，") || USAGE_TAGS.contains(name)) {
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            SpaceTag tag = tagByName.get(key);
            if (picked.containsKey(key) || (tag != null && current.contains(tag.getId()))) {
                continue;
            }
            BookmarkAiSuggestionVO.TagTarget target = new BookmarkAiSuggestionVO.TagTarget();
            if (tag != null) {
                target.setId(tag.getId());
                target.setName(tag.getName());
                target.setColor(tag.getColor());
            } else {
                target.setName(name);
            }
            picked.put(key, target);
        }
        return picked.isEmpty() ? null : new ArrayList<>(picked.values());
    }

    static String changedTitle(String raw, String current) {
        String title = clean(raw, TITLE_MAX);
        return title == null || title.equals(current == null ? null : current.trim()) ? null : title;
    }

    // ----------------------------------------------------------------- 目录重排

    @Override
    public FolderAiPlanVO plan(FolderAiPlanRequest req) {
        Long userId = requireUserId();
        List<SpaceBookmarkFolder> folders = folderMapper.selectList(
                new LambdaQueryWrapper<SpaceBookmarkFolder>().eq(SpaceBookmarkFolder::getUserId, userId));
        if (folders.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "还没有目录，先建几个目录或导入书签再来整理");
        }
        if (folders.size() > PLAN_MAX_FOLDERS) {
            throw new BizException(HttpStatus.BAD_REQUEST, "目录超过 " + PLAN_MAX_FOLDERS + " 个，AI 一次看不过来，可以先手动合并一部分再试");
        }

        Map<Long, Integer> counts = new HashMap<>();
        bookmarkMapper.selectMaps(new QueryWrapper<SpaceBookmark>()
                        .select("folder_id AS fid", "COUNT(*) AS cnt")
                        .eq("user_id", userId)
                        .groupBy("folder_id"))
                .forEach(row -> {
                    Object fid = row.get("fid");
                    Object cnt = row.get("cnt");
                    if (fid instanceof Number f && cnt instanceof Number c) {
                        counts.put(f.longValue(), c.intValue());
                    }
                });
        Map<Long, List<String>> titles = new HashMap<>();
        bookmarkMapper.selectList(new LambdaQueryWrapper<SpaceBookmark>()
                        .select(SpaceBookmark::getFolderId, SpaceBookmark::getTitle)
                        .eq(SpaceBookmark::getUserId, userId)
                        .eq(SpaceBookmark::getStatus, STATUS_NORMAL)
                        .ne(SpaceBookmark::getFolderId, 0L)
                        .orderByDesc(SpaceBookmark::getVisitCount)
                        .orderByDesc(SpaceBookmark::getId)
                        .last("limit 3000"))
                .forEach(b -> {
                    List<String> list = titles.computeIfAbsent(b.getFolderId(), k -> new ArrayList<>());
                    String title = clean(b.getTitle(), 30);
                    if (list.size() < PLAN_SAMPLE_TITLES && title != null) {
                        list.add(title);
                    }
                });

        FolderPlanner planner = new FolderPlanner(folders, counts, titles);
        JsonNode root = call(userId, BookmarkAiPrompts.plan(planner.describe(), req == null ? null : req.getHint()), 4000);

        FolderAiPlanVO vo = new FolderAiPlanVO();
        vo.setSummary(clean(BookmarkAiPrompts.text(root, "summary"), 300));
        int dropped = 0;
        JsonNode ops = root.get("ops");
        if (ops != null && ops.isArray()) {
            int index = 0;
            for (JsonNode op : ops) {
                if (index++ >= PLAN_MAX_OPS) {
                    dropped++;
                    continue;
                }
                FolderAiPlanVO.Op planned = planner.apply(new FolderPlanner.RawOp(
                        BookmarkAiPrompts.text(op, "op"),
                        BookmarkAiPrompts.text(op, "key"),
                        BookmarkAiPrompts.text(op, "folder"),
                        BookmarkAiPrompts.text(op, "parent"),
                        BookmarkAiPrompts.text(op, "into"),
                        BookmarkAiPrompts.text(op, "name"),
                        BookmarkAiPrompts.text(op, "reason")));
                if (planned == null) {
                    log.info("目录重排丢弃不合规的操作：{}", op);
                    dropped++;
                } else {
                    vo.getOps().add(planned);
                }
            }
        }
        vo.setDropped(dropped);
        return vo;
    }

    // ----------------------------------------------------------------- 调用模型

    private JsonNode call(Long userId, String prompt, int maxTokens) {
        AiService ai = aiService.getIfAvailable();
        if (ai == null) {
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, "AI 整理未启用：space 服务需要开启 nebula.ai");
        }
        if (!limiter.tryAcquire(userId)) {
            throw new BizException(HttpStatus.TOO_MANY_REQUESTS, "AI 整理用得太频繁了，请过一小时再试");
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", BookmarkAiPrompts.SYSTEM));
        messages.add(message("user", prompt));
        AiRequest request = new AiRequest(messages);
        request.setUserId(String.valueOf(userId));
        request.setAgentCode(AGENT_CODE);
        request.setTemperature(0.2);
        request.setMaxTokens(maxTokens);
        request.setTimeoutMs(TIMEOUT_MS);
        request.getOptions().put("response_format", Map.of("type", "json_object"));

        Map<String, Object> response;
        try {
            response = ai.chat(request);
        } catch (RuntimeException e) {
            log.warn("书签 AI 整理调用模型失败：{}", e.getMessage());
            String message = e.getMessage() != null && e.getMessage().contains("ApiKey")
                    ? "AI 整理还没配置模型密钥（环境变量 AI_API_KEY）"
                    : "AI 服务暂时不可用，请稍后再试";
            throw new BizException(HttpStatus.BAD_GATEWAY, message);
        }
        Object content = response == null ? null : response.get("content");
        JsonNode root = BookmarkAiPrompts.parse(content == null ? null : content.toString());
        if (root == null) {
            log.warn("书签 AI 整理回复无法解析：{}", content);
            throw new BizException(HttpStatus.BAD_GATEWAY, "AI 返回的内容无法识别，请重试");
        }
        return root;
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    // ----------------------------------------------------------------- 工具

    /**
     * 每个目录的完整路径（「前端 / 构建工具」）
     */
    static Map<Long, String> folderPaths(List<SpaceBookmarkFolder> folders) {
        Map<Long, SpaceBookmarkFolder> byId = folders.stream()
                .collect(Collectors.toMap(SpaceBookmarkFolder::getId, Function.identity(), (a, b) -> a));
        Map<Long, String> paths = new LinkedHashMap<>();
        folders.stream()
                .sorted(Comparator.comparing((SpaceBookmarkFolder f) -> f.getLevel() == null ? 0 : f.getLevel())
                        .thenComparing(f -> f.getSortOrder() == null ? 0 : f.getSortOrder())
                        .thenComparing(SpaceBookmarkFolder::getId))
                .forEach(folder -> {
                    List<String> names = new ArrayList<>();
                    Set<Long> seen = new HashSet<>();
                    for (SpaceBookmarkFolder cur = folder; cur != null && seen.add(cur.getId()); cur = byId.get(cur.getParentId())) {
                        names.add(0, cur.getName());
                    }
                    paths.put(folder.getId(), String.join(" / ", names));
                });
        return paths;
    }

    /** 按「/」拆路径，每段去空白；全角斜杠也认 */
    static List<String> splitPath(String raw) {
        if (raw == null) {
            return List.of();
        }
        return Arrays.stream(raw.split("[/／]"))
                .map(s -> s.trim().replaceAll("\\s+", " "))
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** 路径比较用的键：重新按「 / 」拼接并忽略大小写 */
    static String pathKey(String path) {
        return String.join(" / ", splitPath(path)).toLowerCase(Locale.ROOT);
    }

    /** 去首尾空白、压缩空白；空的返回 null，太长的截断 */
    static String clean(String value, int max) {
        if (value == null) {
            return null;
        }
        String text = value.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }
}
