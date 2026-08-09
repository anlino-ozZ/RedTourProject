package com.redtour.business.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 健康检查 Mapper（演示 mapper 分层，SELECT 1 不依赖业务表）
 * 业务 mapper 接口统一放本包，由启动类 @MapperScan 扫描
 */
@Mapper
public interface HealthMapper {

    /** 数据库连通性检查 */
    @Select("SELECT 1")
    Integer checkDb();
}
