package com.nebula.space.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.Getter;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全局搜索的查询条件：前端 utils/searchQuery.ts 的 parseSearch 原样移植，两边必须一致
 *
 * <pre>
 *   b: vue        只搜书签（n: 随手记、t: 任务、m: 会议、l: 稍后读与划线、r: 周报、p: 人物）
 *   #工作          按标签或清单过滤
 *   &#64;张工          负责人 / 来源 / 人物
 *   is:open       未完成（is:done、is:overdue）
 *   after:9-20    该日期之后（before: 同理，含当天）
 *   "同名目录"     精确短语
 * </pre>
 *
 * <p>服务端只负责从库里召回候选：每个条件都按「宽于前端」来查（LIKE 不分大小写、JSON 列整列匹配），
 * 前端拿到候选后按同一套规则精确过滤、打分、排序。</p>
 */
@Getter
public final class SearchCriteria {

    public static final String TASK = "task";
    public static final String NOTE = "note";
    public static final String BOOKMARK = "bookmark";
    public static final String MEETING = "meeting";
    public static final String READING = "reading";
    public static final String REPORT = "report";
    public static final String PERSON = "person";

    private static final Map<String, String> PREFIX_KIND = Map.of(
            "b", BOOKMARK, "n", NOTE, "t", TASK, "m", MEETING, "l", READING, "r", REPORT, "p", PERSON);

    /**
     * 和前端 JS 的 \s 一样，全角空格等 Unicode 空白也算分隔
     */
    private static final Pattern TOKEN = Pattern.compile("\"([^\"]*)\"?|(\\S+)", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern PREFIX = Pattern.compile("^([bntmlrp])[:：](.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern KEY_VALUE = Pattern.compile("^(is|after|before)[:：](.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DAY = Pattern.compile("^(?:(\\d{4})[-/.])?(\\d{1,2})[-/.](\\d{1,2})$");

    private String kind;
    private final List<String> terms = new ArrayList<>();
    private final List<String> tags = new ArrayList<>();
    private final List<String> people = new ArrayList<>();
    private final List<String> states = new ArrayList<>();
    /**
     * after: / before: 的原样日期串（YYYY-MM-DD，可能是 2-30 这种不存在的日子，和前端一样按字符串比）
     */
    private String after;
    private String before;
    /**
     * &#64;某人 连同人物卡里登记的其他叫法，由搜索服务查完人物卡后填入；没有别名时就是 people 本身
     */
    private List<String> peopleNames = List.of();

    private SearchCriteria() {
    }

    public static SearchCriteria parse(String input, LocalDate today) {
        SearchCriteria q = new SearchCriteria();
        if (input == null) {
            return q;
        }
        Matcher m = TOKEN.matcher(input);
        while (m.find()) {
            if (m.group(1) != null) {
                String phrase = m.group(1).strip();
                if (!phrase.isEmpty()) {
                    q.terms.add(lower(phrase));
                }
                continue;
            }
            String token = m.group(2);
            Matcher prefix = PREFIX.matcher(token);
            if (prefix.matches() && q.kind == null) {
                q.kind = PREFIX_KIND.get(lower(prefix.group(1)));
                if (!prefix.group(2).isEmpty()) {
                    q.terms.add(lower(prefix.group(2)));
                }
                continue;
            }
            if (token.length() > 1 && (token.charAt(0) == '#' || token.charAt(0) == '＃')) {
                q.tags.add(lower(token.substring(1)));
                continue;
            }
            if (token.length() > 1 && token.charAt(0) == '@') {
                q.people.add(lower(token.substring(1)));
                continue;
            }
            Matcher kv = KEY_VALUE.matcher(token);
            if (kv.matches()) {
                String key = lower(kv.group(1));
                String value = lower(kv.group(2));
                if ("is".equals(key) && ("open".equals(value) || "done".equals(value) || "overdue".equals(value))) {
                    q.states.add(value);
                    continue;
                }
                String day = "is".equals(key) ? null : parseDay(value, today);
                if (day != null) {
                    if ("after".equals(key)) {
                        q.after = day;
                    } else {
                        q.before = day;
                    }
                    continue;
                }
            }
            q.terms.add(lower(token));
        }
        q.peopleNames = List.copyOf(q.people);
        return q;
    }

    /**
     * 9-20、9/20、2026-9-20 → YYYY-MM-DD；认不出返回 null
     */
    static String parseDay(String raw, LocalDate today) {
        Matcher m = DAY.matcher(raw);
        if (!m.matches()) {
            return null;
        }
        String year = m.group(1) != null ? m.group(1) : String.valueOf(today.getYear());
        return year + "-" + pad(m.group(2)) + "-" + pad(m.group(3));
    }

    /**
     * 没有任何条件（前端此时显示最近打开，不会来查）
     */
    public boolean isEmpty() {
        return terms.isEmpty() && tags.isEmpty() && people.isEmpty() && states.isEmpty() && after == null && before == null;
    }

    public boolean wants(String k) {
        return kind == null || kind.equals(k);
    }

    public boolean hasState(String state) {
        return states.contains(state);
    }

    public boolean hasDateRange() {
        return after != null || before != null;
    }

    /**
     * after: 对应的日期；日子不存在（2-30）时不按日期筛，交给前端按字符串比
     */
    public LocalDate afterDate() {
        return toDate(after);
    }

    public LocalDate beforeDate() {
        return toDate(before);
    }

    public void setPeopleNames(List<String> names) {
        this.peopleNames = Collections.unmodifiableList(new ArrayList<>(names));
    }

    // ----------------------------------------------------------------- 拼 SQL

    /**
     * after: / before: 落在日期列上（含当天）
     */
    public <T> void inDateRange(LambdaQueryWrapper<T> wrapper, SFunction<T, ?> dateColumn) {
        LocalDate from = afterDate();
        LocalDate to = beforeDate();
        wrapper.ge(from != null, dateColumn, from).le(to != null, dateColumn, to);
    }

    /**
     * after: / before: 落在时间列上：before 当天整天都算
     */
    public <T> void inTimeRange(LambdaQueryWrapper<T> wrapper, SFunction<T, ?> timeColumn) {
        LocalDate from = afterDate();
        LocalDate to = beforeDate();
        wrapper.ge(from != null, timeColumn, from == null ? null : from.atStartOfDay())
                .lt(to != null, timeColumn, to == null ? null : to.plusDays(1).atStartOfDay());
    }

    /**
     * 每个词都要命中：词与词之间 AND，同一个词在几列里任一列出现即可
     */
    @SafeVarargs
    public static <T> void matchTerms(LambdaQueryWrapper<T> wrapper, List<String> terms, SFunction<T, ?>... columns) {
        for (String term : terms) {
            likeAny(wrapper, term, columns);
        }
    }

    /**
     * 这个词在几列里任一列出现
     */
    @SafeVarargs
    public static <T> void likeAny(LambdaQueryWrapper<T> wrapper, String word, SFunction<T, ?>... columns) {
        String escaped = escapeLike(word);
        wrapper.and(w -> {
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) {
                    w.or();
                }
                w.like(columns[i], escaped);
            }
        });
    }

    /**
     * 几个词里任一个在这一列出现
     */
    public static <T> void likeAnyWord(LambdaQueryWrapper<T> wrapper, SFunction<T, ?> column, List<String> words) {
        wrapper.and(w -> {
            for (int i = 0; i < words.size(); i++) {
                if (i > 0) {
                    w.or();
                }
                w.like(column, escapeLike(words.get(i)));
            }
        });
    }

    /**
     * LIKE 里的 % _ \ 按字面匹配
     */
    public static String escapeLike(String word) {
        StringBuilder sb = new StringBuilder(word.length() + 4);
        for (char c : word.toCharArray()) {
            if (c == '\\' || c == '%' || c == '_') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static LocalDate toDate(String ymd) {
        if (ymd == null) {
            return null;
        }
        try {
            return LocalDate.parse(ymd);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    private static String pad(String n) {
        return n.length() == 1 ? "0" + n : n;
    }
}
