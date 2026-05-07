package com.nebula.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * service-system 内部使用的 sys_menu Mapper
 *
 * @author nebula
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
}
