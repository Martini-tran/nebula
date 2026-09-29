package com.nebula.space.service;

import com.nebula.space.vo.admin.BookmarkExportTaskAdminVO;
import com.nebula.space.vo.admin.BookmarkImportTaskAdminVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;

/**
 * 书签导入/导出服务
 *
 * <p>导入：解析 Chrome 导出的 Netscape Bookmark 文件（HTML），后台逐条入库，前端轮询任务看进度。
 * <p>导出：Chrome 兼容的 Netscape Bookmark HTML，或带目录、标签、备注的 JSON（完整备份），支持按目录/标签/全部范围筛选。
 */
public interface SpaceBookmarkPorterService {

    /**
     * 导入 Chrome 书签 HTML：校验后建任务（处理中，带总条数）立即返回，后台逐条入库
     * <p>每导入一批更新一次任务上的计数，前端轮询任务详情显示进度；单条失败计入 failCount 并记下原因，
     * 不影响其它书签。逐条提交不整体回滚：中途出错时已导入的保留，重新导入同一个文件会按网址跳过它们。
     *
     * @param file 上传的 HTML 文件
     * @return 刚建的导入任务（处理中）
     */
    BookmarkImportTaskAdminVO importChromeHtml(MultipartFile file);

    /**
     * 导出书签为 Chrome 兼容的 Netscape Bookmark HTML
     *
     * @param scopeType       范围：all / folder / tag
     * @param scopeId         scopeType 为 folder/tag 时必填
     * @param includeArchived 连同「已归档」的一起导出（失效的始终不导出）
     * @param out             输出流（直接写入 HTTP 响应）
     * @return 已写入的导出任务（含统计）
     */
    BookmarkExportTaskAdminVO exportChromeHtml(String scopeType, Long scopeId, boolean includeArchived, OutputStream out);

    /**
     * 导出书签为 JSON：HTML 里放不下的标签、描述、备注、状态都在，另附全部目录（带路径）与标签，可据此完整还原
     *
     * @param scopeType       范围：all / folder / tag
     * @param scopeId         scopeType 为 folder/tag 时必填
     * @param includeArchived 连同「已归档」的一起导出（失效的始终不导出）
     * @param out             输出流（直接写入 HTTP 响应）
     * @return 已写入的导出任务（含统计）
     */
    BookmarkExportTaskAdminVO exportJson(String scopeType, Long scopeId, boolean includeArchived, OutputStream out);
}
