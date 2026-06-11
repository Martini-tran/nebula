package com.nebula.blog.service;

import com.nebula.blog.dto.admin.PostImportTaskPageQuery;
import com.nebula.blog.vo.admin.PostImportTaskVO;
import com.nebula.common.core.domain.PageResult;

/**
 * 后台文章导入任务查询服务接口
 */
public interface BlogPostImportTaskService {

    /**
     * 分页查询导入任务（不含逐文件明细）
     */
    PageResult<PostImportTaskVO> page(PostImportTaskPageQuery query);

    /**
     * 查询导入任务详情（含逐文件明细）
     */
    PostImportTaskVO detail(Long id);
}
