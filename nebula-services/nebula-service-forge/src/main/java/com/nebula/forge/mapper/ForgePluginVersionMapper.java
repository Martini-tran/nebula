package com.nebula.forge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.forge.entity.ForgePluginVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 插件版本表 Mapper
 *
 * @author nebula
 */
@Mapper
public interface ForgePluginVersionMapper extends BaseMapper<ForgePluginVersion> {
}
