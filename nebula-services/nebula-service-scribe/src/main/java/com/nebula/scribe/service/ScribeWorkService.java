package com.nebula.scribe.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.scribe.dto.WorkCreateRequest;
import com.nebula.scribe.dto.WorkPageQuery;
import com.nebula.scribe.dto.WorkUpdateRequest;
import com.nebula.scribe.vo.WorkDetailVO;
import com.nebula.scribe.vo.WorkListVO;

/**
 * 作品服务，所有操作都限定在当前登录作者名下
 */
public interface ScribeWorkService {

    /**
     * 分页查询当前作者的作品
     */
    PageResult<WorkListVO> page(WorkPageQuery query);

    /**
     * 新建作品，归属当前作者，初始状态为构思中
     */
    WorkListVO create(WorkCreateRequest req);

    /**
     * 查询作品详情；非本人作品与不存在同样按「作品不存在」处理
     */
    WorkDetailVO detail(Long id);

    /**
     * 修改作品（整表单覆盖）
     */
    WorkDetailVO update(Long id, WorkUpdateRequest req);

    /**
     * 删除作品：软删除进回收站
     */
    void delete(Long id);
}
