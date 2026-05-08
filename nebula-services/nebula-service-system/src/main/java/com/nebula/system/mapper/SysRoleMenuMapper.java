package com.nebula.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.system.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * service-system 内部使用的 sys_role_menu Mapper
 * 联合主键 (role_id, menu_id) — 仅用 selectList / insert / delete(QueryWrapper)，不调用 byId 系列
 *
 * @author nebula
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {
}
