package com.redtour.business.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 剧本进度 Mapper（注解式 SQL）。
 * 主键自增，uk_user_script(user_id, script_id) 保证每用户每剧本一条；
 * 完成接口用 ON DUPLICATE KEY UPDATE 幂等置 completed=1。
 */
@Mapper
public interface ScriptProgressMapper {

    @Select("SELECT COUNT(1) FROM script_progress WHERE user_id = #{userId} AND script_id = #{scriptId}")
    long countByUserAndScript(@Param("userId") Long userId, @Param("scriptId") Long scriptId);

    @Insert("""
            INSERT INTO script_progress (user_id, script_id, current_node_id, completed)
            VALUES (#{userId}, #{scriptId}, 0, 1)
            ON DUPLICATE KEY UPDATE completed = 1, updated_at = NOW()
            """)
    int upsertCompleted(@Param("userId") Long userId, @Param("scriptId") Long scriptId);
}
