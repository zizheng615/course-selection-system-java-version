package com.example.cs.controller;

import com.example.cs.common.Result;
import com.example.cs.dto.LoginDTO;
import com.example.cs.service.AuthService;
import com.example.cs.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证", description = "登录与当前用户信息")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录", description = "成功后返回 JWT，后续请求需在 Header 携带 Authorization: Bearer <token>")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    @Operation(summary = "当前登录用户信息")
    @GetMapping("/me")
    public Result<LoginVO> me() {
        return Result.ok(authService.currentUser());
    }
}
