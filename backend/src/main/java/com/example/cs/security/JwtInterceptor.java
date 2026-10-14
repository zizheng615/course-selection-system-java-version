package com.example.cs.security;

import com.example.cs.common.BusinessException;
import com.example.cs.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 认证与鉴权拦截器。
 *
 * <p>流程：解析 Authorization 头 → 写入 {@link UserContext} → 校验 {@link RequireRole}。
 * 无论请求成功与否，都会在 afterCompletion 中清理 ThreadLocal，防止线程复用导致的数据串号。
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行 CORS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "未登录，请先登录");
        }

        LoginUser user = jwtUtil.parse(header.substring(7));
        if (user == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "登录已过期，请重新登录");
        }
        UserContext.set(user);

        // 角色校验：方法上的注解优先于类上的注解
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole != null && Arrays.stream(requireRole.value()).noneMatch(user.role()::equals)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION,
                    "无权访问该资源，需要角色：" + String.join("/", requireRole.value()));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
