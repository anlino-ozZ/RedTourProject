package com.redtour.business.mapper;

import com.redtour.business.entity.WikiEntry;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Wiki 条目 Mapper。
 */
@Mapper
public interface WikiEntryMapper {

    /** 分页查询指定景区的 Wiki 条目。 */
    @Select("""
            <script>
            SELECT id, scenic_area_id, title, file_path, content, tags, links, compile_status, updated_at
            FROM wiki_entry
            WHERE scenic_area_id = #{scenicAreaId}
            <if test="keyword != null and keyword != ''">
                AND (title LIKE CONCAT('%', #{keyword}, '%')
                     OR content LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY updated_at DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<WikiEntry> findPage(@Param("scenicAreaId") Long scenicAreaId,
                             @Param("keyword") String keyword,
                             @Param("offset") int offset,
                             @Param("pageSize") int pageSize);

    /** 统计指定景区的 Wiki 条目数量。 */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM wiki_entry
            WHERE scenic_area_id = #{scenicAreaId}
            <if test="keyword != null and keyword != ''">
                AND (title LIKE CONCAT('%', #{keyword}, '%')
                     OR content LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    long count(@Param("scenicAreaId") Long scenicAreaId, @Param("keyword") String keyword);

    /** 根据 ID 查询条目。 */
    @Select("SELECT id, scenic_area_id, title, file_path, content, tags, links, compile_status, updated_at "
            + "FROM wiki_entry WHERE id = #{id} LIMIT 1")
    WikiEntry findById(@Param("id") Long id);

    /** 新增待编译条目。 */
    @Insert("INSERT INTO wiki_entry "
            + "(scenic_area_id, title, file_path, content, tags, links, compile_status) "
            + "VALUES (#{scenicAreaId}, #{title}, #{filePath}, #{content}, #{tags}, #{links}, #{compileStatus})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WikiEntry entry);

    /** 编辑正文并将编译状态重置为 pending。 */
    @Update("UPDATE wiki_entry SET title = #{title}, file_path = #{filePath}, content = #{content}, tags = #{tags}, "
            + "links = #{links}, compile_status = 'pending', updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id}")
    int updateContent(WikiEntry entry);

    /** 将条目标记为编译中。 */
    @Update("UPDATE wiki_entry SET compile_status = 'compiling', updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id}")
    int markCompiling(@Param("id") Long id);

    /** 保存编译成功结果。 */
    @Update("UPDATE wiki_entry SET file_path = #{filePath}, links = #{links}, "
            + "compile_status = 'done', updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int markDone(@Param("id") Long id, @Param("filePath") String filePath, @Param("links") String links);

    /** 保存编译失败状态。 */
    @Update("UPDATE wiki_entry SET compile_status = 'failed', updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id}")
    int markFailed(@Param("id") Long id);

    /** 删除条目。 */
    @Delete("DELETE FROM wiki_entry WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
