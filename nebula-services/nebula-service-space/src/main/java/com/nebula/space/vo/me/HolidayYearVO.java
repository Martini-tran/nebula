package com.nebula.space.vo.me;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 一年的法定节假日安排（国务院公告），展开成逐日：放假的日子与调休上班的日子
 *
 * <p>当年安排还没公布（或没收录）时 days 为空，前端退回只按周末判断。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HolidayYearVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int year;

    /**
     * 出处：哪一份通知
     */
    private String source;

    private List<Day> days = List.of();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Day implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * YYYY-MM-DD
         */
        private String date;

        /**
         * 所属节日：春节、国庆节……调休上班的日子也标上是为哪个节调的
         */
        private String name;

        /**
         * true 放假；false 调休上班
         */
        private boolean off;
    }
}
