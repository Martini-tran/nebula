package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.exception.AiException;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.bookmark.AiCallLimiter;
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
import com.nebula.space.vo.admin.BookmarkAiSuggestionVO;
import com.nebula.space.vo.admin.FolderAiPlanVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceBookmarkAiServiceImplTest {

    private SpaceBookmarkMapper bookmarkMapper;
    private SpaceBookmarkFolderMapper folderMapper;
    private SpaceBookmarkTagMapper bookmarkTagMapper;
    private SpaceTagMapper tagMapper;
    private AiService ai;
    private ObjectProvider<AiService> aiProvider;
    private AiCallLimiter limiter;
    private SpaceBookmarkAiServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SpaceBookmark.class);
        TableInfoHelper.initTableInfo(assistant, SpaceBookmarkFolder.class);
        TableInfoHelper.initTableInfo(assistant, SpaceBookmarkTag.class);
        TableInfoHelper.initTableInfo(assistant, SpaceTag.class);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        bookmarkMapper = mock(SpaceBookmarkMapper.class);
        folderMapper = mock(SpaceBookmarkFolderMapper.class);
        bookmarkTagMapper = mock(SpaceBookmarkTagMapper.class);
        tagMapper = mock(SpaceTagMapper.class);
        ai = mock(AiService.class);
        aiProvider = mock(ObjectProvider.class);
        when(aiProvider.getIfAvailable()).thenReturn(ai);
        limiter = mock(AiCallLimiter.class);
        when(limiter.tryAcquire(anyLong())).thenReturn(true);
        service = new SpaceBookmarkAiServiceImpl(bookmarkMapper, folderMapper, bookmarkTagMapper, tagMapper, aiProvider, limiter);
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());

        when(folderMapper.selectList(any())).thenReturn(List.of(
                folder(1, 0, "前端", 1, 0), folder(11, 1, "Vue", 2, 0), folder(2, 0, "后端", 1, 1)));
        when(tagMapper.selectList(any())).thenReturn(List.of(tag(1, "文档", "#4f46e5"), tag(2, "开源", "#2563eb")));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static SpaceBookmarkFolder folder(long id, long parentId, String name, int level, int sort) {
        SpaceBookmarkFolder f = new SpaceBookmarkFolder();
        f.setId(id);
        f.setUserId(42L);
        f.setParentId(parentId);
        f.setName(name);
        f.setLevel(level);
        f.setSortOrder(sort);
        return f;
    }

    private static SpaceTag tag(long id, String name, String color) {
        SpaceTag t = new SpaceTag();
        t.setId(id);
        t.setUserId(42L);
        t.setName(name);
        t.setColor(color);
        return t;
    }

    private static SpaceBookmark bookmark(long id, long folderId, String title, String description) {
        SpaceBookmark b = new SpaceBookmark();
        b.setId(id);
        b.setUserId(42L);
        b.setFolderId(folderId);
        b.setTitle(title);
        b.setUrl("https://example.com/" + id);
        b.setDescription(description);
        b.setStatus(0);
        return b;
    }

    private static SpaceBookmarkTag rel(long bookmarkId, long tagId) {
        SpaceBookmarkTag r = new SpaceBookmarkTag();
        r.setBookmarkId(bookmarkId);
        r.setTagId(tagId);
        return r;
    }

    private static BookmarkAiSuggestRequest suggest(List<String> actions, Long... ids) {
        BookmarkAiSuggestRequest req = new BookmarkAiSuggestRequest();
        req.setBookmarkIds(List.of(ids));
        req.setActions(actions);
        return req;
    }

    private void aiReplies(String content) {
        when(ai.chat(any())).thenReturn(Map.of("content", content));
    }

    // ----------------------------------------------------------------- 书签建议

    @Test
    void suggestionsAreValidatedAgainstExistingFoldersAndTags() {
        when(bookmarkMapper.selectList(any())).thenReturn(List.of(
                bookmark(102, 11, "Pinia", "状态管理"),
                bookmark(101, 0, "首页 - Vite 官方中文文档 | 下一代的前端工具链", "")));
        when(bookmarkTagMapper.selectList(any())).thenReturn(List.of(rel(102, 1)));
        aiReplies("""
                {"items":[
                  {"i":1,"folder":"前端／构建工具","tags":["常用","文档","#构建","文档","构建"],"title":"Vite 官方文档","description":" 下一代前端构建工具 "},
                  {"i":2,"folder":"前端 / vue","tags":["文档"],"title":"Pinia","description":"已有描述时忽略"},
                  {"i":9,"folder":"后端"}
                ]}""");

        List<BookmarkAiSuggestionVO> result = service.suggest(
                suggest(List.of("folder", "tags", "TITLE", "description", "unknown"), 101L, 102L, 999L));

        // 102 的建议全是原样：不返回
        assertEquals(1, result.size());
        BookmarkAiSuggestionVO vo = result.get(0);
        assertEquals(101L, vo.getBookmarkId());
        assertNull(vo.getFolder().getId());
        assertEquals("前端 / 构建工具", vo.getFolder().getPath());
        assertEquals(2, vo.getTags().size());
        assertEquals(1L, vo.getTags().get(0).getId());
        assertEquals("#4f46e5", vo.getTags().get(0).getColor());
        assertNull(vo.getTags().get(1).getId());
        assertEquals("构建", vo.getTags().get(1).getName());
        assertEquals("Vite 官方文档", vo.getTitle());
        assertEquals("下一代前端构建工具", vo.getDescription());

        ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
        verify(ai).chat(captor.capture());
        AiRequest request = captor.getValue();
        assertEquals("42", request.getUserId());
        assertEquals(2, request.getMessages().size());
        assertEquals(Map.of("type", "json_object"), request.getOptions().get("response_format"));
        String prompt = (String) request.getMessages().get(1).get("content");
        assertTrue(prompt.contains("前端 / Vue"), prompt);
        assertTrue(prompt.contains("\"folder\":\"未分类\""), prompt);
        assertTrue(prompt.contains("\"tags\":[\"文档\"]"), prompt);
    }

    @Test
    void existingFolderSuggestionCarriesId() {
        when(bookmarkMapper.selectList(any())).thenReturn(List.of(bookmark(101, 0, "Spring Boot", "")));
        when(bookmarkTagMapper.selectList(any())).thenReturn(List.of());
        aiReplies("{\"items\":[{\"i\":1,\"folder\":\"后端\"}]}");

        BookmarkAiSuggestionVO vo = service.suggest(suggest(List.of("folder"), 101L)).get(0);

        assertEquals(2L, vo.getFolder().getId());
        assertEquals("后端", vo.getFolder().getPath());
        assertNull(vo.getTags());
        assertNull(vo.getTitle());
    }

    @Test
    void badFolderSuggestionsAreDropped() {
        assertNull(SpaceBookmarkAiServiceImpl.folderTarget("未分类", 5L, Map.of(), Map.of()));
        assertNull(SpaceBookmarkAiServiceImpl.folderTarget("未分类 / 杂项", 5L, Map.of(), Map.of()));
        assertNull(SpaceBookmarkAiServiceImpl.folderTarget(" / ", 5L, Map.of(), Map.of()));
        assertNull(SpaceBookmarkAiServiceImpl.folderTarget("a/b/c/d/e", 5L, Map.of(), Map.of()));
        assertNull(SpaceBookmarkAiServiceImpl.folderTarget("x".repeat(51), 5L, Map.of(), Map.of()));
    }

    @Test
    void unreadableReplyIsReported() {
        when(bookmarkMapper.selectList(any())).thenReturn(List.of(bookmark(101, 0, "Vite", "")));
        when(bookmarkTagMapper.selectList(any())).thenReturn(List.of());
        aiReplies("抱歉，我不能处理这个请求。");

        BizException e = assertThrows(BizException.class, () -> service.suggest(suggest(List.of("tags"), 101L)));
        assertEquals(HttpStatus.BAD_GATEWAY, e.getCode());
    }

    @Test
    void missingApiKeyIsExplained() {
        when(bookmarkMapper.selectList(any())).thenReturn(List.of(bookmark(101, 0, "Vite", "")));
        when(bookmarkTagMapper.selectList(any())).thenReturn(List.of());
        when(ai.chat(any())).thenThrow(new AiException("未配置AI ApiKey（nebula.ai.openai.api-key 或节点模型档案）"));

        BizException e = assertThrows(BizException.class, () -> service.suggest(suggest(List.of("tags"), 101L)));
        assertTrue(e.getMessage().contains("AI_API_KEY"), e.getMessage());
    }

    @Test
    void aiDisabledOrOverLimitIsRefused() {
        when(bookmarkMapper.selectList(any())).thenReturn(List.of(bookmark(101, 0, "Vite", "")));
        when(bookmarkTagMapper.selectList(any())).thenReturn(List.of());

        when(limiter.tryAcquire(42L)).thenReturn(false);
        BizException limited = assertThrows(BizException.class, () -> service.suggest(suggest(List.of("tags"), 101L)));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.getCode());

        when(aiProvider.getIfAvailable()).thenReturn(null);
        BizException disabled = assertThrows(BizException.class, () -> service.suggest(suggest(List.of("tags"), 101L)));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, disabled.getCode());
        verify(ai, never()).chat(any());
    }

    @Test
    void noValidActionIsBadRequest() {
        BizException e = assertThrows(BizException.class, () -> service.suggest(suggest(List.of("delete"), 101L)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
    }

    // ----------------------------------------------------------------- 目录重排

    @Test
    void planKeepsValidOpsAndCountsDropped() {
        when(bookmarkMapper.selectMaps(any())).thenReturn(List.of(Map.of("fid", 11L, "cnt", 3L), Map.of("fid", 2L, "cnt", 5L)));
        when(bookmarkMapper.selectList(any())).thenReturn(List.of(bookmark(1, 11, "Vue.js 官方文档", "")));
        aiReplies("""
                {"summary":"把 Vue 提到顶层，后端改名","ops":[
                  {"op":"create","key":"N1","parent":"","name":"框架","reason":"集中放框架"},
                  {"op":"move","folder":"F2","parent":"N1","reason":"Vue 是框架"},
                  {"op":"rename","folder":"F3","name":"服务端","reason":"更准确"},
                  {"op":"merge","folder":"F1","into":"F1"},
                  {"op":"rename","folder":"F42","name":"不存在"}
                ]}""");
        FolderAiPlanRequest req = new FolderAiPlanRequest();
        req.setHint("按技术栈分");

        FolderAiPlanVO plan = service.plan(req);

        assertEquals("把 Vue 提到顶层，后端改名", plan.getSummary());
        assertEquals(List.of("create", "move", "rename"), plan.getOps().stream().map(FolderAiPlanVO.Op::getOp).toList());
        assertEquals("N1", plan.getOps().get(1).getParent());
        assertEquals("11", plan.getOps().get(1).getFolder());
        assertEquals("框架 / Vue", plan.getOps().get(1).getAfter());
        assertEquals("2", plan.getOps().get(2).getFolder());
        assertEquals(2, plan.getDropped());

        ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
        verify(ai).chat(captor.capture());
        String prompt = (String) captor.getValue().getMessages().get(1).get("content");
        assertTrue(prompt.contains("F2 | 前端 / Vue | 3 条 | Vue.js 官方文档"), prompt);
        assertTrue(prompt.contains("按技术栈分"), prompt);
    }

    @Test
    void planNeedsFolders() {
        when(folderMapper.selectList(any())).thenReturn(List.of());
        BizException e = assertThrows(BizException.class, () -> service.plan(new FolderAiPlanRequest()));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
    }
}
