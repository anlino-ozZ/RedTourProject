package com.redtour.business.mapper;

import com.redtour.business.entity.SysUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
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

    /** 统计 admin 用户名数量（用于启动时判断是否初始化管理员） */
    @Select("SELECT COUNT(1) FROM sys_user WHERE username = 'admin' AND deleted = 0")
    int countAdmin();

    /** 插入管理员（初始化时使用，密码已 BCrypt 加密） */
    @Insert("INSERT INTO sys_user (username, password, nickname, role, status) " +
            "VALUES (#{username}, #{password}, #{nickname}, #{role}, #{status})")
    int insert(SysUser user);
}
