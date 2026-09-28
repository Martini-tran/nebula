package com.nebula.space.dto.me;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 手记的一次联系（打电话、吃饭），会议和随手记之外的往来（存在 JSON 列里）
 */
@Data
public class PersonContact implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "联系日期不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日期格式应为 yyyy-MM-dd")
    private String date;

    @Size(max = 200, message = "联系备注最长 200 字")
    private String note;
}
