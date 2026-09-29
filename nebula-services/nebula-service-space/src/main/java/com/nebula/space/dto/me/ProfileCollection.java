package com.nebula.space.dto.me;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 公开的书签合集：对应一个书签目录，只公开目录里直接放的正常书签（存在 JSON 列里）
 */
@Data
public class ProfileCollection implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 书签目录 space_bookmark_folder.id
     */
    private Long folderId;

    @Size(max = 20, message = "合集名最长 20 字")
    private String title;

    @Size(max = 60, message = "合集介绍最长 60 字")
    private String description;
}
