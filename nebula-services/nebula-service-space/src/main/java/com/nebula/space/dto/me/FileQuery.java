package com.nebula.space.dto.me;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 文件柜列表查询
 */
@Data
public class FileQuery {

    /**
     * folder 某个文件夹（默认） / recent 最近 / trash 最近删除 / kind 按类型 / source 其他模块的附件 / all 全部
     */
    @Pattern(regexp = "folder|recent|trash|kind|source|all", message = "view 只能是 folder、recent、trash、kind、source、all")
    private String view;

    /**
     * view=folder 时浏览的文件夹，空为根目录
     */
    private Long folderId;

    @Pattern(regexp = "pdf|image|doc|sheet|zip|other", message = "kind 不正确")
    private String kind;

    @Pattern(regexp = "notes|meetings|reading|bookmarks", message = "source 不正确")
    private String source;
}
