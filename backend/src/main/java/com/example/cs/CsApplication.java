package com.example.cs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 高校选课管理系统 - 应用入口。
 *
 * <p>启动前请确保：
 * <ol>
 *   <li>MySQL 已运行，且已执行 sql/01_schema.sql 与 sql/02_seed_data.sql</li>
 *   <li>数据库密码已通过环境变量 DB_PASSWORD 或 application.yml 配置</li>
 * </ol>
 */
@SpringBootApplication
public class CsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsApplication.class, args);
    }
}
