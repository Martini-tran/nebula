package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.entity.SpaceBookmark;
import com.nebula.space.entity.SpaceBookmarkExportTask;
import com.nebula.space.entity.SpaceBookmarkFolder;
import com.nebula.space.entity.SpaceBookmarkImportTask;
import com.nebula.space.entity.SpaceBookmarkTag;
import com.nebula.space.entity.SpaceTag;
import com.nebula.space.mapper.SpaceBookmarkExportTaskMapper;
import com.nebula.space.mapper.SpaceBookmarkFolderMapper;
import com.nebula.space.mapper.SpaceBookmarkImportTaskMapper;
import com.nebula.space.mapper.SpaceBookmarkMapper;
import com.nebula.space.mapper.SpaceBookmarkTagMapper;
import com.nebula.space.mapper.SpaceTagMapper;
import com.nebula.space.service.SpaceBookmarkPorterService;
import com.nebula.space.vo.admin.BookmarkExportTaskAdminVO;
import com.nebula.space.vo.admin.BookmarkImportTaskAdminVO;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 书签导入/导出服务实现
 *
 * <p>Netscape Bookmark 格式（Chrome 导出）结构示意：
 * <pre>
 *   &lt;DT&gt;&lt;H3 ADD_DATE="..." LAST_MODIFIED="..."&gt;目录名&lt;/H3&gt;
 *   &lt;DL&gt;&lt;p&gt;
 *     &lt;DT&gt;&lt;A HREF="..." ADD_DATE="..." ICON="..."&gt;标题&lt;/A&gt;
 *     &lt;DT&gt;&lt;A HREF="..."&gt;...&lt;/A&gt;
 *   &lt;/DL&gt;&lt;p&gt;
 * </pre>
 * 没有严格的 DTD 校验，部分浏览器会把 closing 标签省略，所以这里用行级正则解析。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpaceBookmarkPorterServiceImpl implements SpaceBookmarkPorterService {

    private static final long ROOT_FOLDER_ID = 0L;
    private static final long MAX_IMPORT_FILE_SIZE = 20L * 1024 * 1024;
    private static final int MAX_FOLDER_NAME_LENGTH = 100;
    private static final int MAX_FAVICON_URL_LENGTH = 1000;
    private static final int MAX_DOMAIN_LENGTH = 255;

    /** 状态：0待处理 1处理中 2成功 3失败 */
    private static final int TASK_PENDING = 0;
    private static final int TASK_PROCESSING = 1;
    private static final int TASK_SUCCESS = 2;
    private static final int TASK_FAIL = 3;

    private static final String SCOPE_ALL = "all";
    private static final String SCOPE_FOLDER = "folder";
    private static final String SCOPE_TAG = "tag";
    private static final Set<String> SCOPE_VALUES = Set.of(SCOPE_ALL, SCOPE_FOLDER, SCOPE_TAG);
    private static final String EXPORT_TYPE_CHROME_HTML = "chrome_html";
    private static final String EXPORT_TYPE_JSON = "json";

    private static final Pattern H3_PATTERN = Pattern.compile(
            "<H3\\b([^>]*)>(.*?)</H3>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern A_PATTERN = Pattern.compile(
            "<A\\b([^>]*)>(.*?)</A>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** 匹配 ATTR="value"，value 内允许含 &amp; 等转义 */
    private static final Pattern ATTR_PATTERN = Pattern.compile(
            "([A-Za-z_][\\w-]*)\\s*=\\s*\"([^\"]*)\"");

    private static final DateTimeFormatter EXPORT_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int STATUS_NORMAL = 0;
    private static final int STATUS_ARCHIVED = 1;
    /**
     * 每导入多少条更新一次任务上的计数（前端据此显示进度）
     */
    static final int PROGRESS_EVERY = 50;
    /**
     * 最多记下多少条失败原因
     */
    static final int MAX_FAILURES = 100;
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final AtomicInteger THREAD_SEQ = new AtomicInteger();
    /**
     * 后台导入用的线程；两个足够（一个人导入一次通常几秒到几十秒）
     */
    private final ExecutorService importPool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "bookmark-import-" + THREAD_SEQ.incrementAndGet());
        t.setDaemon(true);
        return t;
    });
    private Executor importExecutor = importPool;

    private final SpaceBookmarkMapper bookmarkMapper;
    private final SpaceBookmarkFolderMapper folderMapper;
    private final SpaceBookmarkTagMapper bookmarkTagMapper;
    private final SpaceBookmarkImportTaskMapper importTaskMapper;
    private final SpaceBookmarkExportTaskMapper exportTaskMapper;
    private final SpaceTagMapper tagMapper;

    // ================================================================== 导入

    @Override
    public BookmarkImportTaskAdminVO importChromeHtml(MultipartFile file) {
        Long userId = requireUserId();
        validateImportFile(file);

        String html;
        try {
            html = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read uploaded bookmark file", e);
            throw new BizException(HttpStatus.BAD_REQUEST, "读取上传文件失败");
        }
        String[] tokens = tokenize(html);
        int total = 0;
        for (String token : tokens) {
            if (token.trim().toUpperCase(Locale.ROOT).startsWith("<A")) {
                total++;
            }
        }
        if (total == 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "文件里没有找到书签，请确认是浏览器「导出书签」得到的 HTML");
        }

        // 先建任务（处理中，带总条数），马上返回；前端轮询任务看进度
        SpaceBookmarkImportTask task = new SpaceBookmarkImportTask();
        task.setUserId(userId);
        task.setSource("chrome");
        task.setStatus(TASK_PROCESSING);
        task.setTotalCount(total);
        task.setSuccessCount(0);
        task.setDuplicateCount(0);
        task.setFailCount(0);
        importTaskMapper.insert(task);

        importExecutor.execute(() -> runImport(task, userId, tokens));
        return toImportVO(task);
    }

    /**
     * 后台逐条入库。不开大事务：每条书签单独提交，进度对轮询可见；中途出错时已导入的保留
     */
    void runImport(SpaceBookmarkImportTask task, Long userId, String[] tokens) {
        // 审计字段（create_by）从用户上下文取，后台线程要自己带上
        UserContext.set(userId);
        ImportStats stats = new ImportStats();
        try {
            // 当前用户已有的所有 url_hash，用于跨次导入去重
            Set<String> existingHashes = new HashSet<>(
                    bookmarkMapper.selectList(
                                    new LambdaQueryWrapper<SpaceBookmark>()
                                            .select(SpaceBookmark::getUrlHash)
                                            .eq(SpaceBookmark::getUserId, userId)
                            ).stream()
                            .map(SpaceBookmark::getUrlHash)
                            .filter(Objects::nonNull)
                            .toList()
            );
            // 已有顶层目录的"父=ROOT + name"索引；同名复用，避免每次导入都新建一棵树
            Map<String, Long> existingTopFolders = new HashMap<>();
            for (SpaceBookmarkFolder f : folderMapper.selectList(
                    new LambdaQueryWrapper<SpaceBookmarkFolder>()
                            .eq(SpaceBookmarkFolder::getUserId, userId)
                            .eq(SpaceBookmarkFolder::getParentId, ROOT_FOLDER_ID)
            )) {
                existingTopFolders.put(f.getName(), f.getId());
            }

            parseAndPersist(tokens, userId, existingHashes, existingTopFolders, stats, () -> saveProgress(task, stats));
            task.setStatus(TASK_SUCCESS);
        } catch (Exception e) {
            log.error("Import bookmark html failed, taskId={}", task.getId(), e);
            task.setStatus(TASK_FAIL);
            task.setErrorMsg(truncate("导入到第 " + stats.total + " 条时出错，前面的已导入：" + e.getMessage(), 1900));
        } finally {
            try {
                saveProgress(task, stats);
            } catch (Exception e) {
                log.error("Save bookmark import task failed, taskId={}", task.getId(), e);
            } finally {
                UserContext.clear();
            }
        }
    }

    private void saveProgress(SpaceBookmarkImportTask task, ImportStats stats) {
        task.setSuccessCount(stats.success);
        task.setDuplicateCount(stats.duplicate);
        task.setFailCount(stats.fail);
        task.setFailDetail(stats.failures.isEmpty() ? null : writeJson(stats.failures));
        importTaskMapper.updateById(task);
    }

    /**
     * 测试里换成同步执行
     */
    void useExecutor(Executor executor) {
        this.importExecutor = executor;
    }

    @PreDestroy
    void shutdown() {
        importPool.shutdownNow();
    }

    private void validateImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "上传文件不能为空");
        }
        if (file.getSize() > MAX_IMPORT_FILE_SIZE) {
            throw new BizException(HttpStatus.BAD_REQUEST, "文件大小不能超过 20MB");
        }
        String filename = file.getOriginalFilename();
        if (filename != null && !filename.toLowerCase(Locale.ROOT).endsWith(".html")
                && !filename.toLowerCase(Locale.ROOT).endsWith(".htm")) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请上传 Chrome 导出的 .html 书签文件");
        }
    }

    /**
     * 解析 Netscape HTML 并写库
     *
     * <p>策略：按行扫描，遇到 &lt;H3&gt; 入栈作为当前目录；遇到 &lt;/DL&gt; 出栈；
     * 遇到 &lt;A&gt; 创建书签到当前栈顶目录。
     */
    /**
     * 使用行扫描，但 &lt;DL&gt; 前后可能没有换行，统一规范化为按标签切分
     */
    static String[] tokenize(String html) {
        return html.split("(?i)(?=<DT>|<DL>|</DL>|<H3|<A\\b|<HR>|<TITLE>)");
    }

    private void parseAndPersist(String[] tokens,
                                  Long userId,
                                  Set<String> existingHashes,
                                  Map<String, Long> existingTopFolders,
                                  ImportStats stats,
                                  Runnable onProgress) {
        Deque<Long> folderStack = new ArrayDeque<>();
        folderStack.push(ROOT_FOLDER_ID);

        // 同一栈层内的"父+name → folderId"缓存，便于在二次扫描时复用同名目录
        Map<String, Long> sessionFolderCache = new HashMap<>();

        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) continue;

            String upper = trimmed.toUpperCase(Locale.ROOT);
            if (upper.startsWith("</DL>")) {
                if (folderStack.size() > 1) {
                    folderStack.pop();
                }
                continue;
            }
            if (upper.startsWith("<H3")) {
                Matcher m = H3_PATTERN.matcher(trimmed);
                if (m.find()) {
                    String name = truncate(unescapeHtml(m.group(2)).trim(), MAX_FOLDER_NAME_LENGTH);
                    if (!name.isEmpty()) {
                        Long parentId = folderStack.peek();
                        Long folderId = ensureFolder(userId, parentId, name,
                                existingTopFolders, sessionFolderCache);
                        folderStack.push(folderId);
                    } else {
                        // 名称为空，仍然入栈一个"未知目录"，pop 时与 DL 对齐
                        folderStack.push(folderStack.peek());
                    }
                }
                continue;
            }
            if (upper.startsWith("<A")) {
                stats.total++;
                importAnchor(trimmed, userId, folderStack.peek(), existingHashes, stats);
                if (stats.total % PROGRESS_EVERY == 0) {
                    onProgress.run();
                }
            }
        }
    }

    private void importAnchor(String token, Long userId, Long folderId, Set<String> existingHashes, ImportStats stats) {
        Matcher m = A_PATTERN.matcher(token);
        if (!m.find()) {
            stats.fail(null, null, "这一行不是完整的书签（缺少 </A>）");
            return;
        }
        Map<String, String> attrs = parseAttrs(m.group(1));
        String href = attrs.get("HREF");
        String title = unescapeHtml(m.group(2)).trim();
        if (!StringUtils.hasText(href)) {
            stats.fail(title, null, "没有网址");
            return;
        }
        String domain = extractDomain(href);
        if (domain != null && domain.length() > MAX_DOMAIN_LENGTH) {
            stats.fail(title, href, "域名超过 " + MAX_DOMAIN_LENGTH + " 个字符");
            return;
        }
        try {
            importOneBookmark(userId, folderId, href, title, attrs, existingHashes, stats);
        } catch (Exception e) {
            log.warn("Skip invalid bookmark href={}, msg={}", href, e.getMessage());
            stats.fail(title, href, "保存失败");
        }
    }

    private Long ensureFolder(Long userId,
                              Long parentId,
                              String name,
                              Map<String, Long> existingTopFolders,
                              Map<String, Long> sessionFolderCache) {
        String cacheKey = parentId + "|" + name;
        Long cached = sessionFolderCache.get(cacheKey);
        if (cached != null) return cached;

        // 顶层目录优先复用同名
        if (parentId.equals(ROOT_FOLDER_ID)) {
            Long existing = existingTopFolders.get(name);
            if (existing != null) {
                sessionFolderCache.put(cacheKey, existing);
                return existing;
            }
        } else {
            // 非顶层：若同父 + 同名已存在，复用
            SpaceBookmarkFolder existed = folderMapper.selectOne(
                    new LambdaQueryWrapper<SpaceBookmarkFolder>()
                            .eq(SpaceBookmarkFolder::getUserId, userId)
                            .eq(SpaceBookmarkFolder::getParentId, parentId)
                            .eq(SpaceBookmarkFolder::getName, name)
                            .last("limit 1")
            );
            if (existed != null) {
                sessionFolderCache.put(cacheKey, existed.getId());
                return existed.getId();
            }
        }

        // 创建新目录
        SpaceBookmarkFolder folder = new SpaceBookmarkFolder();
        folder.setUserId(userId);
        folder.setParentId(parentId);
        folder.setName(name);
        folder.setSource("chrome");

        if (parentId.equals(ROOT_FOLDER_ID)) {
            folder.setLevel(1);
            folder.setAncestors(String.valueOf(ROOT_FOLDER_ID));
        } else {
            SpaceBookmarkFolder parent = folderMapper.selectById(parentId);
            if (parent == null) {
                folder.setLevel(1);
                folder.setAncestors(String.valueOf(ROOT_FOLDER_ID));
            } else {
                folder.setLevel(parent.getLevel() == null ? 2 : parent.getLevel() + 1);
                folder.setAncestors(parent.getAncestors() + "," + parent.getId());
            }
        }
        folder.setSortOrder(0);
        folderMapper.insert(folder);
        sessionFolderCache.put(cacheKey, folder.getId());
        if (parentId.equals(ROOT_FOLDER_ID)) {
            existingTopFolders.put(name, folder.getId());
        }
        return folder.getId();
    }

    private void importOneBookmark(Long userId,
                                    Long folderId,
                                    String href,
                                    String title,
                                    Map<String, String> attrs,
                                    Set<String> existingHashes,
                                    ImportStats stats) {
        String normalizedUrl = normalizeUrl(href);
        String urlHash = sha256(normalizedUrl);
        if (existingHashes.contains(urlHash)) {
            stats.duplicate++;
            return;
        }

        SpaceBookmark bookmark = new SpaceBookmark();
        bookmark.setUserId(userId);
        bookmark.setFolderId(folderId);
        bookmark.setTitle(StringUtils.hasText(title) ? truncate(title, 500) : truncate(href, 500));
        bookmark.setUrl(href);
        bookmark.setNormalizedUrl(normalizedUrl);
        bookmark.setUrlHash(urlHash);
        bookmark.setDomain(extractDomain(href));
        bookmark.setFaviconUrl(safeFaviconUrl(attrs.get("ICON")));
        bookmark.setSource("chrome");
        bookmark.setStatus(0);
        bookmark.setVisitCount(0);
        bookmark.setSortOrder(0);
        bookmarkMapper.insert(bookmark);
        existingHashes.add(urlHash);
        stats.success++;
    }

    // ================================================================== 导出

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookmarkExportTaskAdminVO exportChromeHtml(String scopeType, Long scopeId, boolean includeArchived, OutputStream out) {
        return export(EXPORT_TYPE_CHROME_HTML, scopeType, scopeId, includeArchived,
                (userId, bookmarks, folders, scope) -> writeNetscapeHtml(out, bookmarks, folders, scope));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookmarkExportTaskAdminVO exportJson(String scopeType, Long scopeId, boolean includeArchived, OutputStream out) {
        return export(EXPORT_TYPE_JSON, scopeType, scopeId, includeArchived,
                (userId, bookmarks, folders, scope) -> writeBookmarksJson(out, userId, bookmarks, folders, scope, scopeId, includeArchived));
    }

    /**
     * 把取到的书签写成某种格式
     */
    @FunctionalInterface
    private interface ExportWriter {
        void write(Long userId, List<SpaceBookmark> bookmarks, List<SpaceBookmarkFolder> folders, String scope) throws IOException;
    }

    private BookmarkExportTaskAdminVO export(String exportType, String scopeType, Long scopeId, boolean includeArchived,
                                             ExportWriter writer) {
        Long userId = requireUserId();
        String normalizedScope = StringUtils.hasText(scopeType) ? scopeType : SCOPE_ALL;
        if (!SCOPE_VALUES.contains(normalizedScope)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "无效的导出范围");
        }
        if ((SCOPE_FOLDER.equals(normalizedScope) || SCOPE_TAG.equals(normalizedScope)) && scopeId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "导出范围 ID 不能为空");
        }

        SpaceBookmarkExportTask task = new SpaceBookmarkExportTask();
        task.setUserId(userId);
        task.setExportType(exportType);
        task.setScopeType(normalizedScope);
        task.setScopeId(scopeId);
        task.setStatus(TASK_PROCESSING);
        task.setTotalCount(0);
        exportTaskMapper.insert(task);

        try {
            List<SpaceBookmark> bookmarks = loadExportBookmarks(userId, normalizedScope, scopeId, includeArchived);
            List<SpaceBookmarkFolder> folders = folderMapper.selectList(
                    new LambdaQueryWrapper<SpaceBookmarkFolder>().eq(SpaceBookmarkFolder::getUserId, userId)
            );
            writer.write(userId, bookmarks, folders, normalizedScope);
            task.setStatus(TASK_SUCCESS);
            task.setTotalCount(bookmarks.size());
        } catch (BizException e) {
            task.setStatus(TASK_FAIL);
            task.setErrorMsg(truncate(e.getMessage(), 1900));
            exportTaskMapper.updateById(task);
            throw e;
        } catch (Exception e) {
            log.error("Export bookmarks failed", e);
            task.setStatus(TASK_FAIL);
            task.setErrorMsg(truncate("导出失败: " + e.getMessage(), 1900));
            exportTaskMapper.updateById(task);
            throw new BizException(HttpStatus.INTERNAL_SERVER_ERROR, "导出失败，请稍后重试");
        }
        exportTaskMapper.updateById(task);
        return toExportVO(task);
    }

    private List<SpaceBookmark> loadExportBookmarks(Long userId, String scope, Long scopeId, boolean includeArchived) {
        LambdaQueryWrapper<SpaceBookmark> wrapper = new LambdaQueryWrapper<SpaceBookmark>()
                .eq(SpaceBookmark::getUserId, userId)
                .in(SpaceBookmark::getStatus, includeArchived ? List.of(STATUS_NORMAL, STATUS_ARCHIVED) : List.of(STATUS_NORMAL))
                .orderByAsc(SpaceBookmark::getFolderId)
                .orderByAsc(SpaceBookmark::getSortOrder)
                .orderByAsc(SpaceBookmark::getId);
        if (SCOPE_FOLDER.equals(scope)) {
            wrapper.eq(SpaceBookmark::getFolderId, scopeId);
        } else if (SCOPE_TAG.equals(scope)) {
            // 标签范围：先取 bookmark_id 再查
            List<Long> ids = bookmarkTagMapper.selectList(
                            new LambdaQueryWrapper<SpaceBookmarkTag>().eq(SpaceBookmarkTag::getTagId, scopeId)
                    ).stream()
                    .map(SpaceBookmarkTag::getBookmarkId)
                    .distinct()
                    .toList();
            if (ids.isEmpty()) return Collections.emptyList();
            wrapper.in(SpaceBookmark::getId, ids);
        }
        return bookmarkMapper.selectList(wrapper);
    }

    /**
     * 输出 Netscape Bookmark 格式
     *
     * <p>结构：固定头 + 单层"导出根" &lt;DL&gt;，其下按目录分组放书签；
     * 多级目录使用嵌套 &lt;DL&gt; 表达。
     */
    private void writeNetscapeHtml(OutputStream out,
                                   List<SpaceBookmark> bookmarks,
                                   List<SpaceBookmarkFolder> folders,
                                   String scope) throws IOException {
        // 构建：folderId → 子目录列表、folderId → 书签列表
        Map<Long, List<SpaceBookmarkFolder>> children = new HashMap<>();
        for (SpaceBookmarkFolder f : folders) {
            children.computeIfAbsent(f.getParentId(), k -> new ArrayList<>()).add(f);
        }
        for (List<SpaceBookmarkFolder> list : children.values()) {
            list.sort(Comparator
                    .comparing((SpaceBookmarkFolder f) -> f.getSortOrder() == null ? 0 : f.getSortOrder())
                    .thenComparing(SpaceBookmarkFolder::getId));
        }
        Map<Long, List<SpaceBookmark>> bookmarksByFolder = new HashMap<>();
        for (SpaceBookmark b : bookmarks) {
            Long fid = b.getFolderId() == null ? ROOT_FOLDER_ID : b.getFolderId();
            bookmarksByFolder.computeIfAbsent(fid, k -> new ArrayList<>()).add(b);
        }

        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        // Chrome 标准 Netscape Bookmark 文件头
        w.write("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n");
        w.write("<!-- This is an automatically generated file.\n");
        w.write("     It will be read and overwritten.\n");
        w.write("     DO NOT EDIT! -->\n");
        w.write("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n");
        w.write("<TITLE>Bookmarks</TITLE>\n");
        w.write("<H1>Bookmarks</H1>\n");
        w.write("<DL><p>\n");

        // 顶层：渲染所有 root 子目录及其后代
        List<SpaceBookmarkFolder> rootFolders = children.getOrDefault(ROOT_FOLDER_ID, Collections.emptyList());
        for (SpaceBookmarkFolder root : rootFolders) {
            writeFolder(w, root, children, bookmarksByFolder, 1);
        }
        // 未分类（folder_id = 0）的书签，直接挂在根 DL 下
        if (SCOPE_ALL.equals(scope) || SCOPE_FOLDER.equals(scope)) {
            List<SpaceBookmark> uncategorized = bookmarksByFolder.getOrDefault(ROOT_FOLDER_ID, Collections.emptyList());
            for (SpaceBookmark b : uncategorized) {
                writeBookmark(w, b, 1);
            }
        }

        w.write("</DL><p>\n");
        w.flush();
    }

    /**
     * JSON 备份：全部目录（带路径，能还原目录树）、标签，和范围内的书签（含标签名、描述、备注、状态）
     */
    private void writeBookmarksJson(OutputStream out,
                                    Long userId,
                                    List<SpaceBookmark> bookmarks,
                                    List<SpaceBookmarkFolder> folders,
                                    String scope,
                                    Long scopeId,
                                    boolean includeArchived) throws IOException {
        Map<Long, SpaceBookmarkFolder> folderById = new HashMap<>();
        folders.forEach(f -> folderById.put(f.getId(), f));
        List<SpaceTag> tags = tagMapper.selectList(
                new LambdaQueryWrapper<SpaceTag>()
                        .eq(SpaceTag::getUserId, userId)
                        .orderByAsc(SpaceTag::getSortOrder)
                        .orderByAsc(SpaceTag::getId)
        );
        Map<Long, String> tagName = new HashMap<>();
        tags.forEach(t -> tagName.put(t.getId(), t.getName()));
        // 按标签取关联（标签数量少），比按几千个书签 ID 去 IN 省事
        Map<Long, List<String>> tagsOf = new HashMap<>();
        if (!tagName.isEmpty()) {
            for (SpaceBookmarkTag rel : bookmarkTagMapper.selectList(
                    new LambdaQueryWrapper<SpaceBookmarkTag>().in(SpaceBookmarkTag::getTagId, tagName.keySet()))) {
                String name = tagName.get(rel.getTagId());
                if (name != null) {
                    tagsOf.computeIfAbsent(rel.getBookmarkId(), k -> new ArrayList<>()).add(name);
                }
            }
        }

        List<Map<String, Object>> folderRows = new ArrayList<>();
        for (SpaceBookmarkFolder f : folders) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(f.getId()));
            row.put("parentId", String.valueOf(f.getParentId()));
            row.put("name", f.getName());
            row.put("path", folderPath(f.getId(), folderById));
            row.put("sortOrder", f.getSortOrder());
            folderRows.add(row);
        }
        List<Map<String, Object>> tagRows = new ArrayList<>();
        for (SpaceTag t : tags) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(t.getId()));
            row.put("name", t.getName());
            row.put("color", t.getColor());
            tagRows.add(row);
        }
        List<Map<String, Object>> bookmarkRows = new ArrayList<>();
        for (SpaceBookmark b : bookmarks) {
            Long folderId = b.getFolderId() == null ? ROOT_FOLDER_ID : b.getFolderId();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(b.getId()));
            row.put("folderId", String.valueOf(folderId));
            row.put("folderPath", folderPath(folderId, folderById));
            row.put("title", b.getTitle());
            row.put("url", b.getUrl());
            row.put("description", b.getDescription());
            row.put("remark", b.getRemark());
            row.put("status", Objects.equals(b.getStatus(), STATUS_ARCHIVED) ? "archived" : "normal");
            row.put("tags", tagsOf.getOrDefault(b.getId(), List.of()));
            row.put("faviconUrl", b.getFaviconUrl());
            row.put("visitCount", b.getVisitCount());
            row.put("lastVisitTime", format(b.getLastVisitTime()));
            row.put("createTime", format(b.getCreateTime()));
            row.put("updateTime", format(b.getUpdateTime()));
            bookmarkRows.add(row);
        }

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("app", "nebula-space");
        doc.put("kind", "bookmarks");
        doc.put("version", 1);
        doc.put("exportedAt", format(LocalDateTime.now()));
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("type", scope);
        range.put("id", scopeId == null ? null : String.valueOf(scopeId));
        range.put("includeArchived", includeArchived);
        doc.put("scope", range);
        doc.put("folders", folderRows);
        doc.put("tags", tagRows);
        doc.put("bookmarks", bookmarkRows);
        // 先写成字节再输出：Jackson 写流时会顺手关掉目标流
        out.write(JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(doc));
        out.flush();
    }

    /**
     * 「工作/前端」这样的目录路径；未分类为空串
     */
    private static String folderPath(Long folderId, Map<Long, SpaceBookmarkFolder> folderById) {
        Deque<String> names = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        Long id = folderId;
        while (id != null && id != ROOT_FOLDER_ID && seen.add(id)) {
            SpaceBookmarkFolder f = folderById.get(id);
            if (f == null) {
                break;
            }
            names.push(f.getName());
            id = f.getParentId();
        }
        return String.join("/", names);
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(EXPORT_DATE_FMT);
    }

    private void writeFolder(BufferedWriter w,
                             SpaceBookmarkFolder folder,
                             Map<Long, List<SpaceBookmarkFolder>> children,
                             Map<Long, List<SpaceBookmark>> bookmarksByFolder,
                             int indent) throws IOException {
        String pad = "    ".repeat(indent);
        long addDate = toEpochSeconds(folder.getCreateTime());
        long modified = toEpochSeconds(folder.getUpdateTime());
        w.write(pad);
        w.write("<DT><H3 ADD_DATE=\"" + addDate + "\" LAST_MODIFIED=\"" + modified + "\">"
                + escapeHtml(folder.getName()) + "</H3>\n");
        w.write(pad);
        w.write("<DL><p>\n");

        // 该目录下的书签
        List<SpaceBookmark> own = bookmarksByFolder.getOrDefault(folder.getId(), Collections.emptyList());
        for (SpaceBookmark b : own) {
            writeBookmark(w, b, indent + 1);
        }
        // 子目录
        for (SpaceBookmarkFolder child : children.getOrDefault(folder.getId(), Collections.emptyList())) {
            writeFolder(w, child, children, bookmarksByFolder, indent + 1);
        }

        w.write(pad);
        w.write("</DL><p>\n");
    }

    private void writeBookmark(BufferedWriter w, SpaceBookmark b, int indent) throws IOException {
        String pad = "    ".repeat(indent);
        long addDate = toEpochSeconds(b.getCreateTime());
        StringBuilder sb = new StringBuilder();
        sb.append(pad).append("<DT><A HREF=\"").append(escapeHtml(b.getUrl())).append("\"")
                .append(" ADD_DATE=\"").append(addDate).append("\"");
        if (StringUtils.hasText(b.getFaviconUrl())) {
            sb.append(" ICON=\"").append(escapeHtml(b.getFaviconUrl())).append("\"");
        }
        sb.append(">").append(escapeHtml(b.getTitle() == null ? b.getUrl() : b.getTitle()))
                .append("</A>\n");
        w.write(sb.toString());
    }

    // ================================================================== 工具

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }

    private Map<String, String> parseAttrs(String raw) {
        Map<String, String> map = new HashMap<>();
        Matcher m = ATTR_PATTERN.matcher(raw);
        while (m.find()) {
            map.put(m.group(1).toUpperCase(Locale.ROOT), unescapeHtml(m.group(2)));
        }
        return map;
    }

    private String unescapeHtml(String s) {
        if (s == null) return null;
        return s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String safeFaviconUrl(String icon) {
        if (!StringUtils.hasText(icon)) {
            return null;
        }
        return icon.length() <= MAX_FAVICON_URL_LENGTH ? icon : null;
    }

    private long toEpochSeconds(LocalDateTime dt) {
        if (dt == null) return Instant.now().getEpochSecond();
        return dt.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    /** 与 SpaceBookmarkAdminServiceImpl 相同的 URL 规范化逻辑，方便去重命中 */
    private String normalizeUrl(String url) {
        if (!StringUtils.hasText(url)) return "";
        String trimmed = url.trim();
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            StringBuilder sb = new StringBuilder();
            if (!scheme.isEmpty()) sb.append(scheme).append("://");
            sb.append(host);
            if (port > 0) sb.append(":").append(port);
            sb.append(path).append(query);
            return sb.toString();
        } catch (IllegalArgumentException e) {
            return trimmed;
        }
    }

    private String extractDomain(String url) {
        if (!StringUtils.hasText(url)) return null;
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return text;
        }
    }

    private BookmarkImportTaskAdminVO toImportVO(SpaceBookmarkImportTask t) {
        BookmarkImportTaskAdminVO vo = new BookmarkImportTaskAdminVO();
        vo.setId(t.getId());
        vo.setUserId(t.getUserId());
        vo.setFileId(t.getFileId());
        vo.setSource(t.getSource());
        vo.setStatus(t.getStatus());
        vo.setTotalCount(t.getTotalCount());
        vo.setSuccessCount(t.getSuccessCount());
        vo.setDuplicateCount(t.getDuplicateCount());
        vo.setFailCount(t.getFailCount());
        vo.setErrorMsg(t.getErrorMsg());
        vo.setFailures(readFailures(t.getFailDetail()));
        vo.setCreateTime(t.getCreateTime());
        vo.setUpdateTime(t.getUpdateTime());
        return vo;
    }

    static List<BookmarkImportTaskAdminVO.Failure> readFailures(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return List.of(JSON.readValue(json, BookmarkImportTaskAdminVO.Failure[].class));
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private BookmarkExportTaskAdminVO toExportVO(SpaceBookmarkExportTask t) {
        BookmarkExportTaskAdminVO vo = new BookmarkExportTaskAdminVO();
        vo.setId(t.getId());
        vo.setUserId(t.getUserId());
        vo.setFileId(t.getFileId());
        vo.setExportType(t.getExportType());
        vo.setScopeType(t.getScopeType());
        vo.setScopeId(t.getScopeId());
        vo.setStatus(t.getStatus());
        vo.setTotalCount(t.getTotalCount());
        vo.setErrorMsg(t.getErrorMsg());
        vo.setCreateTime(t.getCreateTime());
        vo.setUpdateTime(t.getUpdateTime());
        return vo;
    }

    static class ImportStats {
        int total;
        int success;
        int duplicate;
        int fail;
        final List<BookmarkImportTaskAdminVO.Failure> failures = new ArrayList<>();

        void fail(String title, String url, String reason) {
            fail++;
            if (failures.size() < MAX_FAILURES) {
                failures.add(new BookmarkImportTaskAdminVO.Failure(
                        title == null ? null : title.length() > 200 ? title.substring(0, 200) : title,
                        url == null ? null : url.length() > 500 ? url.substring(0, 500) : url,
                        reason));
            }
        }
    }
}
