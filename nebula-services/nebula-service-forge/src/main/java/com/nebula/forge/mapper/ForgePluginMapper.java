package com.nebula.forge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.forge.entity.ForgePlugin;
import org.apache.ibatis.annotations.Mapper;

/**
 * 插件主表 Mapper
 *
 * @author nebula
 */
@Mapper
public interface ForgePluginMapper extends BaseMapper<ForgePlugin> {
}
