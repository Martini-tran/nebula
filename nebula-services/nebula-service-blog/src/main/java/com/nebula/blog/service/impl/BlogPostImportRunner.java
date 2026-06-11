package com.nebula.blog.service.impl;

import com.nebula.blog.entity.BlogPostImportItem;
import com.nebula.blog.entity.BlogPostImportTask;
import com.nebula.blog.mapper.BlogPostImportItemMapper;
import com.nebula.blog.mapper.BlogPostImportTaskMapper;
import com.nebula.blog.service.BlogPostAdminService;
import com.nebula.blog.vo.admin.PostImportResultVO;
import com.nebula.common.core.context.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 博客文章 Markdown 导入异步执行器。
 * <p>独立于 {@link BlogPostAdminService} 实现的 Bean，承载 {@code @Async} 方法（避免自调用导致异步失效），
 * 负责逐文件处理、写入明细、刷新任务进度与最终状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlogPostImportRunner {

    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";

    private static final int ERROR_MESSAGE_MAX = 2000;
    private static final int FILENAME_MAX = 500;
    private static final int TITLE_MAX = 255;
    private static final int SLUG_MAX = 255;
    private static final int ITEM_ERROR_MAX = 1000;

    private final BlogPostImportTaskMapper taskMapper;
    private final BlogPostImportItemMapper itemMapper;

    /**
     * 反向依赖文章服务以复用单文件导入逻辑；@Lazy 打破构造期循环依赖。
     */
    @Lazy
    @Autowired
    private BlogPostAdminService postAdminService;

    /**
     * 异步执行整个导入任务。
     * <p>异步线程无请求上下文，先 {@link UserContext#set(Long)} 恢复用户，结束后 {@link UserContext#clear()} 清理。
     * 单文件异常已在 {@link BlogPostAdminService#importSingleMarkdown} 内吞掉并以失败结果返回，
     * 此处仅对编排级异常兜底，将任务整体置为 failed。
     */
    @Async("blogImportExecutor")
    public void run(Long taskId, List<ImportFile> files, String status, String visibility,
                    String postType, List<Long> categoryIds, boolean rehostImages, Long userId) {
        UserContext.set(userId);
        try {
            markRunning(taskId);

            // 本批已分配的 slug，跨文件去重（DB 尚未提交时避免相互冲突）
            Set<String> usedSlugs = new HashSet<>();
            int success = 0;
            int fail = 0;
            int processed = 0;
            for (ImportFile file : files) {
                PostImportResultVO r = postAdminService.importSingleMarkdown(
                        file.bytes(), file.filename(), status, visibility, postType,
                        categoryIds, rehostImages, usedSlugs);
                saveItem(taskId, r);
                if (r.isSuccess()) {
                    success++;
                } else {
                    fail++;
                }
                processed++;
                // 每文件刷新进度，便于前端轮询观测
                updateProgress(taskId, processed, success, fail);
            }

            markFinished(taskId, success, fail);
        } catch (Exception e) {
            log.error("Markdown import task failed, taskId={}", taskId, e);
            markFailed(taskId, e.getMessage());
        } finally {
            UserContext.clear();
        }
    }

    private void markRunning(Long taskId) {
        BlogPostImportTask update = new BlogPostImportTask();
        update.setId(taskId);
        update.setStatus(STATUS_RUNNING);
        update.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    private void updateProgress(Long taskId, int processed, int success, int fail) {
        BlogPostImportTask update = new BlogPostImportTask();
        update.setId(taskId);
        update.setProcessedCount(processed);
        update.setSuccessCount(success);
        update.setFailCount(fail);
        taskMapper.updateById(update);
    }

    private void markFinished(Long taskId, int success, int fail) {
        BlogPostImportTask update = new BlogPostImportTask();
        update.setId(taskId);
        update.setStatus(STATUS_SUCCESS);
        update.setProcessedCount(success + fail);
        update.setSuccessCount(success);
        update.setFailCount(fail);
        update.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    private void markFailed(Long taskId, String message) {
        BlogPostImportTask update = new BlogPostImportTask();
        update.setId(taskId);
        update.setStatus(STATUS_FAILED);
        update.setErrorMessage(truncate(message, ERROR_MESSAGE_MAX));
        update.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    private void saveItem(Long taskId, PostImportResultVO r) {
        BlogPostImportItem item = new BlogPostImportItem();
        item.setTaskId(taskId);
        item.setFilename(truncate(r.getFilename(), FILENAME_MAX));
        item.setSuccess(r.isSuccess());
        item.setArticleId(r.getArticleId());
        item.setTitle(truncate(r.getTitle(), TITLE_MAX));
        item.setSlug(truncate(r.getSlug(), SLUG_MAX));
        item.setError(truncate(r.getError(), ITEM_ERROR_MAX));
        itemMapper.insert(item);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * 待导入文件的内存载体（请求线程读入字节后传入异步线程）。
     */
    public record ImportFile(String filename, byte[] bytes) implements Serializable {
    }
}
