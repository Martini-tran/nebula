package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.entity.SpaceBookmark;
import com.nebula.space.entity.SpaceBookmarkFolder;
import com.nebula.space.entity.SpaceBookmarkTag;
import com.nebula.space.entity.SpaceTag;
import com.nebula.space.mapper.SpaceBookmarkFolderMapper;
import com.nebula.space.mapper.SpaceBookmarkMapper;
import com.nebula.space.mapper.SpaceBookmarkTagMapper;
import com.nebula.space.mapper.SpaceTagMapper;
import com.nebula.space.reading.ArticleExtractor;
import com.nebula.space.reading.ArticleFetcher;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 书签整理的几个服务端操作：查重、访问计数、补标题，目录删除的三种处置，标签合并
 */
class SpaceBookmarkOrganizeTest {

    private static final Long ME = 42L;

    private SpaceBookmarkMapper bookmarkMapper;
    private SpaceBookmarkFolderMapper folderMapper;
    private SpaceBookmarkTagMapper bookmarkTagMapper;
    private SpaceTagMapper tagMapper;
    private ArticleFetcher fetcher;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : List.of(SpaceBookmark.class, SpaceBookmarkFolder.class, SpaceBookmarkTag.class, SpaceTag.class)) {
            TableInfoHelper.initTableInfo(assistant, c);
        }
    }

    @BeforeEach
    void setUp() {
        bookmarkMapper = mock(SpaceBookmarkMapper.class);
        folderMapper = mock(SpaceBookmarkFolderMapper.class);
        bookmarkTagMapper = mock(SpaceBookmarkTagMapper.class);
        tagMapper = mock(SpaceTagMapper.class);
        fetcher = mock(ArticleFetcher.class);
        UserContext.set(ME, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private SpaceBookmarkAdminServiceImpl bookmarks() {
        return new SpaceBookmarkAdminServiceImpl(bookmarkMapper, folderMapper, bookmarkTagMapper, tagMapper, fetcher);
    }

    private SpaceBookmark bookmark(String title, String description) {
        SpaceBookmark b = new SpaceBookmark();
        b.setId(7L);
        b.setUserId(ME);
        b.setUrl("https://www.vuejs.org/guide/");
        b.setDomain("www.vuejs.org");
        b.setTitle(title);
        b.setDescription(description);
        when(bookmarkMapper.selectById(7L)).thenReturn(b);
        return b;
    }

    @SuppressWarnings("unchecked")
    private String lastUpdateSql() {
        ArgumentCaptor<Wrapper<SpaceBookmark>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(bookmarkMapper).update(any(), captor.capture());
        return ((LambdaUpdateWrapper<SpaceBookmark>) captor.getValue()).getSqlSet();
    }

    // ----------------------------------------------------------------- 书签

    @Test
    void placeholderTitleIsTheDomainOrUrl() {
        assertTrue(SpaceBookmarkAdminServiceImpl.isPlaceholderTitle(bookmark("vuejs.org", null)));
        assertTrue(SpaceBookmarkAdminServiceImpl.isPlaceholderTitle(bookmark("www.vuejs.org", null)));
        assertTrue(SpaceBookmarkAdminServiceImpl.isPlaceholderTitle(bookmark(" ", null)));
        assertTrue(SpaceBookmarkAdminServiceImpl.isPlaceholderTitle(bookmark("https://www.vuejs.org/guide/", null)));
        assertFalse(SpaceBookmarkAdminServiceImpl.isPlaceholderTitle(bookmark("Vue 指南", null)));
    }

    @Test
    void fillMetaReplacesOnlyAutoTitleAndEmptyDescription() {
        bookmark("vuejs.org", null);
        when(fetcher.fetch("https://www.vuejs.org/guide/")).thenReturn(new ArticleExtractor.Article("Vue.js 指南", "渐进式框架", null, 0));

        bookmarks().fillMeta(7L);

        String sql = lastUpdateSql();
        assertTrue(sql.contains("title=") && sql.contains("description="), sql);
    }

    @Test
    void fillMetaKeepsWhatTheUserWrote() {
        bookmark("我的 Vue 笔记", "自己写的");
        when(fetcher.fetch(any())).thenReturn(new ArticleExtractor.Article("Vue.js 指南", "渐进式框架", null, 0));
        bookmarks().fillMeta(7L);
        verify(bookmarkMapper, never()).update(any(), any());
    }

    @Test
    void fillMetaLeavesItWhenThePageWontOpen() {
        bookmark("vuejs.org", null);
        bookmarks().fillMeta(7L);
        verify(bookmarkMapper, never()).update(any(), any());
    }

    @Test
    void visitCountsWithoutTouchingUpdateTime() {
        bookmark("Vue", null);
        bookmarks().recordVisit(7L);
        String sql = lastUpdateSql();
        assertTrue(sql.contains("visit_count = COALESCE(visit_count, 0) + 1"), sql);
        assertTrue(sql.contains("update_time = update_time"), sql);
        assertTrue(sql.contains("last_visit_time="), sql);
    }

    @Test
    void visitingSomeoneElsesBookmarkIsRefused() {
        bookmark("Vue", null).setUserId(99L);
        BizException e = assertThrows(BizException.class, () -> bookmarks().recordVisit(7L));
        assertEquals(HttpStatus.FORBIDDEN, e.getCode());
    }

    @Test
    void duplicateLooksUpByNormalizedUrlHash() {
        assertEquals(null, bookmarks().findDuplicate("  ", null));
        verify(bookmarkMapper, never()).selectOne(any());
        bookmarks().findDuplicate("HTTPS://WWW.VueJS.org/guide/#intro", 3L);
        verify(bookmarkMapper).selectOne(any());
    }

    // ----------------------------------------------------------------- 目录

    private SpaceBookmarkFolderAdminServiceImpl folders() {
        return new SpaceBookmarkFolderAdminServiceImpl(folderMapper, bookmarkMapper, bookmarkTagMapper);
    }

    private SpaceBookmarkFolder folder(long id, long parentId, String ancestors, String name) {
        SpaceBookmarkFolder f = new SpaceBookmarkFolder();
        f.setId(id);
        f.setUserId(ME);
        f.setParentId(parentId);
        f.setAncestors(ancestors);
        f.setLevel(ancestors.split(",").length);
        f.setName(name);
        when(folderMapper.selectById(id)).thenReturn(f);
        return f;
    }

    @Test
    void moveUpLiftsChildrenAndBookmarksOneLevel() {
        folder(1, 0, "0", "学习");
        folder(10, 1, "0,1", "前端");
        SpaceBookmarkFolder child = folder(11, 10, "0,1,10", "Vue");
        // 第一次查子目录，之后刷新后代时没有更深的
        when(folderMapper.selectList(any())).thenReturn(List.of(child), List.of());
        when(bookmarkMapper.update(any(), any())).thenReturn(3);

        int moved = folders().delete(10L, "moveUp");

        assertEquals(3, moved);
        verify(folderMapper).deleteById(10L);
        assertEquals(1L, child.getParentId());
        assertEquals("0,1", child.getAncestors());
        assertEquals(2, child.getLevel());
        verify(folderMapper).updateById(child);
    }

    @Test
    void moveUpStopsWhenAChildNameClashes() {
        folder(1, 0, "0", "学习");
        folder(10, 1, "0,1", "前端");
        SpaceBookmarkFolder child = folder(11, 10, "0,1,10", "Vue");
        when(folderMapper.selectList(any())).thenReturn(List.of(child));
        when(folderMapper.selectCount(any())).thenReturn(1L);
        BizException e = assertThrows(BizException.class, () -> folders().delete(10L, "moveUp"));
        assertEquals(HttpStatus.CONFLICT, e.getCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    void uncategorizeEmptiesTheWholeSubtree() {
        folder(10, 0, "0", "前端");
        SpaceBookmarkFolder vue = folder(11, 10, "0,10", "Vue");
        SpaceBookmarkFolder pinia = folder(12, 11, "0,10,11", "Pinia");
        when(folderMapper.selectList(any())).thenReturn(List.of(vue, pinia));

        folders().delete(10L, "uncategorize");

        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(folderMapper).deleteByIds(ids.capture());
        assertEquals(List.of(10L, 11L, 12L), List.copyOf(ids.getValue()));
        verify(bookmarkMapper, never()).deleteByIds(any(Collection.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void cascadeDeletesBookmarksAndTheirTags() {
        folder(10, 0, "0", "前端");
        SpaceBookmarkFolder vue = folder(11, 10, "0,10", "Vue");
        when(folderMapper.selectList(any())).thenReturn(List.of(vue));
        SpaceBookmark a = new SpaceBookmark();
        a.setId(100L);
        SpaceBookmark b = new SpaceBookmark();
        b.setId(101L);
        when(bookmarkMapper.selectList(any())).thenReturn(List.of(a, b));

        assertEquals(2, folders().delete(10L, "cascade"));

        verify(bookmarkTagMapper).delete(any(Wrapper.class));
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(bookmarkMapper).deleteByIds(ids.capture());
        assertEquals(List.of(100L, 101L), List.copyOf(ids.getValue()));
        verify(folderMapper).deleteByIds(List.of(10L, 11L));
    }

    @Test
    void unknownStrategyIsRejected() {
        folder(10, 0, "0", "前端");
        BizException e = assertThrows(BizException.class, () -> folders().delete(10L, "shred"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
    }

    // ----------------------------------------------------------------- 标签

    private SpaceTag tag(long id, long userId) {
        SpaceTag t = new SpaceTag();
        t.setId(id);
        t.setUserId(userId);
        when(tagMapper.selectById(id)).thenReturn(t);
        return t;
    }

    private static SpaceBookmarkTag rel(long bookmarkId, long tagId) {
        SpaceBookmarkTag r = new SpaceBookmarkTag();
        r.setBookmarkId(bookmarkId);
        r.setTagId(tagId);
        return r;
    }

    @SuppressWarnings("unchecked")
    @Test
    void mergeMovesBookmarksWithoutDoublingThenDeletesTheTag() {
        tag(1, ME);
        tag(2, ME);
        // 先查目标标签已有的，再查被合并的
        when(bookmarkTagMapper.selectList(any())).thenReturn(List.of(rel(100, 2)), List.of(rel(100, 1), rel(101, 1)));
        SpaceTagAdminServiceImpl tags = new SpaceTagAdminServiceImpl(tagMapper, bookmarkTagMapper);

        assertEquals(1, tags.merge(1L, 2L));

        ArgumentCaptor<SpaceBookmarkTag> inserted = ArgumentCaptor.forClass(SpaceBookmarkTag.class);
        verify(bookmarkTagMapper, times(1)).insert(inserted.capture());
        assertEquals(101L, inserted.getValue().getBookmarkId());
        assertEquals(2L, inserted.getValue().getTagId());
        verify(tagMapper).deleteById(1L);
    }

    @Test
    void mergeRefusesSameOrForeignTags() {
        tag(1, ME);
        tag(3, 99L);
        SpaceTagAdminServiceImpl tags = new SpaceTagAdminServiceImpl(tagMapper, bookmarkTagMapper);
        assertThrows(BizException.class, () -> tags.merge(1L, 1L));
        BizException e = assertThrows(BizException.class, () -> tags.merge(1L, 3L));
        assertEquals(HttpStatus.FORBIDDEN, e.getCode());
        verify(tagMapper, never()).deleteById(any(Long.class));
    }
}
