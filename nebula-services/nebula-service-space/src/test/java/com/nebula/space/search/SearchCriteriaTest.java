package com.nebula.space.search;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.space.entity.SpaceTask;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchCriteriaTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SpaceTask.class);
    }

    @Test
    void parsesEveryKindOfToken() {
        SearchCriteria q = SearchCriteria.parse("#工作 @张工 is:open after:9-20 before:2026-10-1 \"同名 目录\" Hello", TODAY);
        assertNull(q.getKind());
        assertEquals(List.of("工作"), q.getTags());
        assertEquals(List.of("张工"), q.getPeople());
        assertEquals(List.of("张工"), q.getPeopleNames());
        assertEquals(List.of("open"), q.getStates());
        assertEquals("2026-09-20", q.getAfter());
        assertEquals("2026-10-01", q.getBefore());
        assertEquals(LocalDate.of(2026, 10, 1), q.beforeDate());
        assertEquals(List.of("同名 目录", "hello"), q.getTerms());
    }

    @Test
    void prefixPicksKindOnlyOnce() {
        SearchCriteria q = SearchCriteria.parse("b:Vue n:y", TODAY);
        assertEquals(SearchCriteria.BOOKMARK, q.getKind());
        assertEquals(List.of("vue", "n:y"), q.getTerms());
        assertTrue(q.wants(SearchCriteria.BOOKMARK));
        assertFalse(q.wants(SearchCriteria.NOTE));

        SearchCriteria bare = SearchCriteria.parse("t： 周报", TODAY);
        assertEquals(SearchCriteria.TASK, bare.getKind());
        assertEquals(List.of("周报"), bare.getTerms());
    }

    @Test
    void unknownValuesStayAsTerms() {
        SearchCriteria q = SearchCriteria.parse("is:later after:明天 # @", TODAY);
        assertEquals(List.of("is:later", "after:明天", "#", "@"), q.getTerms());
        assertTrue(q.getStates().isEmpty());
        assertNull(q.getAfter());
    }

    @Test
    void impossibleDayIsADateButNotFiltered() {
        SearchCriteria q = SearchCriteria.parse("after:2-30", TODAY);
        assertEquals("2026-02-30", q.getAfter());
        assertNull(q.afterDate());
        assertTrue(q.getTerms().isEmpty());
        assertFalse(q.isEmpty());
    }

    @Test
    void fullWidthSpaceSeparatesAndQuotesMayBeOpen() {
        assertEquals(List.of("周报", "张工"), SearchCriteria.parse("周报　张工", TODAY).getTerms());
        assertEquals(List.of("半开 引号"), SearchCriteria.parse("\"半开 引号", TODAY).getTerms());
        assertEquals(List.of("学习"), SearchCriteria.parse("＃学习", TODAY).getTags());
        assertTrue(SearchCriteria.parse("  \"\"  ", TODAY).isEmpty());
        assertTrue(SearchCriteria.parse(null, TODAY).isEmpty());
    }

    @Test
    void escapesLikeWildcards() {
        assertEquals("50\\%\\_a\\\\b", SearchCriteria.escapeLike("50%_a\\b"));
    }

    @Test
    void everyTermMustHitOneOfTheColumns() {
        LambdaQueryWrapper<SpaceTask> w = new LambdaQueryWrapper<>();
        SearchCriteria.matchTerms(w, List.of("a", "b%"), SpaceTask::getTitle, SpaceTask::getNote);
        String sql = w.getSqlSegment();
        assertEquals(2, sql.split("\\) AND \\(").length, sql);
        assertEquals(4, sql.split(" LIKE ").length - 1, sql);
        assertEquals(2, sql.split(" OR ").length - 1, sql);
        assertTrue(w.getParamNameValuePairs().containsValue("%b\\%%"), w.getParamNameValuePairs().toString());
    }
}
