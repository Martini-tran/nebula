package com.nebula.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * service-system 内部使用的 sys_role Mapper
 *
 * @author nebula
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}
