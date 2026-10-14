package com.example.cs.controller;

import com.example.cs.common.PageResult;
import com.example.cs.common.Result;
import com.example.cs.dto.UserCredentialDTO;
import com.example.cs.security.RequireRole;
import com.example.cs.service.UserService;
import com.example.cs.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "账号管理", description = "登录账号与密码的维护（仅管理员）")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class UserController {

    private final UserService userService;

    @Operation(summary = "账号分页列表", description = "含关联的学生/教师业务信息")
    @GetMapping
    public Result<PageResult<UserVO>> page(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String role,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return Result.ok(userService.page(keyword, role, page, size));
    }

    @Operation(summary = "修改登录账号 / 重置密码",
            description = "password 留空表示只改账号、不改密码")
    @PutMapping("/{id}/credentials")
    public Result<Void> updateCredentials(@PathVariable Long id,
                                          @Valid @RequestBody UserCredentialDTO dto) {
        userService.updateCredentials(id, dto);
        return Result.ok();
    }

    @Operation(summary = "启用 / 禁用账号", description = "不能禁用当前登录的账号")
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return Result.ok();
    }

    @Operation(summary = "删除账号", description = "已绑定学生/教师信息时会被拒绝")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }
}
