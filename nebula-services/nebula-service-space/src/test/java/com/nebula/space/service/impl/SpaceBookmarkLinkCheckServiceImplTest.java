package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.context.UserContext;
import com.nebula.space.bookmark.LinkChecker;
import com.nebula.space.dto.admin.BookmarkLinkCheckRequest;
import com.nebula.space.entity.SpaceBookmark;
import com.nebula.space.mapper.SpaceBookmarkMapper;
import com.nebula.space.vo.admin.BookmarkLinkCheckVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceBookmarkLinkCheckServiceImplTest {

    private SpaceBookmarkMapper bookmarkMapper;
    private LinkChecker linkChecker;
    private SpaceBookmarkLinkCheckServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SpaceBookmark.class);
    }

    @BeforeEach
    void setUp() {
        bookmarkMapper = mock(SpaceBookmarkMapper.class);
        linkChecker = mock(LinkChecker.class);
        service = new SpaceBookmarkLinkCheckServiceImpl(bookmarkMapper, linkChecker);
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static SpaceBookmark bookmark(long id, int status, String url) {
        SpaceBookmark b = new SpaceBookmark();
        b.setId(id);
        b.setUserId(42L);
        b.setStatus(status);
        b.setUrl(url);
        return b;
    }

    private static BookmarkLinkCheckRequest req(Long... ids) {
        BookmarkLinkCheckRequest req = new BookmarkLinkCheckRequest();
        req.setBookmarkIds(List.of(ids));
        return req;
    }

    @Test
    @SuppressWarnings("unchecked")
    void statusFollowsVerdictAndArchivedAreSkipped() {
        // 查询已按 user_id 过滤：99 不是自己的，不在返回里
        when(bookmarkMapper.selectList(any())).thenReturn(List.of(
                bookmark(4, 2, "https://d.example/"),
                bookmark(3, 1, "https://c.example/"),
                bookmark(2, 2, "https://b.example/"),
                bookmark(1, 0, "https://a.example/")));
        Map<String, LinkChecker.Outcome> outcomes = Map.of(
                "https://a.example/", new LinkChecker.Outcome(LinkChecker.Verdict.DEAD, "页面不存在（404）"),
                "https://b.example/", new LinkChecker.Outcome(LinkChecker.Verdict.ALIVE, null),
                "https://d.example/", new LinkChecker.Outcome(LinkChecker.Verdict.UNKNOWN, "访问超时"));
        when(linkChecker.checkAll(anyList())).thenAnswer(inv -> ((List<String>) inv.getArgument(0)).stream().map(outcomes::get).toList());
        when(bookmarkMapper.update(isNull(), any())).thenReturn(1);

        List<BookmarkLinkCheckVO> result = service.check(req(1L, 2L, 3L, 4L, 99L, 1L));

        verify(linkChecker).checkAll(List.of("https://a.example/", "https://b.example/", "https://d.example/"));
        assertEquals(List.of(1L, 2L, 3L, 4L), result.stream().map(BookmarkLinkCheckVO::getId).toList());

        assertEquals("dead", result.get(0).getVerdict());
        assertEquals(2, result.get(0).getStatus());
        assertTrue(result.get(0).getChanged());
        assertEquals("页面不存在（404）", result.get(0).getReason());

        assertEquals("alive", result.get(1).getVerdict());
        assertEquals(0, result.get(1).getStatus());
        assertTrue(result.get(1).getChanged());

        assertEquals("skipped", result.get(2).getVerdict());
        assertEquals(1, result.get(2).getStatus());

        assertEquals("unknown", result.get(3).getVerdict());
        assertEquals(2, result.get(3).getStatus());
        assertFalse(result.get(3).getChanged());

        ArgumentCaptor<LambdaUpdateWrapper<SpaceBookmark>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(bookmarkMapper, times(3)).update(isNull(), captor.capture());
        List<LambdaUpdateWrapper<SpaceBookmark>> updates = captor.getAllValues();
        // 检查不改 update_time；检查期间被归档的不写
        updates.forEach(u -> {
            assertTrue(u.getSqlSet().contains("update_time = update_time"), u.getSqlSet());
            assertTrue(u.getSqlSegment().contains("status <>"), u.getSqlSegment());
        });
        assertTrue(updates.get(0).getSqlSet().contains("check_result"));
        assertTrue(updates.get(0).getParamNameValuePairs().containsValue("页面不存在（404）"));
        // 已失效的这次没查清：保留原来的失效原因
        assertFalse(updates.get(2).getSqlSet().contains("check_result"), updates.get(2).getSqlSet());
    }

    @Test
    void bookmarkArchivedDuringCheckIsReportedSkipped() {
        when(bookmarkMapper.selectList(any())).thenReturn(List.of(bookmark(1, 0, "https://a.example/")));
        when(linkChecker.checkAll(anyList())).thenReturn(List.of(new LinkChecker.Outcome(LinkChecker.Verdict.DEAD, "域名无法解析")));
        when(bookmarkMapper.update(isNull(), any())).thenReturn(0);

        List<BookmarkLinkCheckVO> result = service.check(req(1L));

        assertEquals("skipped", result.get(0).getVerdict());
        assertFalse(result.get(0).getChanged());
    }
}
