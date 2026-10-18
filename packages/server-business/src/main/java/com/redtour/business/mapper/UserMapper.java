package com.redtour.business.mapper;

import com.redtour.business.entity.SysUser;
import org.apache.ibatis.annotations.*;

import java.util.List;

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

    /** 用户管理-分页列表 */
    @Select("""
            SELECT id, username, nickname, role, scenic_area_id, avatar, status, created_at, updated_at, deleted
            FROM sys_user
            WHERE deleted = 0
            AND (#{keyword} IS NULL OR username LIKE CONCAT('%', #{keyword}, '%') OR nickname LIKE CONCAT('%', #{keyword}, '%'))
            ORDER BY id ASC
            LIMIT #{offset}, #{pageSize}
            """)
    List<SysUser> findPage(@Param("keyword") String keyword, @Param("offset") int offset, @Param("pageSize") int pageSize);

    /** 用户管理-总数 */
    @Select("""
            SELECT COUNT(1) FROM sys_user
            WHERE deleted = 0
            AND (#{keyword} IS NULL OR username LIKE CONCAT('%', #{keyword}, '%') OR nickname LIKE CONCAT('%', #{keyword}, '%'))
            """)
    long count(@Param("keyword") String keyword);

    /** 排除指定ID的用户名查重（用于更新时唯一性校验） */
    @Select("SELECT COUNT(1) FROM sys_user WHERE username = #{username} AND deleted = 0 AND id != #{excludeId}")
    int countByUsernameExcludeId(@Param("username") String username, @Param("excludeId") Long excludeId);

    /** 更新用户信息（nickname / role / scenic_area_id / avatar / status） */
    @Update("""
            UPDATE sys_user SET
                nickname = #{nickname},
                role = #{role},
                scenic_area_id = #{scenicAreaId},
                avatar = #{avatar},
                status = #{status},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted = 0
            """)
    int updateById(SysUser user);

    /** 仅更新密码 */
    @Update("UPDATE sys_user SET password = #{password}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = 0")
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    /** 软删除 */
    @Update("UPDATE sys_user SET deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = 0")
    int softDelete(@Param("id") Long id);

    /** 仅更新状态（状态开关） */
    @Update("UPDATE sys_user SET status = #{status}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("status") int status);
}
