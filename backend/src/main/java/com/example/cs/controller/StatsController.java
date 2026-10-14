package com.example.cs.controller;

import com.example.cs.common.Result;
import com.example.cs.security.RequireRole;
import com.example.cs.service.StatsService;
import com.example.cs.vo.MajorStatVO;
import com.example.cs.vo.PopularCourseVO;
import com.example.cs.vo.TeacherWorkloadVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "统计报表", description = "聚合查询与排行（仅管理员）")
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "热门课程 Top-N", description = "按选课人数降序，含满座率")
    @GetMapping("/popular-courses")
    public Result<List<PopularCourseVO>> popularCourses(@RequestParam(required = false) String semester,
                                                        @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(statsService.popularCourses(semester, limit));
    }

    @Operation(summary = "各专业选课分布")
    @GetMapping("/major-distribution")
    public Result<List<MajorStatVO>> majorDistribution() {
        return Result.ok(statsService.majorDistribution());
    }

    @Operation(summary = "教师工作量统计")
    @GetMapping("/teacher-workload")
    public Result<List<TeacherWorkloadVO>> teacherWorkload(@RequestParam(required = false) String semester) {
        return Result.ok(statsService.teacherWorkload(semester));
    }
}
