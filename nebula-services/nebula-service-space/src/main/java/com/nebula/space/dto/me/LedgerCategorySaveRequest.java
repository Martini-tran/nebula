package com.nebula.space.dto.me;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 修改分类：不传的字段不动。收支方向不能改（已记的流水会对不上）
 */
@Data
public class LedgerCategorySaveRequest {

    @Size(min = 1, max = 20, message = "名称 1-20 字")
    private String name;

    @Size(max = 64)
    @Pattern(regexp = "[a-z0-9-]+:[a-z0-9-]+", message = "图标应为 Iconify 名称，如 lucide:utensils")
    private String icon;

    @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "颜色格式应为 #rrggbb")
    private String color;

    @Size(max = 50, message = "关键词最多 50 个")
    private List<@Size(max = 20, message = "关键词最长 20 字") String> keywords;

    @Min(0)
    @Max(9999)
    private Integer sortOrder;
}
