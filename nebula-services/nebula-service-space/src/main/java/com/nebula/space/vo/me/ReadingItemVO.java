package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 稍后读文章视图对象
 *
 * <p>列表里不带正文（content 为 null），看 saved 判断有没有阅读版；打开单篇时才带正文。</p>
 */
@Data
public class ReadingItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String url;

    private String title;

    private String domain;

    private String excerpt;

    /**
     * 存档的阅读版正文（按段落）；列表里、没抓到时为 null
     */
    private List<String> content;

    /**
     * 有没有存档的阅读版
     */
    private Boolean saved;

    /**
     * 预计阅读分钟，0 为未知
     */
    private Integer minutes;

    private String status;

    private Double progress;

    private Double position;

    private String thought;

    private Boolean archived;

    private Long bookmarkId;

    private LocalDateTime addTime;

    private LocalDateTime lastReadTime;

    private LocalDateTime doneTime;
}
