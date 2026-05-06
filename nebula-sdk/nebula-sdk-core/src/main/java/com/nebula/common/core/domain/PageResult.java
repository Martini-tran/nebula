package com.nebula.common.core.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一分页返回对象
 * 与具体 ORM 解耦，便于在 Controller / Service 层之间传递分页结果
 *
 * @param <T> 行数据类型
 * @author nebula
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前页数据
     */
    private final List<T> records;

    /**
     * 总记录数
     */
    private final long total;

    /**
     * 当前页码（从1开始）
     */
    private final long current;

    /**
     * 每页大小
     */
    private final long size;

    /**
     * 总页数
     */
    private final long pages;

    /**
     * 构造分页结果
     *
     * @param records 当前页数据，为 null 时按空列表处理
     * @param total   总记录数
     * @param current 当前页码（从1开始）
     * @param size    每页大小，必须大于0
     * @param <T>     行数据类型
     * @return 分页结果对象
     */
    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        long safeSize = size <= 0 ? 0 : size;
        long pages = safeSize == 0 ? 0 : (total + safeSize - 1) / safeSize;
        return new PageResult<>(records == null ? Collections.emptyList() : records, total, current, safeSize, pages);
    }

    /**
     * 空分页结果
     *
     * @param current 当前页码
     * @param size    每页大小
     * @param <T>     行数据类型
     * @return 空数据的分页结果对象
     */
    public static <T> PageResult<T> empty(long current, long size) {
        return of(Collections.emptyList(), 0L, current, size);
    }
}
