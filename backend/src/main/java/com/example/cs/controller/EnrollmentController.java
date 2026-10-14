package com.example.cs.controller;

import com.example.cs.common.PageResult;
import com.example.cs.common.Result;
import com.example.cs.dto.AdminEnrollDTO;
import com.example.cs.dto.ScoreDTO;
import com.example.cs.dto.SelectCourseDTO;
import com.example.cs.security.RequireRole;
import com.example.cs.security.UserContext;
import com.example.cs.service.EnrollmentService;
import com.example.cs.vo.CourseStudentVO;
import com.example.cs.vo.CourseVO;
import com.example.cs.vo.EnrollmentRecordVO;
import com.example.cs.vo.MyCourseVO;
import com.example.cs.vo.TimetableVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 选课接口。
 *
 * <p>类级路径设为 {@code /api}，因为「课程学生名单」在语义上属于课程资源
 * （{@code /api/courses/{id}/students}），不便挂在 /api/enrollments 前缀下。
 */
@Tag(name = "选课", description = "选课、退课、课表与成绩")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    // ---------------- 学生 ----------------

    @Operation(summary = "选课",
            description = "依次校验：课程是否开放 → 是否已选 → 时间是否冲突 → 原子扣减容量。"
                    + "任一步失败整个事务回滚。")
    @RequireRole("STUDENT")
    @PostMapping("/enrollments")
    public Result<Void> select(@Valid @RequestBody SelectCourseDTO dto) {
        enrollmentService.selectCourse(UserContext.requireStudentId(), dto.getCourseId());
        return Result.ok();
    }

    @Operation(summary = "退课", description = "把选课记录置为 WITHDRAWN 并归还容量")
    @RequireRole("STUDENT")
    @DeleteMapping("/enrollments/{courseId}")
    public Result<Void> withdraw(@PathVariable Long courseId) {
        enrollmentService.withdrawCourse(UserContext.requireStudentId(), courseId);
        return Result.ok();
    }

    @Operation(summary = "我的已选课程（含成绩）")
    @RequireRole("STUDENT")
    @GetMapping("/enrollments/my")
    public Result<List<MyCourseVO>> myCourses(@RequestParam(required = false) String semester) {
        return Result.ok(enrollmentService.myCourses(UserContext.requireStudentId(), semester));
    }

    @Operation(summary = "我的课表", description = "按星期与节次排序")
    @RequireRole("STUDENT")
    @GetMapping("/enrollments/my/timetable")
    public Result<List<TimetableVO>> myTimetable(@RequestParam(required = false) String semester) {
        return Result.ok(enrollmentService.myTimetable(UserContext.requireStudentId(), semester));
    }

    @Operation(summary = "可选课程",
            description = "已在 SQL 中排除「已选过」与「与已选课程时间冲突」的课程")
    @RequireRole("STUDENT")
    @GetMapping("/enrollments/available")
    public Result<List<CourseVO>> available(@RequestParam(required = false) String semester) {
        return Result.ok(enrollmentService.selectableCourses(UserContext.requireStudentId(), semester));
    }

    @Operation(summary = "与已选课程冲突的课程列表",
            description = "用于向学生解释某门课为什么不可选")
    @RequireRole("STUDENT")
    @GetMapping("/enrollments/conflicts")
    public Result<List<CourseVO>> conflicts(@RequestParam(required = false) String semester) {
        return Result.ok(enrollmentService.conflictingCourses(UserContext.requireStudentId(), semester));
    }

    // ---------------- 教师 / 管理员 ----------------

    @Operation(summary = "课程学生名单（含成绩）")
    @RequireRole({"TEACHER", "ADMIN"})
    @GetMapping("/courses/{courseId}/students")
    public Result<List<CourseStudentVO>> courseStudents(@PathVariable Long courseId) {
        return Result.ok(enrollmentService.courseStudents(courseId));
    }

    @Operation(summary = "录入成绩")
    @RequireRole({"TEACHER", "ADMIN"})
    @PatchMapping("/enrollments/score")
    public Result<Void> recordScore(@Valid @RequestBody ScoreDTO dto) {
        enrollmentService.recordScore(dto.getEnrollmentId(), dto.getScore());
        return Result.ok();
    }

    // ---------------- 管理员：选课记录直接维护 ----------------
    //
    // 路径统一带 record 前缀，与学生自助的 /enrollments/{courseId} 区分开：
    // 两者都是 DELETE /api/enrollments/... ，若不多一段路径会产生映射冲突。

    @Operation(summary = "选课记录分页查询",
            description = "支持按学生/课程关键词、指定课程、选课状态筛选")
    @RequireRole("ADMIN")
    @GetMapping("/enrollments")
    public Result<PageResult<EnrollmentRecordVO>> adminPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(enrollmentService.adminPage(keyword, courseId, status, page, size));
    }

    @Operation(summary = "管理员代学生加选课",
            description = "与学生端走完全相同的校验：容量、时间冲突、重复选课都会被拒绝")
    @RequireRole("ADMIN")
    @PostMapping("/enrollments/admin")
    public Result<Void> adminEnroll(@Valid @RequestBody AdminEnrollDTO dto) {
        enrollmentService.adminEnroll(dto.getStudentId(), dto.getCourseId());
        return Result.ok();
    }

    @Operation(summary = "管理员退课（软退）", description = "置为 WITHDRAWN 并归还容量，保留修读历史")
    @RequireRole("ADMIN")
    @PatchMapping("/enrollments/record/{id}/withdraw")
    public Result<Void> adminWithdraw(@PathVariable Long id) {
        enrollmentService.adminWithdraw(id);
        return Result.ok();
    }

    @Operation(summary = "管理员删除选课记录（硬删）",
            description = "整行删除，用于清理误录数据；若原为已选状态会同步归还容量")
    @RequireRole("ADMIN")
    @DeleteMapping("/enrollments/record/{id}")
    public Result<Void> adminDelete(@PathVariable Long id) {
        enrollmentService.adminDelete(id);
        return Result.ok();
    }
}
