package com.nebula.forge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.forge.entity.ForgeUserPlugin;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户插件安装 Mapper
 *
 * @author nebula
 */
@Mapper
public interface ForgeUserPluginMapper extends BaseMapper<ForgeUserPlugin> {
}
