package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.blog.dto.admin.PostImportTaskPageQuery;
import com.nebula.blog.entity.BlogPostImportItem;
import com.nebula.blog.entity.BlogPostImportTask;
import com.nebula.blog.mapper.BlogPostImportItemMapper;
import com.nebula.blog.mapper.BlogPostImportTaskMapper;
import com.nebula.blog.service.BlogPostImportTaskService;
import com.nebula.blog.vo.admin.PostImportItemVO;
import com.nebula.blog.vo.admin.PostImportTaskVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 后台文章导入任务查询服务实现
 */
@Service
@RequiredArgsConstructor
public class BlogPostImportTaskServiceImpl implements BlogPostImportTaskService {

    private final BlogPostImportTaskMapper taskMapper;
    private final BlogPostImportItemMapper itemMapper;

    @Override
    public PageResult<PostImportTaskVO> page(PostImportTaskPageQuery query) {
        PostImportTaskPageQuery safeQuery = query == null ? new PostImportTaskPageQuery() : query;
        Page<BlogPostImportTask> page = new Page<>(safeQuery.safePageNum(), safeQuery.safePageSize());
        LambdaQueryWrapper<BlogPostImportTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(safeQuery.getStatus()), BlogPostImportTask::getStatus, safeQuery.getStatus())
                .eq(safeQuery.getUserId() != null, BlogPostImportTask::getUserId, safeQuery.getUserId())
                .orderByDesc(BlogPostImportTask::getId);

        Page<BlogPostImportTask> result = taskMapper.selectPage(page, wrapper);
        List<PostImportTaskVO> rows = result.getRecords().stream()
                .map(this::toVO)
                .toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public PostImportTaskVO detail(Long id) {
        BlogPostImportTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "导入任务不存在");
        }
        PostImportTaskVO vo = toVO(task);
        LambdaQueryWrapper<BlogPostImportItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BlogPostImportItem::getTaskId, id)
                .orderByAsc(BlogPostImportItem::getId);
        List<PostImportItemVO> items = itemMapper.selectList(wrapper).stream()
                .map(this::toItemVO)
                .toList();
        vo.setItems(items);
        return vo;
    }

    private PostImportTaskVO toVO(BlogPostImportTask task) {
        PostImportTaskVO vo = new PostImportTaskVO();
        BeanUtils.copyProperties(task, vo, "items");
        return vo;
    }

    private PostImportItemVO toItemVO(BlogPostImportItem item) {
        PostImportItemVO vo = new PostImportItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }
}
