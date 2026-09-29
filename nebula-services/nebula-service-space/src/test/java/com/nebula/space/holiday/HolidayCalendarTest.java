package com.nebula.space.holiday;

import com.nebula.space.vo.me.HolidayYearVO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HolidayCalendarTest {

    private static Map<String, HolidayYearVO.Day> byDate(HolidayYearVO year) {
        return year.getDays().stream().collect(Collectors.toMap(HolidayYearVO.Day::getDate, Function.identity()));
    }

    @Test
    void loads2026AsAnnounced() throws IOException {
        HolidayYearVO y = new HolidayCalendar().year(2026);
        // 放假 3+9+3+5+3+3+7 天，调休上班 1+2+1+2 天，不重叠
        assertEquals(33, y.getDays().stream().filter(HolidayYearVO.Day::isOff).count());
        assertEquals(6, y.getDays().stream().filter(d -> !d.isOff()).count());
        Map<String, HolidayYearVO.Day> days = byDate(y);
        assertEquals(39, days.size());
        assertEquals("春节", days.get("2026-02-15").getName());
        assertTrue(days.get("2026-02-23").isOff());
        assertFalse(days.get("2026-02-28").isOff());
        assertFalse(days.get("2026-01-04").isOff());
        assertTrue(days.get("2026-10-07").isOff());
        assertTrue(y.getSource().contains("2026"));
    }

    @Test
    void loads2025AsAnnounced() throws IOException {
        Map<String, HolidayYearVO.Day> days = byDate(new HolidayCalendar().year(2025));
        assertEquals(33, days.size());
        assertTrue(days.get("2025-10-08").isOff());
        assertFalse(days.get("2025-10-11").isOff());
        assertFalse(days.get("2025-04-27").isOff());
    }

    @Test
    void unknownYearIsEmpty() throws IOException {
        assertTrue(new HolidayCalendar().year(2030).getDays().isEmpty());
    }

    @Test
    void reversedRangeIsRejected() {
        HolidayCalendar.Holiday h = new HolidayCalendar.Holiday();
        h.setName("春节");
        h.setFrom("2026-02-23");
        h.setTo("2026-02-15");
        HolidayCalendar.YearFile file = new HolidayCalendar.YearFile();
        file.setYear(2026);
        file.setHolidays(List.of(h));
        assertThrows(IllegalStateException.class, () -> HolidayCalendar.expand(file, "2026.json"));
    }
}
