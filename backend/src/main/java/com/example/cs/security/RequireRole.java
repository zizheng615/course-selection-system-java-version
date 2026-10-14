package com.example.cs.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色控制注解。标注在 Controller 类或方法上，由 {@link JwtInterceptor} 校验。
 *
 * <pre>
 *   &#64;RequireRole("ADMIN")                仅管理员
 *   &#64;RequireRole({"ADMIN", "TEACHER"})   管理员或教师
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许访问的角色列表，满足其一即可。 */
    String[] value();
}
