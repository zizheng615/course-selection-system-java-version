package com.example.cs.controller;

import com.example.cs.common.Result;
import com.example.cs.dto.ScheduleDTO;
import com.example.cs.security.RequireRole;
import com.example.cs.service.ScheduleService;
import com.example.cs.vo.ScheduleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "排课管理", description = "课程上课时间的增删改查（仅管理员）")
@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @Operation(summary = "查看某门课程的上课时间", description = "所有角色均可查看")
    @GetMapping
    public Result<List<ScheduleVO>> listByCourse(@RequestParam Long courseId) {
        return Result.ok(scheduleService.listByCourse(courseId));
    }

    @Operation(summary = "新增上课时间", description = "会校验同一教师的时间冲突")
    @RequireRole("ADMIN")
    @PostMapping
    public Result<ScheduleVO> create(@Valid @RequestBody ScheduleDTO dto) {
        return Result.ok(scheduleService.create(dto));
    }

    @Operation(summary = "修改上课时间")
    @RequireRole("ADMIN")
    @PutMapping("/{id}")
    public Result<ScheduleVO> update(@PathVariable Long id, @Valid @RequestBody ScheduleDTO dto) {
        return Result.ok(scheduleService.update(id, dto));
    }

    @Operation(summary = "删除上课时间")
    @RequireRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return Result.ok();
    }
}
