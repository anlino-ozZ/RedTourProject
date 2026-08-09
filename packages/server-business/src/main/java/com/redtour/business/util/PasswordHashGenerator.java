package com.redtour.business.util;

import com.redtour.business.service.impl.AuthServiceImpl;

/**
 * BCrypt 密码哈希生成工具（手动生成哈希写 SQL 时使用）
 * 运行方式：IDE 中右键 Run main() 或：
 *   cd packages/server-business
 *   mvn exec:java -Dexec.mainClass=com.redtour.business.util.PasswordHashGenerator -Dexec.args="admin123"
 *
 * 输出示例：
 *   明文: admin123
 *   BCrypt: $2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
 */
public class PasswordHashGenerator {

    public static void main(String[] args) {
        String raw = args.length > 0 ? args[0] : "admin123";
        String encoded = AuthServiceImpl.getPasswordEncoder().encode(raw);
        System.out.println("明文密码: " + raw);
        System.out.println("BCrypt 哈希: " + encoded);
        System.out.println();
        System.out.println("-- SQL 插入示例：");
        System.out.println("INSERT INTO sys_user (username, password, nickname, role, status)");
        System.out.println("VALUES ('admin', '" + encoded + "', '超级管理员', 'super_admin', 1);");
    }
}
