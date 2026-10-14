package com.example.cs.controller;

import com.example.cs.common.PageResult;
import com.example.cs.common.Result;
import com.example.cs.dto.CourseDTO;
import com.example.cs.security.RequireRole;
import com.example.cs.security.UserContext;
import com.example.cs.service.CourseService;
import com.example.cs.vo.CourseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "课程管理", description = "课程的增删改查与上下架")
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @Operation(summary = "课程分页列表（含余量）", description = "所有角色均可查看")
    @GetMapping
    public Result<PageResult<CourseVO>> page(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String semester,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return Result.ok(courseService.page(keyword, semester, status, page, size));
    }

    @Operation(summary = "学期选项列表")
    @GetMapping("/semesters")
    public Result<List<String>> semesters() {
        return Result.ok(courseService.semesters());
    }

    @Operation(summary = "教师本人的授课列表")
    @RequireRole("TEACHER")
    @GetMapping("/my")
    public Result<List<CourseVO>> myCourses(@RequestParam(required = false) String semester) {
        return Result.ok(courseService.listByTeacher(UserContext.requireTeacherId(), semester));
    }

    @Operation(summary = "课程详情")
    @GetMapping("/{id}")
    public Result<CourseVO> detail(@PathVariable Long id) {
        return Result.ok(courseService.findById(id));
    }

    @Operation(summary = "新增课程")
    @RequireRole("ADMIN")
    @PostMapping
    public Result<CourseVO> create(@Valid @RequestBody CourseDTO dto) {
        return Result.ok(courseService.create(dto));
    }

    @Operation(summary = "修改课程", description = "容量不能调小到低于已选人数")
    @RequireRole("ADMIN")
    @PutMapping("/{id}")
    public Result<CourseVO> update(@PathVariable Long id, @Valid @RequestBody CourseDTO dto) {
        return Result.ok(courseService.update(id, dto));
    }

    @Operation(summary = "开放 / 关闭选课")
    @RequireRole("ADMIN")
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestParam String status) {
        courseService.updateStatus(id, status);
        return Result.ok();
    }

    @Operation(summary = "删除课程", description = "已有选课记录时会被拒绝")
    @RequireRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return Result.ok();
    }
}
