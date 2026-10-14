package com.example.cs.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 配置，用于接口自测与生成接口文档。
 * 访问：http://localhost:8080/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI courseSelectionOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("高校选课管理系统 API")
                        .version("1.0.0")
                        .description("课程设计原型 — 先在 /api/auth/login 登录获取 Token，"
                                + "再点击右上角 Authorize 填入即可调试其余接口。"))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SCHEME_NAME,
                        new SecurityScheme()
                                .name(SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
