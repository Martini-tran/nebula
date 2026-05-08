package com.nebula.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * service-system 内部使用的 sys_user_role Mapper
 * 联合主键 (user_id, role_id) — 仅用 selectList / insert / delete(QueryWrapper)
 *
 * @author nebula
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
