package com.nebula.space.dto.me;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 参会人：me 标记自己，absent 标记缺席；未设置的标记不输出
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeetingAttendee implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "参会人姓名不能为空")
    @Size(max = 50)
    private String name;

    private Boolean me;

    private Boolean absent;
}
