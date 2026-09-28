package com.nebula.space.dto.me;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

/**
 * 重命名 / 移动文件或文件夹
 *
 * <p>区分「没传」和「传了 null」：folderId 传 null 是移到根目录，不传是不移动。</p>
 */
@Getter
public class FileUpdateRequest {

    @JsonIgnore
    private final Set<String> present = new HashSet<>();

    @Size(max = 255, message = "名称最长 255 字")
    private String name;

    private Long folderId;

    public boolean has(String field) {
        return present.contains(field);
    }

    public void setName(String name) {
        this.name = name;
        present.add("name");
    }

    public void setFolderId(Long folderId) {
        this.folderId = folderId;
        present.add("folderId");
    }
}
