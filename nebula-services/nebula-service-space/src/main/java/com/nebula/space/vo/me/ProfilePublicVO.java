package com.nebula.space.vo.me;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 访客看到的公开主页（公开，无需登录）
 *
 * <p>只有这些字段：划线不带批注，读完的文章不带划线和读后感，目标不带数值和金额类。关掉的区块整块为空。</p>
 */
@Data
public class ProfilePublicVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String handle;

    /**
     * 账号的昵称，没填用用户名
     */
    private String nickname;

    private String bio;

    private List<Link> links;

    private String now;

    private LocalDate nowUpdated;

    /**
     * 打开的区块，按展示顺序
     */
    private List<String> blocks;

    private List<Collection> collections;

    private List<Reading> reading;

    private List<Goal> goals;

    private List<Quote> quotes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Link implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String label;

        private String url;

        /**
         * 邮箱：访客点一下才显示，防爬
         */
        private boolean masked;
    }

    @Data
    public static class Collection implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 书签目录ID，导入、计数时回传
         */
        private String id;

        private String title;

        private String description;

        /**
         * 合集里最近一次改动的日期
         */
        private LocalDate updated;

        private List<Bookmark> bookmarks;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Bookmark implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String title;

        private String url;

        private String domain;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reading implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String title;

        private String url;

        private String domain;

        /**
         * 读完那天
         */
        private LocalDate date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Goal implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String icon;

        private String title;

        /**
         * 进度 0~1
         */
        private double pct;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Quote implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 划线原文
         */
        private String text;

        /**
         * 出自哪篇文章
         */
        private String source;
    }
}
