package com.nebula.space.dto.me;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 加入稍后读
 */
@Data
public class ReadingCreateRequest {

    @NotBlank(message = "网址不能为空")
    @Size(max = 2000, message = "网址太长")
    private String url;

    /**
     * 从书签加入时带书签标题；不传则用抓到的标题
     */
    @Size(max = 300, message = "标题最长 300 字")
    private String title;

    /**
     * 从哪个书签加入；不是自己的书签就不认
     */
    private Long bookmarkId;
}
