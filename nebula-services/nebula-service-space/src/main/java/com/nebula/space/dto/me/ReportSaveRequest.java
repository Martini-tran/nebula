package com.nebula.space.dto.me;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存日报或周报（整份覆盖）；正文为空表示删除这一份
 */
@Data
public class ReportSaveRequest {

    @Size(max = 100000, message = "正文最长 100000 字")
    private String content;
}
