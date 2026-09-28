package com.nebula.space.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 读系统账号的展示名（分享页写「谁分享的」）。空间服务不维护账号，只读这一列
 */
@Mapper
public interface SpaceUserMapper {

    /**
     * 昵称，没填昵称用用户名；账号不在了返回 null
     */
    @Select("SELECT COALESCE(NULLIF(nickname, ''), username) FROM sys_user WHERE id = #{id}")
    String displayName(@Param("id") Long id);
}
