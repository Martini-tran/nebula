package com.nebula.space.service;

import com.nebula.space.vo.admin.BookmarkExportTaskAdminVO;
import com.nebula.space.vo.admin.BookmarkImportTaskAdminVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;

/**
 * 书签导入/导出服务
 *
 * <p>导入：解析 Chrome 导出的 Netscape Bookmark 文件（HTML），同步入库。
 * <p>导出：生成 Chrome 兼容的 Netscape Bookmark HTML，支持按目录/标签/全部范围筛选。
 */
public interface SpaceBookmarkPorterService {

    /**
     * 同步导入 Chrome 书签 HTML
     * <p>解析失败时整体回滚，单条 URL 解析失败计入 failCount 但不影响其它书签
     *
     * @param file 上传的 HTML 文件
     * @return 已写入的导入任务（含统计）
     */
    BookmarkImportTaskAdminVO importChromeHtml(MultipartFile file);

    /**
     * 导出书签为 Chrome 兼容的 Netscape Bookmark HTML
     *
     * @param scopeType 范围：all / folder / tag
     * @param scopeId   scopeType 为 folder/tag 时必填
     * @param out       输出流（直接写入 HTTP 响应）
     * @return 已写入的导出任务（含统计）
     */
    BookmarkExportTaskAdminVO exportChromeHtml(String scopeType, Long scopeId, OutputStream out);
}
