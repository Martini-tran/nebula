package com.nebula.forge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.forge.entity.ForgePluginPermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 插件版本权限声明表 Mapper
 *
 * @author nebula
 */
@Mapper
public interface ForgePluginPermissionMapper extends BaseMapper<ForgePluginPermission> {
}
