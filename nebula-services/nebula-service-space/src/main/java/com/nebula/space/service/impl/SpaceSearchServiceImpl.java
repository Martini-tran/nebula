package com.nebula.space.service.impl;

import com.nebula.common.core.context.UserContext;
import com.nebula.space.search.SearchCriteria;
import com.nebula.space.service.SpaceBookmarkAdminService;
import com.nebula.space.service.SpaceMeetingService;
import com.nebula.space.service.SpaceNoteService;
import com.nebula.space.service.SpacePersonService;
import com.nebula.space.service.SpaceReadingService;
import com.nebula.space.service.SpaceReportService;
import com.nebula.space.service.SpaceSearchService;
import com.nebula.space.service.SpaceTaskListService;
import com.nebula.space.service.SpaceTaskService;
import com.nebula.space.vo.me.HighlightVO;
import com.nebula.space.vo.me.PersonVO;
import com.nebula.space.vo.me.ReadingItemVO;
import com.nebula.space.vo.me.SearchResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 全局搜索实现：解析查询后分发到各模块在库里召回
 *
 * <p>哪些条件让某个模块整个不参与，与前端 api/search.ts 各 searchXxx 开头的判断一致：
 * 随手记、书签不认 is: 与 &#64;；会议不认 #；稍后读不认 # 与 &#64;；日报周报只认词和日期；人物不认 is: 与日期。
 * 书签和书签列表接口一样要有 space:bookmark:list 权限，没有就不搜（前端原来调书签接口被拒时也是空）。
 * 每个模块按最近的在前截断，避免一个很宽的词把整库吐出去。</p>
 */
@Service
@RequiredArgsConstructor
public class SpaceSearchServiceImpl implements SpaceSearchService {

    /**
     * 输入最长多少字，再长的截掉
     */
    static final int MAX_INPUT = 200;
    static final int TASK_LIMIT = 200;
    static final int LIMIT = 100;
    static final int BOOKMARK_LIMIT = 60;
    static final int HIGHLIGHT_LIMIT = 60;
    static final String BOOKMARK_LIST = "space:bookmark:list";

    private final SpaceTaskService taskService;
    private final SpaceTaskListService taskListService;
    private final SpaceNoteService noteService;
    private final SpaceMeetingService meetingService;
    private final SpaceReportService reportService;
    private final SpaceReadingService readingService;
    private final SpacePersonService personService;
    private final SpaceBookmarkAdminService bookmarkService;

    @Override
    public SearchResultVO search(String q) {
        return search(q, LocalDate.now());
    }

    SearchResultVO search(String input, LocalDate today) {
        String text = input == null ? "" : input.strip();
        if (text.length() > MAX_INPUT) {
            text = text.substring(0, MAX_INPUT);
        }
        SearchCriteria q = SearchCriteria.parse(text, today);
        SearchResultVO out = new SearchResultVO();
        if (q.isEmpty()) {
            return out;
        }
        boolean noTags = q.getTags().isEmpty();
        boolean noPeople = q.getPeople().isEmpty();
        boolean noStates = q.getStates().isEmpty();

        // 先认出 @ 的是谁，任务和会议要连他的其他叫法一起查
        List<PersonVO> mentioned = noPeople ? List.of() : expandPeople(q);

        if (q.wants(SearchCriteria.TASK)) {
            out.setTasks(taskService.search(q, TASK_LIMIT));
            out.setLists(taskListService.list());
        }
        if (q.wants(SearchCriteria.NOTE) && noStates && noPeople) {
            out.setNotes(noteService.search(q, LIMIT));
        }
        if (q.wants(SearchCriteria.BOOKMARK) && noStates && noPeople && UserContext.hasPermission(BOOKMARK_LIST)) {
            out.setBookmarks(bookmarkService.search(q, BOOKMARK_LIMIT));
        }
        if (q.wants(SearchCriteria.MEETING) && noTags) {
            out.setMeetings(meetingService.search(q, LIMIT));
        }
        if (q.wants(SearchCriteria.READING) && noTags && noPeople) {
            reading(q, out);
        }
        if (q.wants(SearchCriteria.REPORT) && noTags && noPeople && noStates) {
            out.setReports(reportService.search(q, LIMIT));
        }
        List<PersonVO> people = q.wants(SearchCriteria.PERSON) && noStates && !q.hasDateRange()
                ? personService.search(q, LIMIT)
                : List.of();
        out.setPeople(union(people, mentioned));
        return out;
    }

    /**
     * 文章与划线；划线所属的文章没被召回时补上（不带正文），前端要显示「划线 · 文章标题」
     */
    private void reading(SearchCriteria q, SearchResultVO out) {
        List<ReadingItemVO> items = new ArrayList<>(readingService.search(q, LIMIT));
        if (q.getStates().isEmpty() && !q.getTerms().isEmpty()) {
            List<HighlightVO> marks = readingService.searchHighlights(q, HIGHLIGHT_LIMIT);
            Set<Long> have = items.stream().map(ReadingItemVO::getId).collect(Collectors.toSet());
            Set<Long> missing = marks.stream()
                    .map(HighlightVO::getItemId)
                    .filter(id -> id != null && !have.contains(id))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            items.addAll(readingService.listByIds(missing));
            out.setHighlights(marks);
        }
        out.setReading(items);
    }

    /**
     * &#64;张工：人物卡里有一个叫法与输入完全相同时，这张卡的所有叫法都算（与前端 namesFor 一致，取第一张对上的卡）
     *
     * @return 认出来的人物卡
     */
    private List<PersonVO> expandPeople(SearchCriteria q) {
        List<PersonVO> all = personService.list();
        Set<String> names = new LinkedHashSet<>();
        List<PersonVO> hit = new ArrayList<>();
        for (String who : q.getPeople()) {
            names.add(who);
            all.stream()
                    .filter(p -> namesOf(p).stream().anyMatch(n -> lower(n).equals(who)))
                    .findFirst()
                    .ifPresent(p -> {
                        namesOf(p).forEach(n -> names.add(lower(n)));
                        hit.add(p);
                    });
        }
        q.setPeopleNames(List.copyOf(names));
        return hit;
    }

    /**
     * 一个人的各种叫法：姓名、称呼、其他叫法，去空白后至少两个字（一个字太容易误中）
     */
    static List<String> namesOf(PersonVO p) {
        Stream<String> extra = p.getExtraNames() == null ? Stream.empty() : p.getExtraNames().stream();
        return Stream.concat(Stream.of(p.getName(), p.getAlias()), extra)
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(n -> n.length() >= 2)
                .distinct()
                .toList();
    }

    private static List<PersonVO> union(List<PersonVO> a, List<PersonVO> b) {
        Map<Long, PersonVO> byId = new LinkedHashMap<>();
        Stream.concat(a.stream(), b.stream()).forEach(p -> byId.putIfAbsent(p.getId(), p));
        return List.copyOf(byId.values());
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
