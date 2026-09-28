package com.nebula.space.dto.me;

import lombok.Data;

/**
 * 划线查询
 */
@Data
public class HighlightQuery {

    /**
     * 只看这篇文章的；不传为全部
     */
    private Long itemId;
}
