package com.redtour.business.mapper;

import com.redtour.business.dto.SpotFavoriteResult;
import com.redtour.business.entity.SpotFavorite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 景点收藏 Mapper（注解式 SQL）。
 * spot_favorite 唯一约束 (user_id, spot_id)。
 */
@Mapper
public interface SpotFavoriteMapper {

    /** 新增收藏（useGeneratedKeys 回填主键）。 */
    @Insert("INSERT INTO spot_favorite (user_id, spot_id) VALUES (#{userId}, #{spotId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SpotFavorite favorite);

    /** 取消收藏（按 user_id + spot_id）。 */
    @Delete("DELETE FROM spot_favorite WHERE user_id = #{userId} AND spot_id = #{spotId}")
    int delete(@Param("userId") Long userId, @Param("spotId") Long spotId);

    /** 是否已收藏（1=已收藏）。 */
    @Select("SELECT COUNT(1) FROM spot_favorite WHERE user_id = #{userId} AND spot_id = #{spotId} LIMIT 1")
    int exists(@Param("userId") Long userId, @Param("spotId") Long spotId);

    /** 当前用户收藏数（分页计数）。 */
    @Select("SELECT COUNT(1) FROM spot_favorite WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);

    /** 当前用户收藏景点分页（join spot 返回景点详情）。 */
    @Select("""
            SELECT sf.id AS favorite_id, sf.created_at AS favorited_at,
                   s.id, s.scenic_area_id, s.name, s.intro, s.images, s.longitude, s.latitude,
                   s.audio_url, s.wiki_ref, s.sort_order, s.status
            FROM spot_favorite sf
            LEFT JOIN spot s ON s.id = sf.spot_id
            WHERE sf.user_id = #{userId}
            ORDER BY sf.created_at DESC
            LIMIT #{offset}, #{pageSize}
            """)
    @Results({
            @Result(column = "favorite_id", property = "favoriteId"),
            @Result(column = "favorited_at", property = "favoritedAt"),
            @Result(column = "id", property = "id"),
            @Result(column = "scenic_area_id", property = "scenicAreaId"),
            @Result(column = "name", property = "name"),
            @Result(column = "intro", property = "intro"),
            @Result(column = "images", property = "images"),
            @Result(column = "longitude", property = "longitude"),
            @Result(column = "latitude", property = "latitude"),
            @Result(column = "audio_url", property = "audioUrl"),
            @Result(column = "wiki_ref", property = "wikiRef"),
            @Result(column = "sort_order", property = "sortOrder"),
            @Result(column = "status", property = "status")
    })
    List<SpotFavoriteResult> findByUserId(@Param("userId") Long userId,
                                          @Param("offset") int offset,
                                          @Param("pageSize") int pageSize);
}
