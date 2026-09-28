package com.nebula.space.dto.me;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 手记的承诺：会议之外答应的事（存在 JSON 列里）
 */
@Data
public class PersonPromise implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Size(max = 40)
    private String id;

    /**
     * me 我答应他的 / them 他答应我的
     */
    @NotBlank
    @Pattern(regexp = "me|them", message = "承诺方只能是 me 或 them")
    private String who;

    @NotBlank(message = "承诺内容不能为空")
    @Size(max = 200, message = "承诺最长 200 字")
    private String text;

    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日期格式应为 yyyy-MM-dd")
    private String due;

    private boolean done;

    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}", message = "时间格式应为 yyyy-MM-dd HH:mm:ss")
    private String createTime;
}
