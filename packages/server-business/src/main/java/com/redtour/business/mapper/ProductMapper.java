package com.redtour.business.mapper;

import com.redtour.business.entity.Product;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 特产/文创产品 Mapper（注解式动态 SQL）。
 * product 无 deleted 列，下线和删除统一用 status=0 表达；click_count 为浏览量自增列（brief B3）。
 */
@Mapper
public interface ProductMapper {

    /** 管理端分页查询（带景区/名称/状态筛选）。 */
    @Select("""
            <script>
            SELECT id, scenic_area_id, name, price, description, image, stock, category, status, click_count, created_at, updated_at
            FROM product
            <where>
                <if test="scenicAreaId != null">AND scenic_area_id = #{scenicAreaId}</if>
                <if test="name != null and name != ''">AND name LIKE CONCAT('%', #{name}, '%')</if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            ORDER BY id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Product> findPage(@Param("scenicAreaId") Long scenicAreaId,
                           @Param("name") String name,
                           @Param("status") Integer status,
                           @Param("offset") int offset,
                           @Param("pageSize") int pageSize);

    /** 管理端分页计数（与 findPage 同过滤条件）。 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM product
            <where>
                <if test="scenicAreaId != null">AND scenic_area_id = #{scenicAreaId}</if>
                <if test="name != null and name != ''">AND name LIKE CONCAT('%', #{name}, '%')</if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            </script>
            """)
    long count(@Param("scenicAreaId") Long scenicAreaId,
               @Param("name") String name,
               @Param("status") Integer status);

    /** 按 ID 查询（不过滤状态，供管理端使用）。 */
    @Select("SELECT id, scenic_area_id, name, price, description, image, stock, category, status, click_count, created_at, updated_at "
            + "FROM product WHERE id = #{id} LIMIT 1")
    Product findById(@Param("id") Long id);

    /** 新增产品（scenic_area_id 由业务层按权限解析后写入）。 */
    @Insert("INSERT INTO product "
            + "(scenic_area_id, name, price, description, image, stock, category, status) "
            + "VALUES (#{scenicAreaId}, #{name}, #{price}, #{description}, #{image}, #{stock}, #{category}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Product product);

    /** 编辑产品（局部更新非空字段，scenic_area_id 不可变）。 */
    @Update("""
            <script>
            UPDATE product
            <set>
                <if test="name != null">name = #{name},</if>
                <if test="price != null">price = #{price},</if>
                <if test="description != null">description = #{description},</if>
                <if test="image != null">image = #{image},</if>
                <if test="stock != null">stock = #{stock},</if>
                <if test="category != null">category = #{category},</if>
                <if test="status != null">status = #{status},</if>
                updated_at = CURRENT_TIMESTAMP
            </set>
            WHERE id = #{id}
            </script>
            """)
    int updateById(Product product);

    /** 上下架状态切换。 */
    @Update("UPDATE product SET status = #{status}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 下线（无 deleted 列，用 status=0 表达删除）。 */
    @Update("UPDATE product SET status = 0, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int delete(@Param("id") Long id);

    /** 浏览量自增（公开详情 GET /{id} 调用，brief B3）。 */
    @Update("UPDATE product SET click_count = click_count + 1 WHERE id = #{id}")
    int updateClickCount(@Param("id") Long id);

    /** 游客端：仅上架产品分页（可按景区/品类/名称模糊筛选）。 */
    @Select("""
            <script>
            SELECT id, scenic_area_id, name, price, description, image, stock, category, status, click_count, created_at, updated_at
            FROM product
            WHERE status = 1
            <if test="scenicAreaId != null">AND scenic_area_id = #{scenicAreaId}</if>
            <if test="category != null and category != ''">AND category = #{category}</if>
            <if test="name != null and name != ''">AND name LIKE CONCAT('%', #{name}, '%')</if>
            ORDER BY id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Product> findPublicPage(@Param("scenicAreaId") Long scenicAreaId,
                                 @Param("category") String category,
                                 @Param("name") String name,
                                 @Param("offset") int offset,
                                 @Param("pageSize") int pageSize);

    /** 游客端：仅上架产品分页计数。 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM product
            WHERE status = 1
            <if test="scenicAreaId != null">AND scenic_area_id = #{scenicAreaId}</if>
            <if test="category != null and category != ''">AND category = #{category}</if>
            <if test="name != null and name != ''">AND name LIKE CONCAT('%', #{name}, '%')</if>
            </script>
            """)
    long countPublic(@Param("scenicAreaId") Long scenicAreaId,
                     @Param("category") String category,
                     @Param("name") String name);

    /** 游客端：按 ID 查上架产品。 */
    @Select("SELECT id, scenic_area_id, name, price, description, image, stock, category, status, click_count, created_at, updated_at "
            + "FROM product WHERE id = #{id} AND status = 1 LIMIT 1")
    Product findPublicById(@Param("id") Long id);
}
