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

    /**
     * 触摸屏公开分页查询：返回「已发布版本」。
     *
     * <p>取值规则：compile_status = 'done' 用当前字段；否则回退到 published_* 快照；
     * 从未成功发布过（published_file_path 为空）的条目不返回。
     *
     * <p>与 findPage 分开是为了不影响管理端——管理端必须能看到 pending/compiling/failed 才好重编。
     * 套一层派生表是为了让 keyword 匹配到对外展示的版本，而不是编辑中的草稿。
     */
    @Select("""
            <script>
            SELECT * FROM (
                SELECT id, scenic_area_id,
                       CASE WHEN compile_status = 'done' THEN title ELSE published_title END AS title,
                       CASE WHEN compile_status = 'done' THEN file_path ELSE published_file_path END AS file_path,
                       CASE WHEN compile_status = 'done' THEN content ELSE published_content END AS content,
                       CASE WHEN compile_status = 'done' THEN tags ELSE published_tags END AS tags,
                       CASE WHEN compile_status = 'done' THEN links ELSE published_links END AS links,
                       compile_status, updated_at
                FROM wiki_entry
                WHERE scenic_area_id = #{scenicAreaId}
                  AND (compile_status = 'done' OR published_file_path IS NOT NULL)
            ) t
            <if test="keyword != null and keyword != ''">
                WHERE t.title LIKE CONCAT('%', #{keyword}, '%')
                   OR t.content LIKE CONCAT('%', #{keyword}, '%')
            </if>
            ORDER BY t.updated_at DESC, t.id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<WikiEntry> findPublicPage(@Param("scenicAreaId") Long scenicAreaId,
                                   @Param("keyword") String keyword,
                                   @Param("offset") int offset,
                                   @Param("pageSize") int pageSize);

    /** 触摸屏公开计数（与 findPublicPage 同过滤条件）。 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM (
                SELECT id,
                       CASE WHEN compile_status = 'done' THEN title ELSE published_title END AS title,
                       CASE WHEN compile_status = 'done' THEN content ELSE published_content END AS content
                FROM wiki_entry
                WHERE scenic_area_id = #{scenicAreaId}
                  AND (compile_status = 'done' OR published_file_path IS NOT NULL)
            ) t
            <if test="keyword != null and keyword != ''">
                WHERE t.title LIKE CONCAT('%', #{keyword}, '%')
                   OR t.content LIKE CONCAT('%', #{keyword}, '%')
            </if>
            </script>
            """)
    long countPublic(@Param("scenicAreaId") Long scenicAreaId, @Param("keyword") String keyword);

    /** 触摸屏公开详情：返回已发布版本，从未发布过的条目查不到。 */
    @Select("SELECT id, scenic_area_id, "
            + "CASE WHEN compile_status = 'done' THEN title ELSE published_title END AS title, "
            + "CASE WHEN compile_status = 'done' THEN file_path ELSE published_file_path END AS file_path, "
            + "CASE WHEN compile_status = 'done' THEN content ELSE published_content END AS content, "
            + "CASE WHEN compile_status = 'done' THEN tags ELSE published_tags END AS tags, "
            + "CASE WHEN compile_status = 'done' THEN links ELSE published_links END AS links, "
            + "compile_status, updated_at "
            + "FROM wiki_entry "
            + "WHERE id = #{id} AND (compile_status = 'done' OR published_file_path IS NOT NULL) LIMIT 1")
    WikiEntry findPublicById(@Param("id") Long id);

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

    /**
     * 保存编译成功结果，并把对外字段整体快照为「已发布版本」。
     *
     * <p>快照只在这里写入，updateContent（编辑）不会动它。于是条目被重新编译时
     * 状态回到 pending/compiling/failed，触摸屏仍能读到上一版内容，不会断档。
     */
    @Update("UPDATE wiki_entry SET file_path = #{filePath}, links = #{links}, "
            + "published_title = #{title}, published_file_path = #{filePath}, "
            + "published_content = #{content}, published_tags = #{tags}, published_links = #{links}, "
            + "compile_status = 'done', updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int markDone(@Param("id") Long id,
                 @Param("title") String title,
                 @Param("content") String content,
                 @Param("tags") String tags,
                 @Param("filePath") String filePath,
                 @Param("links") String links);

    /** 保存编译失败状态。 */
    @Update("UPDATE wiki_entry SET compile_status = 'failed', updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id}")
    int markFailed(@Param("id") Long id);

    /** 删除条目。 */
    @Delete("DELETE FROM wiki_entry WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /** 查询已发布版本的文件路径（删除条目时用于清理残留编译产物）。 */
    @Select("SELECT published_file_path FROM wiki_entry WHERE id = #{id} LIMIT 1")
    String findPublishedFilePath(@Param("id") Long id);
}
