package com.nebula.space.holiday;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.space.vo.me.HolidayYearVO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 法定节假日与调休：每年国务院公告一次，按年放在 classpath:space/holidays/{year}.json 里
 *
 * <p>文件格式：{year, source, holidays: [{name, from, to, workdays: [调休上班的日子]}]}，
 * 启动时读进来展开成逐日。新一年公告发布后加一个文件即可；文件写错（日期不在当年、起止颠倒）启动时直接报错。</p>
 */
@Slf4j
@Component
public class HolidayCalendar {

    static final String LOCATION = "classpath*:space/holidays/*.json";

    private final Map<Integer, HolidayYearVO> years = new HashMap<>();

    public HolidayCalendar() throws IOException {
        this(new PathMatchingResourcePatternResolver().getResources(LOCATION));
    }

    HolidayCalendar(Resource[] resources) throws IOException {
        ObjectMapper json = new ObjectMapper();
        for (Resource r : resources) {
            try (InputStream in = r.getInputStream()) {
                HolidayYearVO year = expand(json.readValue(in, YearFile.class), r.getFilename());
                years.put(year.getYear(), year);
            }
        }
        log.info("法定节假日已加载：{}", years.keySet().stream().sorted().toList());
    }

    /**
     * 某年的安排；没收录的年份 days 为空
     */
    public HolidayYearVO year(int year) {
        HolidayYearVO found = years.get(year);
        return found != null ? found : new HolidayYearVO(year, "", List.of());
    }

    static HolidayYearVO expand(YearFile file, String name) {
        List<HolidayYearVO.Day> days = new ArrayList<>();
        for (Holiday h : file.getHolidays()) {
            LocalDate from = LocalDate.parse(h.getFrom());
            LocalDate to = LocalDate.parse(h.getTo());
            if (to.isBefore(from) || from.getYear() != file.getYear() && to.getYear() != file.getYear()) {
                throw new IllegalStateException(name + "：" + h.getName() + " 的起止日期不对");
            }
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                days.add(new HolidayYearVO.Day(d.toString(), h.getName(), true));
            }
            for (String w : h.getWorkdays()) {
                days.add(new HolidayYearVO.Day(LocalDate.parse(w).toString(), h.getName(), false));
            }
        }
        days.sort(Comparator.comparing(HolidayYearVO.Day::getDate));
        return new HolidayYearVO(file.getYear(), file.getSource(), List.copyOf(days));
    }

    @Data
    static class YearFile {
        private int year;
        private String source;
        private List<Holiday> holidays = List.of();
    }

    @Data
    static class Holiday {
        private String name;
        private String from;
        private String to;
        private List<String> workdays = List.of();
    }
}
