package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.HighlightCreateRequest;
import com.nebula.space.dto.me.HighlightQuery;
import com.nebula.space.dto.me.HighlightSaveRequest;
import com.nebula.space.dto.me.ReadingCreateRequest;
import com.nebula.space.dto.me.ReadingQuery;
import com.nebula.space.dto.me.ReadingSaveRequest;
import com.nebula.space.entity.SpaceBookmark;
import com.nebula.space.entity.SpaceNote;
import com.nebula.space.entity.SpaceReading;
import com.nebula.space.entity.SpaceReadingHighlight;
import com.nebula.space.entity.SpaceTask;
import com.nebula.space.mapper.SpaceBookmarkMapper;
import com.nebula.space.mapper.SpaceNoteMapper;
import com.nebula.space.mapper.SpaceReadingHighlightMapper;
import com.nebula.space.mapper.SpaceReadingMapper;
import com.nebula.space.mapper.SpaceTaskMapper;
import com.nebula.space.reading.ArticleExtractor;
import com.nebula.space.reading.ArticleFetcher;
import com.nebula.space.search.SearchCriteria;
import com.nebula.space.service.SpaceReadingService;
import com.nebula.space.util.Stamps;
import com.nebula.space.vo.me.HighlightVO;
import com.nebula.space.vo.me.ReadingItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 稍后读服务实现
 */
@Service
@RequiredArgsConstructor
public class SpaceReadingServiceImpl implements SpaceReadingService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final String DONE = "done";
    /**
     * 搜索结果里每篇最多带几段命中的正文
     */
    static final int MATCHED_PARAGRAPHS = 6;
    private static final int MAX_HIGHLIGHT = 5000;

    private final SpaceReadingMapper readingMapper;
    private final SpaceReadingHighlightMapper highlightMapper;
    private final SpaceBookmarkMapper bookmarkMapper;
    private final SpaceNoteMapper noteMapper;
    private final SpaceTaskMapper taskMapper;
    private final ArticleFetcher fetcher;

    // ----------------------------------------------------------------- 文章

    @Override
    public List<ReadingItemVO> list(ReadingQuery query) {
        Long userId = requireUserId();
        ReadingQuery q = query == null ? new ReadingQuery() : query;
        boolean archived = Boolean.TRUE.equals(q.getArchived());
        List<SpaceReading> rows = readingMapper.selectList(
                withoutContent()
                        .eq(SpaceReading::getUserId, userId)
                        .eq(SpaceReading::getArchived, archived ? 1 : 0)
                        .eq(StringUtils.hasText(q.getStatus()), SpaceReading::getReadStatus, q.getStatus())
                        .orderByDesc(SpaceReading::getCreateTime)
                        .orderByDesc(SpaceReading::getId)
        );
        Set<Long> saved = savedIds(userId, rows.stream().map(SpaceReading::getId).toList());
        return rows.stream().map(r -> toVO(r, null, saved.contains(r.getId()))).toList();
    }

    @Override
    public List<ReadingItemVO> search(SearchCriteria q, int limit) {
        boolean done = q.hasState("done");
        boolean open = q.hasState("open");
        // is:overdue 对文章没有意义，前端也不认
        if (!q.getStates().isEmpty() && !done && !open) {
            return List.of();
        }
        LambdaQueryWrapper<SpaceReading> wrapper = new LambdaQueryWrapper<SpaceReading>()
                .eq(SpaceReading::getUserId, requireUserId())
                .eq(done && !open, SpaceReading::getReadStatus, DONE)
                .ne(open && !done, SpaceReading::getReadStatus, DONE);
        q.inTimeRange(wrapper, SpaceReading::getCreateTime);
        SearchCriteria.matchTerms(wrapper, q.getTerms(),
                SpaceReading::getTitle, SpaceReading::getUrl, SpaceReading::getExcerpt, SpaceReading::getContent);
        wrapper.orderByDesc(SpaceReading::getCreateTime).orderByDesc(SpaceReading::getId).last("limit " + limit);
        return readingMapper.selectList(wrapper).stream()
                .map(r -> {
                    List<String> paragraphs = readContent(r.getContent());
                    return toVO(r, matchedParagraphs(paragraphs, q.getTerms()), paragraphs != null);
                })
                .toList();
    }

    @Override
    public List<ReadingItemVO> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Long userId = requireUserId();
        List<SpaceReading> rows = readingMapper.selectList(
                withoutContent()
                        .eq(SpaceReading::getUserId, userId)
                        .in(SpaceReading::getId, ids)
        );
        Set<Long> saved = savedIds(userId, rows.stream().map(SpaceReading::getId).toList());
        return rows.stream().map(r -> toVO(r, null, saved.contains(r.getId()))).toList();
    }

    /**
     * 正文里出现任一搜索词的段落，最多几段；一段都没有返回 null
     */
    static List<String> matchedParagraphs(List<String> paragraphs, List<String> terms) {
        if (paragraphs == null || terms.isEmpty()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (String p : paragraphs) {
            String lower = p.toLowerCase(Locale.ROOT);
            if (terms.stream().anyMatch(lower::contains)) {
                out.add(p);
                if (out.size() >= MATCHED_PARAGRAPHS) {
                    break;
                }
            }
        }
        return out.isEmpty() ? null : out;
    }

    @Override
    public ReadingItemVO get(Long id) {
        SpaceReading r = requireItem(requireUserId(), id, true);
        return withContent(r);
    }

    @Override
    public ReadingItemVO create(ReadingCreateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        Long userId = requireUserId();
        String url = requireUrl(req.getUrl());
        String hash = sha256(normalizeUrl(url));
        Long bookmarkId = req.getBookmarkId() != null && ownsBookmark(userId, req.getBookmarkId()) ? req.getBookmarkId() : null;
        SpaceReading existing = findByHash(userId, hash);
        if (existing != null) {
            return revive(existing, bookmarkId);
        }

        SpaceReading r = new SpaceReading();
        r.setUserId(userId);
        r.setUrl(url);
        r.setUrlHash(hash);
        r.setTitle(StringUtils.hasText(req.getTitle()) ? req.getTitle().trim() : domainOf(url));
        r.setExcerpt("");
        r.setReadMinutes(0);
        r.setReadStatus("unread");
        r.setReadProgress(0.0);
        r.setReadPosition(0.0);
        r.setThought("");
        r.setArchived(0);
        r.setBookmarkId(bookmarkId);
        // 抓取可能要十几秒，放在事务和插入之前
        fillFromFetch(r, fetcher.fetch(url));
        try {
            readingMapper.insert(r);
        } catch (DuplicateKeyException e) {
            // 同一网址同时加了两次：另一份已插入，按已存在处理
            SpaceReading other = findByHash(userId, hash);
            if (other == null) {
                throw e;
            }
            return revive(other, bookmarkId);
        }
        return toVO(r, null, r.getContent() != null);
    }

    @Override
    public ReadingItemVO refetch(Long id) {
        SpaceReading r = requireItem(requireUserId(), id, true);
        if (r.getContent() != null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "这篇已经存档了；划线按存档的段落定位，不再重新抓取");
        }
        if (fillFromFetch(r, fetcher.fetch(r.getUrl()))) {
            r.setUpdateTime(LocalDateTime.now());
            readingMapper.updateById(r);
        }
        return withContent(r);
    }

    @Override
    public ReadingItemVO update(Long id, ReadingSaveRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        Long userId = requireUserId();
        // 阅读时每隔几秒存一次进度，这里不读正文
        SpaceReading r = requireItem(userId, id, false);
        if (req.getTitle() != null) {
            if (!StringUtils.hasText(req.getTitle())) {
                throw new BizException(HttpStatus.BAD_REQUEST, "标题不能为空");
            }
            r.setTitle(req.getTitle().trim());
        }
        if (req.getProgress() != null) {
            r.setReadProgress(req.getProgress());
        }
        if (req.getPosition() != null) {
            r.setReadPosition(req.getPosition());
        }
        if (req.getThought() != null) {
            r.setThought(req.getThought().trim());
        }
        if (req.getArchived() != null) {
            r.setArchived(req.getArchived() ? 1 : 0);
        }
        if (req.getLastReadTime() != null) {
            r.setLastReadTime(Stamps.parse(req.getLastReadTime()));
        }
        if (req.getStatus() != null) {
            r.setReadStatus(req.getStatus());
            if (DONE.equals(req.getStatus())) {
                r.setReadProgress(1.0);
                LocalDateTime done = Stamps.parse(req.getDoneTime());
                if (done != null) {
                    r.setDoneTime(done);
                } else if (r.getDoneTime() == null) {
                    r.setDoneTime(LocalDateTime.now());
                }
            } else {
                r.setDoneTime(null);
            }
        }
        // 审计填充是严格模式，已有值不会覆盖，这里显式刷新；正文没读出来，为空不会被写回
        r.setUpdateTime(LocalDateTime.now());
        readingMapper.updateById(r);
        return toVO(r, null, !savedIds(userId, List.of(r.getId())).isEmpty());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = requireUserId();
        SpaceReading r = requireItem(userId, id, false);
        highlightMapper.delete(
                new LambdaQueryWrapper<SpaceReadingHighlight>()
                        .eq(SpaceReadingHighlight::getUserId, userId)
                        .eq(SpaceReadingHighlight::getItemId, r.getId())
        );
        readingMapper.deleteById(r.getId());
    }

    // ----------------------------------------------------------------- 划线

    @Override
    public List<HighlightVO> listHighlights(HighlightQuery query) {
        Long userId = requireUserId();
        Long itemId = query == null ? null : query.getItemId();
        return highlightMapper.selectList(
                        new LambdaQueryWrapper<SpaceReadingHighlight>()
                                .eq(SpaceReadingHighlight::getUserId, userId)
                                .eq(itemId != null, SpaceReadingHighlight::getItemId, itemId)
                                .orderByDesc(SpaceReadingHighlight::getCreateTime)
                                .orderByDesc(SpaceReadingHighlight::getId)
                ).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public List<HighlightVO> searchHighlights(SearchCriteria q, int limit) {
        LambdaQueryWrapper<SpaceReadingHighlight> wrapper = new LambdaQueryWrapper<SpaceReadingHighlight>()
                .eq(SpaceReadingHighlight::getUserId, requireUserId());
        q.inTimeRange(wrapper, SpaceReadingHighlight::getCreateTime);
        SearchCriteria.matchTerms(wrapper, q.getTerms(), SpaceReadingHighlight::getQuote, SpaceReadingHighlight::getNote);
        wrapper.orderByDesc(SpaceReadingHighlight::getCreateTime).orderByDesc(SpaceReadingHighlight::getId).last("limit " + limit);
        return highlightMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public HighlightVO createHighlight(HighlightCreateRequest req) {
        if (req == null || req.getItemId() == null || req.getPara() == null || req.getStart() == null || req.getEnd() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "划线的文章和位置都要有");
        }
        Long userId = requireUserId();
        SpaceReading item = requireItem(userId, req.getItemId(), true);
        List<String> paragraphs = readContent(item.getContent());
        if (paragraphs == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "这篇没有存档的阅读版，没法划线");
        }
        int para = req.getPara();
        int start = req.getStart();
        int end = req.getEnd();
        if (para < 0 || para >= paragraphs.size() || start < 0 || start >= end || end > paragraphs.get(para).length()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "划线位置不对");
        }
        if (end - start > MAX_HIGHLIGHT) {
            throw new BizException(HttpStatus.BAD_REQUEST, "一次最多划 " + MAX_HIGHLIGHT + " 字");
        }
        boolean overlap = highlightMapper.selectCount(
                new LambdaQueryWrapper<SpaceReadingHighlight>()
                        .eq(SpaceReadingHighlight::getUserId, userId)
                        .eq(SpaceReadingHighlight::getItemId, item.getId())
                        .eq(SpaceReadingHighlight::getPara, para)
                        .lt(SpaceReadingHighlight::getStartOffset, end)
                        .gt(SpaceReadingHighlight::getEndOffset, start)
        ) > 0;
        if (overlap) {
            throw new BizException(HttpStatus.BAD_REQUEST, "和已有划线重叠了");
        }
        SpaceReadingHighlight h = new SpaceReadingHighlight();
        h.setUserId(userId);
        h.setItemId(item.getId());
        h.setPara(para);
        h.setStartOffset(start);
        h.setEndOffset(end);
        // 以存档正文按锚点截出的为准，和阅读页渲染出来的一致
        h.setQuote(paragraphs.get(para).substring(start, end));
        h.setColor(req.getColor() == null ? "yellow" : req.getColor());
        h.setNote(req.getNote() == null ? "" : req.getNote().trim());
        highlightMapper.insert(h);
        return toVO(h);
    }

    @Override
    public HighlightVO updateHighlight(Long id, HighlightSaveRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        Long userId = requireUserId();
        SpaceReadingHighlight h = requireHighlight(userId, id);
        if (req.getColor() != null) {
            h.setColor(req.getColor());
        }
        if (req.getNote() != null) {
            h.setNote(req.getNote().trim());
        }
        if (req.getNoteId() != null) {
            if (noteMapper.selectCount(new LambdaQueryWrapper<SpaceNote>()
                    .eq(SpaceNote::getId, req.getNoteId())
                    .eq(SpaceNote::getUserId, userId)) == 0) {
                throw new BizException(HttpStatus.BAD_REQUEST, "随手记不存在");
            }
            h.setNoteId(req.getNoteId());
        }
        if (req.getTaskId() != null) {
            if (taskMapper.selectCount(new LambdaQueryWrapper<SpaceTask>()
                    .eq(SpaceTask::getId, req.getTaskId())
                    .eq(SpaceTask::getUserId, userId)) == 0) {
                throw new BizException(HttpStatus.BAD_REQUEST, "任务不存在");
            }
            h.setTaskId(req.getTaskId());
        }
        if (req.getTaskTitle() != null) {
            h.setTaskTitle(StringUtils.hasText(req.getTaskTitle()) ? req.getTaskTitle().trim() : null);
        }
        h.setUpdateTime(LocalDateTime.now());
        highlightMapper.updateById(h);
        return toVO(h);
    }

    @Override
    public void deleteHighlight(Long id) {
        SpaceReadingHighlight h = requireHighlight(requireUserId(), id);
        highlightMapper.deleteById(h.getId());
    }

    // ----------------------------------------------------------------- 内部工具

    /**
     * 已经在的网址：归档的放回队列，补上书签，之前没抓到正文的再抓一次
     */
    private ReadingItemVO revive(SpaceReading r, Long bookmarkId) {
        boolean changed = false;
        if (r.getArchived() != null && r.getArchived() == 1) {
            r.setArchived(0);
            changed = true;
        }
        if (r.getBookmarkId() == null && bookmarkId != null) {
            r.setBookmarkId(bookmarkId);
            changed = true;
        }
        if (r.getContent() == null && fillFromFetch(r, fetcher.fetch(r.getUrl()))) {
            changed = true;
        }
        if (changed) {
            r.setUpdateTime(LocalDateTime.now());
            readingMapper.updateById(r);
        }
        return toVO(r, null, r.getContent() != null);
    }

    /**
     * 用抓取结果补正文、摘要，标题还是域名占位的换成网页标题；有改动返回 true
     */
    private boolean fillFromFetch(SpaceReading r, ArticleExtractor.Article article) {
        if (article == null) {
            return false;
        }
        boolean changed = false;
        if (article.paragraphs() != null) {
            r.setContent(writeJson(article.paragraphs()));
            r.setReadMinutes(article.minutes());
            changed = true;
        }
        if (!StringUtils.hasText(r.getExcerpt()) && StringUtils.hasText(article.excerpt())) {
            r.setExcerpt(article.excerpt());
            changed = true;
        }
        if (StringUtils.hasText(article.title()) && domainOf(r.getUrl()).equals(r.getTitle())) {
            r.setTitle(article.title());
            changed = true;
        }
        return changed;
    }

    private static LambdaQueryWrapper<SpaceReading> withoutContent() {
        return new LambdaQueryWrapper<SpaceReading>().select(SpaceReading.class, f -> !"content".equals(f.getColumn()));
    }

    /**
     * 这些文章里哪些有存档正文（列表不读正文，单独查一下）
     */
    private Set<Long> savedIds(Long userId, List<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        List<Object> saved = readingMapper.selectObjs(
                new LambdaQueryWrapper<SpaceReading>()
                        .select(SpaceReading::getId)
                        .eq(SpaceReading::getUserId, userId)
                        .in(SpaceReading::getId, ids)
                        .isNotNull(SpaceReading::getContent)
        );
        Set<Long> out = new HashSet<>();
        for (Object o : saved) {
            if (o instanceof Number n) {
                out.add(n.longValue());
            }
        }
        return out;
    }

    private SpaceReading requireItem(Long userId, Long id, boolean withContent) {
        LambdaQueryWrapper<SpaceReading> wrapper = withContent ? new LambdaQueryWrapper<SpaceReading>() : withoutContent();
        SpaceReading r = id == null ? null : readingMapper.selectOne(
                wrapper.eq(SpaceReading::getId, id).eq(SpaceReading::getUserId, userId)
        );
        if (r == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "文章不存在或已删除");
        }
        return r;
    }

    private SpaceReading findByHash(Long userId, String hash) {
        return readingMapper.selectOne(
                new LambdaQueryWrapper<SpaceReading>()
                        .eq(SpaceReading::getUserId, userId)
                        .eq(SpaceReading::getUrlHash, hash)
        );
    }

    private SpaceReadingHighlight requireHighlight(Long userId, Long id) {
        SpaceReadingHighlight h = id == null ? null : highlightMapper.selectOne(
                new LambdaQueryWrapper<SpaceReadingHighlight>()
                        .eq(SpaceReadingHighlight::getId, id)
                        .eq(SpaceReadingHighlight::getUserId, userId)
        );
        if (h == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "划线不存在或已删除");
        }
        return h;
    }

    private boolean ownsBookmark(Long userId, Long bookmarkId) {
        return bookmarkMapper.selectCount(
                new LambdaQueryWrapper<SpaceBookmark>()
                        .eq(SpaceBookmark::getId, bookmarkId)
                        .eq(SpaceBookmark::getUserId, userId)
        ) > 0;
    }

    /**
     * 只收 http / https 网址；空格转成 %20
     */
    static String requireUrl(String raw) {
        String url = raw == null ? "" : raw.trim().replace(" ", "%20");
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (("http".equals(scheme) || "https".equals(scheme)) && StringUtils.hasText(uri.getRawAuthority())) {
                return url;
            }
        } catch (URISyntaxException e) {
            // 落到下面统一报错
        }
        throw new BizException(HttpStatus.BAD_REQUEST, "网址不正确，要以 http:// 或 https:// 开头");
    }

    /**
     * 去重用的规范化网址：协议、主机转小写，去掉 #锚点，空路径补成 /
     */
    static String normalizeUrl(String url) {
        try {
            URI uri = new URI(url);
            StringBuilder sb = new StringBuilder()
                    .append(uri.getScheme().toLowerCase(Locale.ROOT)).append("://")
                    .append(uri.getRawAuthority().toLowerCase(Locale.ROOT));
            sb.append(StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : "/");
            if (uri.getRawQuery() != null) {
                sb.append('?').append(uri.getRawQuery());
            }
            return sb.toString();
        } catch (URISyntaxException | RuntimeException e) {
            return url;
        }
    }

    /**
     * 列表上显示的来源：主机名去掉 www.
     */
    public static String domainOf(String url) {
        String host = null;
        try {
            host = new URI(url).getHost();
        } catch (URISyntaxException e) {
            // 退到按字面切
        }
        if (host == null) {
            host = url.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "").split("[/?#]", 2)[0];
        }
        host = host.toLowerCase(Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "数据格式不正确");
        }
    }

    private static List<String> readContent(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return JSON.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private ReadingItemVO withContent(SpaceReading r) {
        List<String> content = readContent(r.getContent());
        return toVO(r, content, content != null);
    }

    private ReadingItemVO toVO(SpaceReading r, List<String> content, boolean saved) {
        ReadingItemVO vo = new ReadingItemVO();
        vo.setId(r.getId());
        vo.setUrl(r.getUrl());
        vo.setTitle(r.getTitle());
        vo.setDomain(domainOf(r.getUrl()));
        vo.setExcerpt(r.getExcerpt() == null ? "" : r.getExcerpt());
        vo.setContent(content);
        vo.setSaved(saved);
        vo.setMinutes(r.getReadMinutes() == null ? 0 : r.getReadMinutes());
        vo.setStatus(r.getReadStatus());
        vo.setProgress(r.getReadProgress() == null ? 0.0 : r.getReadProgress());
        vo.setPosition(r.getReadPosition() == null ? 0.0 : r.getReadPosition());
        vo.setThought(r.getThought() == null ? "" : r.getThought());
        vo.setArchived(r.getArchived() != null && r.getArchived() == 1);
        vo.setBookmarkId(r.getBookmarkId());
        vo.setAddTime(r.getCreateTime() == null ? LocalDateTime.now() : r.getCreateTime());
        vo.setLastReadTime(r.getLastReadTime());
        vo.setDoneTime(r.getDoneTime());
        return vo;
    }

    private HighlightVO toVO(SpaceReadingHighlight h) {
        HighlightVO vo = new HighlightVO();
        vo.setId(h.getId());
        vo.setItemId(h.getItemId());
        vo.setPara(h.getPara());
        vo.setStart(h.getStartOffset());
        vo.setEnd(h.getEndOffset());
        vo.setText(h.getQuote());
        vo.setColor(h.getColor());
        vo.setNote(h.getNote());
        vo.setNoteId(h.getNoteId());
        vo.setTaskId(h.getTaskId());
        vo.setTaskTitle(h.getTaskTitle());
        vo.setCreateTime(h.getCreateTime() == null ? LocalDateTime.now() : h.getCreateTime());
        return vo;
    }
}
