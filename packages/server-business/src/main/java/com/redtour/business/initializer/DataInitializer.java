package com.redtour.business.initializer;

import com.redtour.business.entity.SysUser;
import com.redtour.business.mapper.UserMapper;
import com.redtour.business.service.impl.AuthServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时初始化数据：
 * 1. 若无 admin 超级管理员，则自动创建（默认密码 admin123，BCrypt 加密）
 *    保证团队成员启动后立即可登录后台
 * 2. 若已有 admin，则跳过（避免覆盖线上正式账号密码）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;

    @Override
    public void run(String... args) {
        if (userMapper.countAdmin() > 0) {
            log.info("[初始化] admin 账号已存在，跳过初始化");
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(AuthServiceImpl.getPasswordEncoder().encode("admin123"));
        admin.setNickname("超级管理员");
        admin.setRole("super_admin");
        admin.setStatus(1);
        admin.setDeleted(0);
        userMapper.insert(admin);
        log.info("[初始化] 创建超级管理员 admin / admin123 成功（首次登录请尽快修改密码）");
    }
}
