package com.nebula.space.dto.me;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建文件夹
 */
@Data
public class FileFolderCreateRequest {

    @NotBlank(message = "文件夹名不能为空")
    @Size(max = 255, message = "名称最长 255 字")
    private String name;

    /**
     * 建在哪个文件夹里，空为根目录
     */
    private Long folderId;
}
