package com.redtour.business;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 红色文旅智能导览系统 - 业务后端启动类
 * 端口 8000，提供 RESTful 业务接口，对接 AI 引擎（HTTP）与树莓派硬件（TCP）
 */
@SpringBootApplication
@MapperScan("com.redtour.business.mapper") // 扫描 mapper 层
@EnableScheduling // 启用硬件 TCP 心跳重连、离线数据同步等定时任务
public class RedTourBusinessApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedTourBusinessApplication.class, args);
    }
}
