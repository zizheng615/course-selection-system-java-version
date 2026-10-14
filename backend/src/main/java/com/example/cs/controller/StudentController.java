package com.example.cs.controller;

import com.example.cs.common.PageResult;
import com.example.cs.common.Result;
import com.example.cs.dto.StudentDTO;
import com.example.cs.security.RequireRole;
import com.example.cs.service.StudentService;
import com.example.cs.vo.StudentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "学生管理", description = "学生的增删改查（仅管理员）")
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class StudentController {

    private final StudentService studentService;

    @Operation(summary = "学生分页列表")
    @GetMapping
    public Result<PageResult<StudentVO>> page(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String major,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return Result.ok(studentService.page(keyword, major, page, size));
    }

    @Operation(summary = "专业选项列表（筛选下拉框用）")
    @GetMapping("/majors")
    public Result<List<String>> majors() {
        return Result.ok(studentService.majors());
    }

    @Operation(summary = "新增学生", description = "同时创建登录账号，默认账号=学号、密码=123456")
    @PostMapping
    public Result<StudentVO> create(@Valid @RequestBody StudentDTO dto) {
        return Result.ok(studentService.create(dto));
    }

    @Operation(summary = "修改学生")
    @PutMapping("/{id}")
    public Result<StudentVO> update(@PathVariable Long id, @Valid @RequestBody StudentDTO dto) {
        return Result.ok(studentService.update(id, dto));
    }

    @Operation(summary = "删除学生", description = "存在选课记录时会被拒绝")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        studentService.delete(id);
        return Result.ok();
    }
}
