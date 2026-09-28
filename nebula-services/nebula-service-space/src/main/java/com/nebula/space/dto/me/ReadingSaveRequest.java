package com.nebula.space.dto.me;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改文章的阅读状态：不传的字段不动
 *
 * <p>标成读完时进度记为 1，没带读完时间就用现在；改回未读完时读完时间清空。</p>
 */
@Data
public class ReadingSaveRequest {

    @Size(max = 300, message = "标题最长 300 字")
    private String title;

    @Pattern(regexp = "unread|reading|done", message = "不支持的阅读状态")
    private String status;

    @DecimalMin(value = "0", message = "进度在 0 到 1 之间")
    @DecimalMax(value = "1", message = "进度在 0 到 1 之间")
    private Double progress;

    @DecimalMin(value = "0", message = "位置在 0 到 1 之间")
    @DecimalMax(value = "1", message = "位置在 0 到 1 之间")
    private Double position;

    @Size(max = 500, message = "读后感最长 500 字")
    private String thought;

    private Boolean archived;

    /**
     * yyyy-MM-dd HH:mm:ss
     */
    private String lastReadTime;

    /**
     * yyyy-MM-dd HH:mm:ss；只在标成读完时用到
     */
    private String doneTime;
}
