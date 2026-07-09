package com.nebula.space.dto.admin;

import lombok.Data;

import java.util.List;

/**
 * 后台书签标签绑定请求
 * 传入的 tagIds 会全量替换该书签的标签关系
 */
@Data
public class BookmarkTagBindRequest {

    /**
     * 标签ID列表，为 null 时按空列表处理（即清空全部标签）
     */
    private List<Long> tagIds;
}
