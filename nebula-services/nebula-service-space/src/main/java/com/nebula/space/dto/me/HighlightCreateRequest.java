package com.nebula.space.dto.me;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建划线：定位锚点是存档正文的第几段、段内起止字符（UTF-16 下标，和前端字符串下标一致）
 */
@Data
public class HighlightCreateRequest {

    @NotNull(message = "文章不能为空")
    private Long itemId;

    @NotNull(message = "段落不能为空")
    @Min(value = 0, message = "段落下标不正确")
    private Integer para;

    @NotNull(message = "起始位置不能为空")
    @Min(value = 0, message = "起始位置不正确")
    private Integer start;

    @NotNull(message = "结束位置不能为空")
    @Min(value = 1, message = "结束位置不正确")
    private Integer end;

    /**
     * 选中的文字，仅供参考：存的是按锚点从存档正文里截出来的，和阅读页渲染的一致
     */
    @Size(max = 5000, message = "一次最多划 5000 字")
    private String text;

    @Pattern(regexp = "yellow|blue", message = "划线颜色只能是 yellow 或 blue")
    private String color;

    @Size(max = 1000, message = "批注最长 1000 字")
    private String note;
}
