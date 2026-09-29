package com.nebula.space.profile;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.space.dto.me.ProfileBlock;
import com.nebula.space.dto.me.ProfileCollection;
import com.nebula.space.dto.me.ProfileLink;
import com.nebula.space.entity.SpaceBookmark;
import com.nebula.space.entity.SpaceBookmarkFolder;
import com.nebula.space.entity.SpaceGoal;
import com.nebula.space.entity.SpaceHabit;
import com.nebula.space.entity.SpaceHabitLog;
import com.nebula.space.entity.SpaceReading;
import com.nebula.space.entity.SpaceReadingHighlight;
import com.nebula.space.entity.SpaceTask;
import com.nebula.space.mapper.SpaceBookmarkFolderMapper;
import com.nebula.space.mapper.SpaceBookmarkMapper;
import com.nebula.space.mapper.SpaceGoalMapper;
import com.nebula.space.mapper.SpaceHabitLogMapper;
import com.nebula.space.mapper.SpaceHabitMapper;
import com.nebula.space.mapper.SpaceReadingHighlightMapper;
import com.nebula.space.mapper.SpaceReadingMapper;
import com.nebula.space.mapper.SpaceTaskMapper;
import com.nebula.space.vo.me.ProfilePublicVO;
import com.nebula.space.vo.me.ProfileVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicPageBuilderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    private SpaceBookmarkFolderMapper folderMapper;
    private SpaceBookmarkMapper bookmarkMapper;
    private SpaceReadingMapper readingMapper;
    private SpaceReadingHighlightMapper highlightMapper;
    private SpaceGoalMapper goalMapper;
    private SpaceHabitMapper habitMapper;
    private SpaceHabitLogMapper habitLogMapper;
    private SpaceTaskMapper taskMapper;
    private PublicPageBuilder builder;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : List.of(SpaceBookmark.class, SpaceBookmarkFolder.class, SpaceReading.class, SpaceReadingHighlight.class,
                SpaceGoal.class, SpaceHabit.class, SpaceHabitLog.class, SpaceTask.class)) {
            TableInfoHelper.initTableInfo(assistant, c);
        }
    }

    @BeforeEach
    void setUp() {
        folderMapper = mock(SpaceBookmarkFolderMapper.class);
        bookmarkMapper = mock(SpaceBookmarkMapper.class);
        readingMapper = mock(SpaceReadingMapper.class);
        highlightMapper = mock(SpaceReadingHighlightMapper.class);
        goalMapper = mock(SpaceGoalMapper.class);
        habitMapper = mock(SpaceHabitMapper.class);
        habitLogMapper = mock(SpaceHabitLogMapper.class);
        taskMapper = mock(SpaceTaskMapper.class);
        builder = new PublicPageBuilder(folderMapper, bookmarkMapper, readingMapper, highlightMapper,
                goalMapper, habitMapper, habitLogMapper, taskMapper);
    }

    private static ProfileVO profile(String... on) {
        ProfileVO p = new ProfileVO();
        p.setHandle("zhangsan");
        p.setBio("写代码的");
        p.setLinks(List.of());
        p.setNow("在跑步");
        p.setNowUpdated(TODAY);
        p.setBlocks(java.util.Arrays.stream(on).map(k -> ProfileBlock.of(k, true)).toList());
        p.setCollections(List.of());
        p.setHiddenReading(List.of());
        p.setQuoteIds(List.of());
        return p;
    }

    private static ProfileLink link(String label, String url) {
        ProfileLink l = new ProfileLink();
        l.setLabel(label);
        l.setUrl(url);
        return l;
    }

    private static SpaceGoal goal(String kind, String source, String target) {
        SpaceGoal g = new SpaceGoal();
        g.setKind(kind);
        g.setSource(source);
        g.setSourceId(11L);
        g.setTarget(new BigDecimal(target));
        g.setFactor(BigDecimal.ONE);
        g.setBaseline(BigDecimal.ZERO);
        g.setUnit("次");
        return g;
    }

    private static SpaceHabitLog log(int value) {
        SpaceHabitLog l = new SpaceHabitLog();
        l.setValue(value);
        return l;
    }

    private static SpaceBookmark bookmark(Long folderId, String title, String url) {
        SpaceBookmark b = new SpaceBookmark();
        b.setFolderId(folderId);
        b.setTitle(title);
        b.setUrl(url);
        b.setCreateTime(LocalDateTime.of(2026, 9, 1, 8, 0));
        return b;
    }

    @Test
    void closedBlocksStayEmptyAndAreNotQueried() {
        ProfilePublicVO page = builder.build(42L, "张三", profile("links"), TODAY);
        assertEquals("", page.getBio());
        assertEquals("", page.getNow());
        assertEquals(List.of("links"), page.getBlocks());
        assertTrue(page.getCollections().isEmpty());
        verify(readingMapper, never()).selectList(any(Wrapper.class));
        verify(goalMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void linksDropScriptsAndMaskEmails() {
        ProfileVO p = profile("links");
        p.setLinks(List.of(link("GitHub", "https://github.com/zs"), link("坏的", "javascript:alert(1)"),
                link("邮箱", "zs@example.com"), link("信", "mailto:zs@example.com")));
        List<ProfilePublicVO.Link> links = builder.build(42L, "张三", p, TODAY).getLinks();
        assertEquals(List.of("GitHub", "邮箱", "信"), links.stream().map(ProfilePublicVO.Link::getLabel).toList());
        assertEquals(List.of(false, true, true), links.stream().map(ProfilePublicVO.Link::isMasked).toList());
        assertFalse(PublicPageBuilder.isSafeLink("data:text/html,x"));
        assertFalse(PublicPageBuilder.isEmail("https://a@b.com"));
    }

    @Test
    void collectionsOnlyShowOwnFoldersAndWebLinks() {
        SpaceBookmarkFolder folder = new SpaceBookmarkFolder();
        folder.setId(5L);
        folder.setName("工具");
        when(folderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(folder));
        when(bookmarkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                bookmark(5L, "", "https://www.example.com/a"),
                bookmark(5L, "本机", "file:///C:/secret.txt")));
        ProfileCollection mine = new ProfileCollection();
        mine.setFolderId(5L);
        mine.setTitle("");
        ProfileCollection others = new ProfileCollection();
        others.setFolderId(9L);
        others.setTitle("别人的");
        ProfileVO p = profile("collections");
        p.setCollections(List.of(mine, others));

        List<ProfilePublicVO.Collection> cs = builder.build(42L, "张三", p, TODAY).getCollections();
        assertEquals(1, cs.size());
        assertEquals("5", cs.get(0).getId());
        assertEquals("工具", cs.get(0).getTitle());
        assertEquals(LocalDate.of(2026, 9, 1), cs.get(0).getUpdated());
        assertEquals(1, cs.get(0).getBookmarks().size());
        assertEquals("https://www.example.com/a", cs.get(0).getBookmarks().get(0).getTitle());
        assertEquals("example.com", cs.get(0).getBookmarks().get(0).getDomain());
    }

    @Test
    void goalsHideMoneyAndShowProgress() {
        SpaceGoal ledger = goal("metric", "ledger", "10000");
        SpaceGoal money = goal("metric", "manual", "5000");
        money.setUnit("¥");
        SpaceGoal manual = goal("metric", "manual", "4");
        manual.setTitle("读 4 本书");
        manual.setManualValue(new BigDecimal("1"));
        when(goalMapper.selectList(any(Wrapper.class))).thenReturn(List.of(ledger, money, manual));
        List<ProfilePublicVO.Goal> goals = builder.build(42L, "张三", profile("goals"), TODAY).getGoals();
        assertEquals(1, goals.size());
        assertEquals("读 4 本书", goals.get(0).getTitle());
        assertEquals(0.25, goals.get(0).getPct(), 1e-9);
    }

    @Test
    void milestoneCountsDoneKeyResults() {
        SpaceGoal g = goal("milestone", "manual", "0");
        g.setKrs("[{\"id\":\"a\",\"title\":\"一\",\"done\":true},{\"id\":\"b\",\"title\":\"二\",\"done\":false},{\"id\":\"c\",\"title\":\"三\",\"done\":true}]");
        assertEquals(2.0 / 3, builder.pct(g, 42L, 2026), 1e-6);
        g.setKrs("[]");
        assertEquals(0, builder.pct(g, 42L, 2026));
    }

    @Test
    void checkHabitCountsDaysThatHitTarget() {
        SpaceHabit habit = new SpaceHabit();
        habit.setKind("check");
        habit.setTarget(1);
        when(habitMapper.selectOne(any(Wrapper.class))).thenReturn(habit);
        when(habitLogMapper.selectList(any(Wrapper.class))).thenReturn(List.of(log(1), log(0), log(1)));
        assertEquals(0.2, builder.pct(goal("metric", "habit", "10"), 42L, 2026), 1e-9);
    }

    @Test
    void countHabitSumsValuesWithFactorAndBaseline() {
        SpaceHabit habit = new SpaceHabit();
        habit.setKind("count");
        habit.setTarget(5);
        when(habitMapper.selectOne(any(Wrapper.class))).thenReturn(habit);
        when(habitLogMapper.selectList(any(Wrapper.class))).thenReturn(List.of(log(3), log(4)));
        SpaceGoal g = goal("metric", "habit", "30");
        g.setFactor(new BigDecimal("2"));
        g.setBaseline(BigDecimal.ONE);
        assertEquals(0.5, builder.pct(g, 42L, 2026), 1e-9);
    }

    @Test
    void readingAndTaskListCountThisYearAndClampAtOne() {
        when(readingMapper.selectCount(any(Wrapper.class))).thenReturn(12L);
        assertEquals(1.0, builder.pct(goal("metric", "reading", "10"), 42L, 2026));
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(3L);
        assertEquals(0.3, builder.pct(goal("metric", "task_list", "10"), 42L, 2026), 1e-9);
        assertEquals(0, builder.pct(goal("metric", "reading", "0"), 42L, 2026));
    }

    @Test
    void quotesKeepPickedOrderAndCarryNoNotes() {
        SpaceReadingHighlight a = new SpaceReadingHighlight();
        a.setId(7L);
        a.setItemId(100L);
        a.setQuote("第一句");
        a.setNote("私人批注");
        SpaceReadingHighlight b = new SpaceReadingHighlight();
        b.setId(8L);
        b.setItemId(100L);
        b.setQuote("第二句");
        SpaceReading r = new SpaceReading();
        r.setId(100L);
        r.setTitle("长文");
        when(highlightMapper.selectList(any(Wrapper.class))).thenReturn(List.of(a, b));
        when(readingMapper.selectList(any(Wrapper.class))).thenReturn(List.of(r));
        ProfileVO p = profile("quotes");
        p.setQuoteIds(List.of(8L, 7L, 99L));
        List<ProfilePublicVO.Quote> quotes = builder.build(42L, "张三", p, TODAY).getQuotes();
        assertEquals(List.of("第二句", "第一句"), quotes.stream().map(ProfilePublicVO.Quote::getText).toList());
        assertEquals("长文", quotes.get(0).getSource());
    }
}
