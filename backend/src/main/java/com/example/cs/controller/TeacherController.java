package com.example.cs.controller;

import com.example.cs.common.PageResult;
import com.example.cs.common.Result;
import com.example.cs.dto.TeacherDTO;
import com.example.cs.security.RequireRole;
import com.example.cs.service.TeacherService;
import com.example.cs.vo.TeacherVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "教师管理", description = "教师的增删改查（仅管理员）")
@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class TeacherController {

    private final TeacherService teacherService;

    @Operation(summary = "教师分页列表")
    @GetMapping
    public Result<PageResult<TeacherVO>> page(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String dept,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return Result.ok(teacherService.page(keyword, dept, page, size));
    }

    @Operation(summary = "全部教师（课程表单的授课教师下拉框用）")
    @GetMapping("/all")
    public Result<List<TeacherVO>> all() {
        return Result.ok(teacherService.listAll());
    }

    @Operation(summary = "新增教师")
    @PostMapping
    public Result<TeacherVO> create(@Valid @RequestBody TeacherDTO dto) {
        return Result.ok(teacherService.create(dto));
    }

    @Operation(summary = "修改教师")
    @PutMapping("/{id}")
    public Result<TeacherVO> update(@PathVariable Long id, @Valid @RequestBody TeacherDTO dto) {
        return Result.ok(teacherService.update(id, dto));
    }

    @Operation(summary = "删除教师", description = "名下还有课程时会被拒绝")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        teacherService.delete(id);
        return Result.ok();
    }
}
