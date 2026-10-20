package com.redtour.business.mapper;

import com.redtour.business.entity.Product;
import com.redtour.business.entity.ProductOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 特产意向订单 Mapper（注解式动态 SQL）。
 * findProductById 内联查询产品用于下单快照，避免依赖 product 模块 Mapper。
 */
@Mapper
public interface ProductOrderMapper {

    /** 新增订单（id / created_at / updated_at 由数据库默认值生成）。 */
    @Insert("INSERT INTO product_order "
            + "(scenic_area_id, product_id, product_snapshot, user_id, quantity, "
            + "contact_name, contact_phone, remark, status) "
            + "VALUES (#{scenicAreaId}, #{productId}, #{productSnapshot}, #{userId}, #{quantity}, "
            + "#{contactName}, #{contactPhone}, #{remark}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProductOrder order);

    /** 内联查询产品（下单时构建快照），避免与产品模块 Mapper 耦合。 */
    @Select("SELECT id, scenic_area_id, name, price, description, image, stock, category, status, "
            + "created_at, updated_at FROM product WHERE id = #{id} LIMIT 1")
    Product findProductById(@Param("id") Long id);

    /** 管理端：按 ID 查询订单。 */
    @Select("SELECT id, scenic_area_id, product_id, product_snapshot, user_id, quantity, "
            + "contact_name, contact_phone, remark, status, handle_remark, created_at, updated_at "
            + "FROM product_order WHERE id = #{id} LIMIT 1")
    ProductOrder findById(@Param("id") Long id);

    /** 管理端：分页查询（scenicAreaId 为 NULL 时表示不过滤景区，status 可选）。 */
    @Select("""
            <script>
            SELECT id, scenic_area_id, product_id, product_snapshot, user_id, quantity,
                   contact_name, contact_phone, remark, status, handle_remark, created_at, updated_at
            FROM product_order
            WHERE 1 = 1
            <if test="scenicAreaId != null">
                AND scenic_area_id = #{scenicAreaId}
            </if>
            <if test="status != null and status != ''">
                AND status = #{status}
            </if>
            ORDER BY created_at DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<ProductOrder> findPage(@Param("scenicAreaId") Long scenicAreaId,
                                @Param("status") String status,
                                @Param("offset") int offset,
                                @Param("pageSize") int pageSize);

    /** 管理端：分页计数（与 findPage 同过滤条件）。 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM product_order
            WHERE 1 = 1
            <if test="scenicAreaId != null">
                AND scenic_area_id = #{scenicAreaId}
            </if>
            <if test="status != null and status != ''">
                AND status = #{status}
            </if>
            </script>
            """)
    long count(@Param("scenicAreaId") Long scenicAreaId, @Param("status") String status);

    /** 管理端：更新状态 + 处理备注 + 更新时间。 */
    @Update("UPDATE product_order SET status = #{status}, handle_remark = #{handleRemark}, "
            + "updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("handleRemark") String handleRemark);
}
