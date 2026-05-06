package com.nebula.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * service-auth 内部使用的 sys_user Mapper
 *
 * @author nebula
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
