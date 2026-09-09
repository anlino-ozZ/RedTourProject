package com.redtour.business.mapper;

import com.redtour.business.entity.AskLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/**
 * 问答记录 Mapper。
 */
@Mapper
public interface AskLogMapper {

    /** 写入一次问答记录。 */
    @Insert("INSERT INTO ask_log "
            + "(scenic_area_id, question, answer, sources, duration_ms, device_id) "
            + "VALUES (#{scenicAreaId}, #{question}, #{answer}, #{sources}, #{durationMs}, #{deviceId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AskLog askLog);
}
