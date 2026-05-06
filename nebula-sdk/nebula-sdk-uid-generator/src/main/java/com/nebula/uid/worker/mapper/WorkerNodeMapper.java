package com.nebula.uid.worker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.uid.worker.entity.WorkerNodeEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * worker_node 表 Mapper，基于 MyBatis-Plus BaseMapper 实现。
 *
 * @author nebula
 */
@Mapper
public interface WorkerNodeMapper extends BaseMapper<WorkerNodeEntity> {
}
