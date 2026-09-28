package com.nebula.space.service;

import com.nebula.space.dto.admin.BookmarkLinkCheckRequest;
import com.nebula.space.vo.admin.BookmarkLinkCheckVO;

import java.util.List;

/**
 * 书签链接检查服务
 */
public interface SpaceBookmarkLinkCheckService {

    /**
     * 检查一批书签的链接，并按结论改状态：正常的打不开 → 失效；失效的又能打开 → 正常；无法确定的不动。
     * 已归档的不检查。只处理当前用户自己的书签，别人的 ID 直接忽略。
     *
     * @return 每条书签的检查结果，顺序同入参（忽略掉的不返回）
     */
    List<BookmarkLinkCheckVO> check(BookmarkLinkCheckRequest req);
}
