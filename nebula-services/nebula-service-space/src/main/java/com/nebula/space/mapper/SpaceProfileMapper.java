package com.nebula.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.space.entity.SpaceProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 公开主页设置 Mapper
 */
@Mapper
public interface SpaceProfileMapper extends BaseMapper<SpaceProfile> {

    /**
     * 合集被展开一次。原子自增防并发丢计数；计数不算「改了设置」，不刷新 update_time
     */
    @Update("UPDATE space_profile SET collection_views = collection_views + 1, update_time = update_time WHERE id = #{id}")
    int increaseCollectionViews(@Param("id") Long id);

    /**
     * 合集被导入一次
     */
    @Update("UPDATE space_profile SET import_count = import_count + 1, update_time = update_time WHERE id = #{id}")
    int increaseImports(@Param("id") Long id);
}
