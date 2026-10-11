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
        // 1. 超级管理员 admin
        if (userMapper.countByUsername("admin") == 0) {
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(AuthServiceImpl.getPasswordEncoder().encode("admin123"));
            admin.setNickname("超级管理员");
            admin.setRole("super_admin");
            admin.setStatus(1);
            admin.setDeleted(0);
            userMapper.insert(admin);
            log.info("[初始化] 创建超级管理员 admin / admin123 成功");
        }

        // 2. 遵义景区管理员 zunyi_admin（绑定遵义会议纪念馆 scenicAreaId=1）
        if (userMapper.countByUsername("zunyi_admin") == 0) {
            SysUser scenicAdmin = new SysUser();
            scenicAdmin.setUsername("zunyi_admin");
            scenicAdmin.setPassword(AuthServiceImpl.getPasswordEncoder().encode("admin123"));
            scenicAdmin.setNickname("遵义景区管理员");
            scenicAdmin.setRole("admin");
            scenicAdmin.setScenicAreaId(1L);
            scenicAdmin.setStatus(1);
            scenicAdmin.setDeleted(0);
            userMapper.insert(scenicAdmin);
            log.info("[初始化] 创建景区管理员 zunyi_admin / admin123 成功（绑定遵义会议纪念馆）");
        }

        // 3. 游客测试账号 test_tourist
        if (userMapper.countByUsername("test_tourist") == 0) {
            SysUser tourist = new SysUser();
            tourist.setUsername("test_tourist");
            tourist.setPassword(AuthServiceImpl.getPasswordEncoder().encode("123456"));
            tourist.setNickname("测试游客");
            tourist.setRole("tourist");
            tourist.setStatus(1);
            tourist.setDeleted(0);
            userMapper.insert(tourist);
            log.info("[初始化] 创建游客账号 test_tourist / 123456 成功");
        }
    }
}
