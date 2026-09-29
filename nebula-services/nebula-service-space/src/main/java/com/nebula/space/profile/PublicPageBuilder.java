package com.nebula.space.profile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.space.dto.me.GoalKeyResult;
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
import com.nebula.space.service.impl.SpaceReadingServiceImpl;
import com.nebula.space.vo.me.ProfilePublicVO;
import com.nebula.space.vo.me.ProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 拼访客看到的公开主页：只取打开的区块，每块只带白名单字段
 *
 * <p>所有查询都按主人的 userId 过滤，设置里存的 ID 就算被改成别人的也查不出东西。
 * 设置页「以访客身份预览」也走这里，预览和访客看到的是同一份。</p>
 */
@Component
@RequiredArgsConstructor
public class PublicPageBuilder {

    static final int READING_LIMIT = 10;
    /**
     * 一个合集最多展示的书签数，防止把几千条的目录整个吐出去
     */
    static final int BOOKMARK_LIMIT = 300;
    private static final int BOOKMARK_NORMAL = 0;
    private static final String READ_DONE = "done";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<GoalKeyResult>> KR_LIST = new TypeReference<>() {
    };
    private static final Pattern HTTP = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAILTO = Pattern.compile("^mailto:\\S+@\\S+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s/]+@[^\\s/]+\\.[^\\s/]+$");

    private final SpaceBookmarkFolderMapper folderMapper;
    private final SpaceBookmarkMapper bookmarkMapper;
    private final SpaceReadingMapper readingMapper;
    private final SpaceReadingHighlightMapper highlightMapper;
    private final SpaceGoalMapper goalMapper;
    private final SpaceHabitMapper habitMapper;
    private final SpaceHabitLogMapper habitLogMapper;
    private final SpaceTaskMapper taskMapper;

    public ProfilePublicVO build(Long userId, String nickname, ProfileVO profile, LocalDate today) {
        List<String> on = profile.getBlocks().stream().filter(ProfileBlock::isOn).map(ProfileBlock::getKey).toList();
        Set<String> has = Set.copyOf(on);

        ProfilePublicVO page = new ProfilePublicVO();
        page.setHandle(profile.getHandle());
        page.setNickname(StringUtils.hasText(nickname) ? nickname : profile.getHandle());
        page.setBlocks(on);
        page.setBio(has.contains("intro") ? profile.getBio() : "");
        page.setLinks(has.contains("links") ? links(profile.getLinks()) : List.of());
        page.setNow(has.contains("now") ? profile.getNow() : "");
        page.setNowUpdated(has.contains("now") ? profile.getNowUpdated() : null);
        page.setCollections(has.contains("collections") ? collections(userId, profile.getCollections()) : List.of());
        page.setReading(has.contains("reading") ? reading(userId, profile.getHiddenReading()) : List.of());
        page.setGoals(has.contains("goals") ? goals(userId, today) : List.of());
        page.setQuotes(has.contains("quotes") ? quotes(userId, profile.getQuoteIds()) : List.of());
        return page;
    }

    // ----------------------------------------------------------------- 链接

    /**
     * 能放进 href 的链接：http(s) 网址或邮箱，挡掉 javascript: 之类
     */
    public static boolean isSafeLink(String url) {
        return url != null && (HTTP.matcher(url).matches() || isEmail(url));
    }

    /**
     * 邮箱在公开页上点一下才显示
     */
    static boolean isEmail(String url) {
        return MAILTO.matcher(url).matches() || (EMAIL.matcher(url).matches() && !url.regionMatches(true, 0, "http", 0, 4));
    }

    private static List<ProfilePublicVO.Link> links(List<ProfileLink> links) {
        return links.stream()
                .filter(l -> StringUtils.hasText(l.getLabel()) && isSafeLink(l.getUrl()))
                .map(l -> new ProfilePublicVO.Link(l.getLabel(), l.getUrl(), isEmail(l.getUrl())))
                .toList();
    }

    // ----------------------------------------------------------------- 书签合集

    private List<ProfilePublicVO.Collection> collections(Long userId, List<ProfileCollection> collections) {
        if (collections.isEmpty()) {
            return List.of();
        }
        List<Long> folderIds = collections.stream().map(ProfileCollection::getFolderId).filter(Objects::nonNull).distinct().toList();
        if (folderIds.isEmpty()) {
            return List.of();
        }
        Map<Long, SpaceBookmarkFolder> folders = folderMapper.selectList(
                new LambdaQueryWrapper<SpaceBookmarkFolder>()
                        .eq(SpaceBookmarkFolder::getUserId, userId)
                        .in(SpaceBookmarkFolder::getId, folderIds)
        ).stream().collect(Collectors.toMap(SpaceBookmarkFolder::getId, Function.identity()));
        if (folders.isEmpty()) {
            return List.of();
        }
        // 只要目录里直接放的正常书签；子目录要单独勾
        Map<Long, List<SpaceBookmark>> byFolder = bookmarkMapper.selectList(
                new LambdaQueryWrapper<SpaceBookmark>()
                        .select(SpaceBookmark::getId, SpaceBookmark::getFolderId, SpaceBookmark::getTitle, SpaceBookmark::getUrl,
                                SpaceBookmark::getDomain, SpaceBookmark::getCreateTime, SpaceBookmark::getUpdateTime)
                        .eq(SpaceBookmark::getUserId, userId)
                        .in(SpaceBookmark::getFolderId, folders.keySet())
                        .eq(SpaceBookmark::getStatus, BOOKMARK_NORMAL)
                        .orderByDesc(SpaceBookmark::getCreateTime)
        ).stream().collect(Collectors.groupingBy(SpaceBookmark::getFolderId, LinkedHashMap::new, Collectors.toList()));

        List<ProfilePublicVO.Collection> out = new ArrayList<>();
        for (ProfileCollection c : collections) {
            SpaceBookmarkFolder folder = c.getFolderId() == null ? null : folders.get(c.getFolderId());
            if (folder == null || out.stream().anyMatch(x -> x.getId().equals(String.valueOf(folder.getId())))) {
                continue;
            }
            // 浏览器导入的 chrome://、file:// 之类在别人那里打不开，也不该露出本机路径
            List<SpaceBookmark> list = byFolder.getOrDefault(folder.getId(), List.of()).stream()
                    .filter(b -> b.getUrl() != null && HTTP.matcher(b.getUrl()).matches())
                    .limit(BOOKMARK_LIMIT)
                    .toList();
            ProfilePublicVO.Collection vo = new ProfilePublicVO.Collection();
            vo.setId(String.valueOf(folder.getId()));
            vo.setTitle(StringUtils.hasText(c.getTitle()) ? c.getTitle() : folder.getName());
            vo.setDescription(c.getDescription() == null ? "" : c.getDescription());
            vo.setUpdated(list.stream()
                    .map(b -> b.getUpdateTime() != null ? b.getUpdateTime() : b.getCreateTime())
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .map(LocalDateTime::toLocalDate)
                    .orElse(null));
            vo.setBookmarks(list.stream()
                    .map(b -> new ProfilePublicVO.Bookmark(
                            StringUtils.hasText(b.getTitle()) ? b.getTitle() : b.getUrl(),
                            b.getUrl(),
                            StringUtils.hasText(b.getDomain()) ? b.getDomain() : SpaceReadingServiceImpl.domainOf(b.getUrl())))
                    .toList());
            out.add(vo);
        }
        return out;
    }

    // ----------------------------------------------------------------- 最近读完

    private List<ProfilePublicVO.Reading> reading(Long userId, List<Long> hidden) {
        // 只取标题、网址、读完时间；正文、划线、读后感都不碰
        return readingMapper.selectList(
                        new LambdaQueryWrapper<SpaceReading>()
                                .select(SpaceReading::getId, SpaceReading::getTitle, SpaceReading::getUrl, SpaceReading::getDoneTime)
                                .eq(SpaceReading::getUserId, userId)
                                .eq(SpaceReading::getReadStatus, READ_DONE)
                                .isNotNull(SpaceReading::getDoneTime)
                                .notIn(!hidden.isEmpty(), SpaceReading::getId, hidden)
                                .orderByDesc(SpaceReading::getDoneTime)
                                .last("limit " + READING_LIMIT)
                ).stream()
                .map(r -> new ProfilePublicVO.Reading(r.getTitle(), r.getUrl(), SpaceReadingServiceImpl.domainOf(r.getUrl()), r.getDoneTime().toLocalDate()))
                .toList();
    }

    // ----------------------------------------------------------------- 年度目标

    /**
     * 今年的目标名与进度；记账来源和单位是钱的不公开
     */
    private List<ProfilePublicVO.Goal> goals(Long userId, LocalDate today) {
        int year = today.getYear();
        return goalMapper.selectList(
                        new LambdaQueryWrapper<SpaceGoal>()
                                .eq(SpaceGoal::getUserId, userId)
                                .eq(SpaceGoal::getGoalYear, year)
                                .orderByAsc(SpaceGoal::getSortOrder)
                                .orderByAsc(SpaceGoal::getId)
                ).stream()
                .filter(g -> !"ledger".equals(g.getSource()) && !"¥".equals(g.getUnit()))
                .map(g -> new ProfilePublicVO.Goal(g.getIcon(), g.getTitle(), pct(g, userId, year)))
                .toList();
    }

    /**
     * 与前端 goals/goalProgress.ts 的 computeProgress 同一套算法，只要进度比例
     */
    double pct(SpaceGoal g, Long userId, int year) {
        if ("milestone".equals(g.getKind())) {
            List<GoalKeyResult> krs = readKrs(g.getKrs());
            long done = krs.stream().filter(GoalKeyResult::isDone).count();
            return krs.isEmpty() ? 0 : (double) done / krs.size();
        }
        BigDecimal target = orZero(g.getTarget());
        if (target.signum() <= 0) {
            return 0;
        }
        double pct = value(g, userId, year).divide(target, 6, RoundingMode.HALF_UP).doubleValue();
        return Math.min(1, Math.max(0, pct));
    }

    private BigDecimal value(SpaceGoal g, Long userId, int year) {
        BigDecimal baseline = orZero(g.getBaseline());
        BigDecimal factor = g.getFactor() == null ? BigDecimal.ONE : g.getFactor();
        LocalDateTime from = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime to = from.plusYears(1);
        String source = g.getSource() == null ? "manual" : g.getSource();
        switch (source) {
            case "habit" -> {
                if (g.getSourceId() == null) {
                    return baseline;
                }
                SpaceHabit habit = habitMapper.selectOne(
                        new LambdaQueryWrapper<SpaceHabit>()
                                .eq(SpaceHabit::getId, g.getSourceId())
                                .eq(SpaceHabit::getUserId, userId)
                );
                List<SpaceHabitLog> logs = habitLogMapper.selectList(
                        new LambdaQueryWrapper<SpaceHabitLog>()
                                .eq(SpaceHabitLog::getUserId, userId)
                                .eq(SpaceHabitLog::getHabitId, g.getSourceId())
                                .ge(SpaceHabitLog::getLogDate, from.toLocalDate())
                                .lt(SpaceHabitLog::getLogDate, to.toLocalDate())
                );
                // 勾选型按达标次数，计数 / 时长型按数值
                boolean check = habit != null && "check".equals(habit.getKind());
                int threshold = habit == null || habit.getTarget() == null ? 1 : habit.getTarget();
                long sum = logs.stream()
                        .mapToLong(l -> {
                            int v = l.getValue() == null ? 0 : l.getValue();
                            return check ? (v >= threshold ? 1 : 0) : v;
                        })
                        .sum();
                return baseline.add(factor.multiply(BigDecimal.valueOf(sum)));
            }
            case "reading" -> {
                long done = readingMapper.selectCount(
                        new LambdaQueryWrapper<SpaceReading>()
                                .eq(SpaceReading::getUserId, userId)
                                .eq(SpaceReading::getReadStatus, READ_DONE)
                                .ge(SpaceReading::getDoneTime, from)
                                .lt(SpaceReading::getDoneTime, to)
                );
                return baseline.add(factor.multiply(BigDecimal.valueOf(done)));
            }
            case "task_list" -> {
                if (g.getSourceId() == null) {
                    return baseline;
                }
                long done = taskMapper.selectCount(
                        new LambdaQueryWrapper<SpaceTask>()
                                .eq(SpaceTask::getUserId, userId)
                                .eq(SpaceTask::getListId, g.getSourceId())
                                .eq(SpaceTask::getDone, 1)
                                .ge(SpaceTask::getDoneTime, from)
                                .lt(SpaceTask::getDoneTime, to)
                );
                return baseline.add(factor.multiply(BigDecimal.valueOf(done)));
            }
            default -> {
                return orZero(g.getManualValue());
            }
        }
    }

    // ----------------------------------------------------------------- 摘录精选

    /**
     * 只给划线原文和出自哪篇，批注永远不出去；按挑选的顺序
     */
    private List<ProfilePublicVO.Quote> quotes(Long userId, List<Long> quoteIds) {
        if (quoteIds.isEmpty()) {
            return List.of();
        }
        Map<Long, SpaceReadingHighlight> byId = highlightMapper.selectList(
                new LambdaQueryWrapper<SpaceReadingHighlight>()
                        .select(SpaceReadingHighlight::getId, SpaceReadingHighlight::getItemId, SpaceReadingHighlight::getQuote)
                        .eq(SpaceReadingHighlight::getUserId, userId)
                        .in(SpaceReadingHighlight::getId, quoteIds)
        ).stream().collect(Collectors.toMap(SpaceReadingHighlight::getId, Function.identity()));
        if (byId.isEmpty()) {
            return List.of();
        }
        List<Long> itemIds = byId.values().stream().map(SpaceReadingHighlight::getItemId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> titles = itemIds.isEmpty() ? Map.of() : readingMapper.selectList(
                new LambdaQueryWrapper<SpaceReading>()
                        .select(SpaceReading::getId, SpaceReading::getTitle)
                        .eq(SpaceReading::getUserId, userId)
                        .in(SpaceReading::getId, itemIds)
        ).stream().collect(Collectors.toMap(SpaceReading::getId, r -> r.getTitle() == null ? "" : r.getTitle()));
        return quoteIds.stream()
                .distinct()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(h -> new ProfilePublicVO.Quote(h.getQuote(), titles.getOrDefault(h.getItemId(), "")))
                .toList();
    }

    // ----------------------------------------------------------------- 工具

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static List<GoalKeyResult> readKrs(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return JSON.readValue(json, KR_LIST);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
