package com.nebula.space.dto.admin;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 目录结构 AI 重排请求
 */
@Data
public class FolderAiPlanRequest {

    /**
     * 用户的额外要求，如「按技术栈分」「不超过两层」，可空
     */
    @Size(max = 200, message = "整理要求不能超过200字")
    private String hint;
}
