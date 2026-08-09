package com.redtour.business.mapper;

import com.redtour.business.entity.SysUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper（鉴权 + 用户管理共用）
 */
@Mapper
public interface UserMapper {

    /** 根据用户名查询（过滤已禁用 + 已删除） */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0 LIMIT 1")
    SysUser findByUsername(@Param("username") String username);

    /** 根据 ID 查询 */
    @Select("SELECT * FROM sys_user WHERE id = #{id} AND deleted = 0 LIMIT 1")
    SysUser findById(@Param("id") Long id);

    /** 统计指定用户名存在数量（启动时判断是否初始化该账号） */
    @Select("SELECT COUNT(1) FROM sys_user WHERE username = #{username} AND deleted = 0")
    int countByUsername(@Param("username") String username);

    /** 插入管理员（初始化时使用，密码已 BCrypt 加密） */
    @Insert("INSERT INTO sys_user (username, password, nickname, role, scenic_area_id, status, deleted) " +
            "VALUES (#{username}, #{password}, #{nickname}, #{role}, #{scenicAreaId}, #{status}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysUser user);
}
