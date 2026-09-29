package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.nebula.space.vo.admin.BookmarkImportTaskAdminVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceBookmarkPorterServiceImplTest {

    private SpaceBookmarkMapper bookmarkMapper;
    private SpaceBookmarkFolderMapper folderMapper;
    private SpaceBookmarkImportTaskMapper importTaskMapper;
    private SpaceBookmarkTagMapper bookmarkTagMapper;
    private SpaceTagMapper tagMapper;
    private SpaceBookmarkPorterServiceImpl service;
    /**
     * 提交给后台的导入，测试里手动执行
     */
    private final List<Runnable> queued = new ArrayList<>();

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : List.of(SpaceBookmark.class, SpaceBookmarkFolder.class, SpaceBookmarkTag.class, SpaceTag.class,
                SpaceBookmarkImportTask.class, SpaceBookmarkExportTask.class)) {
            TableInfoHelper.initTableInfo(assistant, c);
        }
    }

    @BeforeEach
    void setUp() {
        bookmarkMapper = mock(SpaceBookmarkMapper.class);
        folderMapper = mock(SpaceBookmarkFolderMapper.class);
        importTaskMapper = mock(SpaceBookmarkImportTaskMapper.class);
        bookmarkTagMapper = mock(SpaceBookmarkTagMapper.class);
        tagMapper = mock(SpaceTagMapper.class);
        service = new SpaceBookmarkPorterServiceImpl(bookmarkMapper, folderMapper, bookmarkTagMapper,
                importTaskMapper, mock(SpaceBookmarkExportTaskMapper.class), tagMapper);
        service.useExecutor(queued::add);
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
        UserContext.clear();
    }

    private static MultipartFile html(String body) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        byte[] bytes = ("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n"
                + body + "\n</DL><p>\n").getBytes(StandardCharsets.UTF_8);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) bytes.length);
        when(file.getOriginalFilename()).thenReturn("bookmarks.html");
        when(file.getBytes()).thenReturn(bytes);
        return file;
    }

    private SpaceBookmarkImportTask lastSaved() {
        ArgumentCaptor<SpaceBookmarkImportTask> captor = ArgumentCaptor.forClass(SpaceBookmarkImportTask.class);
        verify(importTaskMapper, org.mockito.Mockito.atLeastOnce()).updateById(captor.capture());
        return captor.getValue();
    }

    @Test
    void returnsAProcessingTaskAndImportsInTheBackground() throws IOException {
        BookmarkImportTaskAdminVO vo = service.importChromeHtml(html("""
                <DT><A HREF="https://a.example.com/">A</A>
                <DT><A HREF="https://b.example.com/">B</A>
                """));

        assertEquals(1, vo.getStatus());
        assertEquals(2, vo.getTotalCount());
        verify(bookmarkMapper, never()).insert(any(SpaceBookmark.class));
        assertEquals(1, queued.size());

        queued.get(0).run();

        verify(bookmarkMapper, org.mockito.Mockito.times(2)).insert(any(SpaceBookmark.class));
        SpaceBookmarkImportTask saved = lastSaved();
        assertEquals(2, saved.getStatus());
        assertEquals(2, saved.getSuccessCount());
        assertNull(saved.getFailDetail());
        // 后台线程用完把用户上下文清掉
        assertNull(UserContext.getUserId());
    }

    @Test
    void recordsWhyEachBookmarkFailed() throws IOException {
        service.importChromeHtml(html("""
                <DT><A ADD_DATE="1">没有网址</A>
                <DT><A HREF="https://%s/">太长</A>
                <DT><A HREF="https://ok.example.com/">正常</A>
                """.formatted(("a".repeat(60) + ".").repeat(5) + "com")));
        queued.get(0).run();

        SpaceBookmarkImportTask saved = lastSaved();
        assertEquals(2, saved.getStatus());
        assertEquals(1, saved.getSuccessCount());
        assertEquals(2, saved.getFailCount());
        List<BookmarkImportTaskAdminVO.Failure> failures = SpaceBookmarkPorterServiceImpl.readFailures(saved.getFailDetail());
        assertEquals(2, failures.size());
        assertEquals("没有网址", failures.get(0).getTitle());
        assertEquals("没有网址", failures.get(0).getReason());
        assertTrue(failures.get(1).getReason().startsWith("域名超过"), failures.get(1).getReason());
    }

    @Test
    void failureMidwayKeepsWhatWasImported() throws IOException {
        when(folderMapper.selectList(any())).thenReturn(List.of());
        doThrow(new IllegalStateException("数据库断开")).when(folderMapper).insert(any(SpaceBookmarkFolder.class));
        service.importChromeHtml(html("""
                <DT><A HREF="https://a.example.com/">A</A>
                <DT><H3>目录</H3>
                <DL><p>
                    <DT><A HREF="https://b.example.com/">B</A>
                </DL><p>
                """));
        queued.get(0).run();

        SpaceBookmarkImportTask saved = lastSaved();
        assertEquals(3, saved.getStatus());
        assertEquals(1, saved.getSuccessCount());
        assertTrue(saved.getErrorMsg().contains("前面的已导入"), saved.getErrorMsg());
    }

    @Test
    void fileWithoutBookmarksIsRejectedUpFront() throws IOException {
        BizException e = assertThrows(BizException.class, () -> service.importChromeHtml(html("<DT><H3>空目录</H3>")));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
        verify(importTaskMapper, never()).insert(any(SpaceBookmarkImportTask.class));
        assertTrue(queued.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void exportCanIncludeArchived() {
        service.exportChromeHtml("all", null, true, new ByteArrayOutputStream());
        ArgumentCaptor<Wrapper<SpaceBookmark>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(bookmarkMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("status IN"), sql);
        Map<String, Object> params = ((AbstractWrapper<?, ?, ?>) captor.getValue()).getParamNameValuePairs();
        assertTrue(params.containsValue(0) && params.containsValue(1), params.toString());
    }

    private static SpaceBookmarkFolder folder(long id, long parentId, String name) {
        SpaceBookmarkFolder f = new SpaceBookmarkFolder();
        f.setId(id);
        f.setParentId(parentId);
        f.setName(name);
        return f;
    }

    @Test
    void jsonExportKeepsTagsNotesAndFolderPath() throws IOException {
        SpaceBookmark b = new SpaceBookmark();
        b.setId(7L);
        b.setFolderId(2L);
        b.setTitle("Vue 文档");
        b.setUrl("https://vuejs.org/");
        b.setRemark("周会前看");
        b.setStatus(1);
        when(bookmarkMapper.selectList(any())).thenReturn(List.of(b));
        when(folderMapper.selectList(any())).thenReturn(List.of(folder(1, 0, "工作"), folder(2, 1, "前端")));
        SpaceTag tag = new SpaceTag();
        tag.setId(5L);
        tag.setName("框架");
        when(tagMapper.selectList(any())).thenReturn(List.of(tag));
        SpaceBookmarkTag rel = new SpaceBookmarkTag();
        rel.setBookmarkId(7L);
        rel.setTagId(5L);
        when(bookmarkTagMapper.selectList(any())).thenReturn(List.of(rel));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportJson("all", null, true, out);

        com.fasterxml.jackson.databind.JsonNode doc = new com.fasterxml.jackson.databind.ObjectMapper().readTree(out.toByteArray());
        assertEquals("bookmarks", doc.get("kind").asText());
        com.fasterxml.jackson.databind.JsonNode row = doc.get("bookmarks").get(0);
        assertEquals("7", row.get("id").asText());
        assertEquals("工作/前端", row.get("folderPath").asText());
        assertEquals("周会前看", row.get("remark").asText());
        assertEquals("archived", row.get("status").asText());
        assertEquals("框架", row.get("tags").get(0).asText());
        assertEquals(2, doc.get("folders").size());
        assertTrue(doc.get("scope").get("includeArchived").asBoolean());
    }
}
