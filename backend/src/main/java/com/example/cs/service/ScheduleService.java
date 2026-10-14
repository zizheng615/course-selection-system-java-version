package com.example.cs.service;

import com.example.cs.common.BusinessException;
import com.example.cs.common.ErrorCode;
import com.example.cs.dto.ScheduleDTO;
import com.example.cs.entity.Course;
import com.example.cs.entity.CourseSchedule;
import com.example.cs.repository.CourseRepository;
import com.example.cs.repository.CourseScheduleRepository;
import com.example.cs.vo.ScheduleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final CourseScheduleRepository scheduleRepo;
    private final CourseRepository courseRepo;

    @Transactional(readOnly = true)
    public List<ScheduleVO> listByCourse(Long courseId) {
        return scheduleRepo.findByCourseId(courseId).stream().map(ScheduleVO::of).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO create(ScheduleDTO dto) {
        validate(dto);
        Course course = requireCourse(dto.getCourseId());
        checkTeacherConflict(course, dto, 0L);

        CourseSchedule schedule = new CourseSchedule();
        applyDto(schedule, dto);
        scheduleRepo.save(schedule);
        return ScheduleVO.of(schedule);
    }

    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO update(Long id, ScheduleDTO dto) {
        validate(dto);
        CourseSchedule schedule = scheduleRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "上课时间记录不存在"));
        Course course = requireCourse(dto.getCourseId());
        checkTeacherConflict(course, dto, id);

        applyDto(schedule, dto);
        scheduleRepo.save(schedule);
        return ScheduleVO.of(schedule);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (!scheduleRepo.existsById(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "上课时间记录不存在");
        }
        scheduleRepo.deleteById(id);
    }

    // ------------------------------------------------------------------

    private Course requireCourse(Long courseId) {
        return courseRepo.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "课程不存在"));
    }

    /** 节次 / 周次的先后关系校验。数据库有 CHECK 约束兜底，这里提前给出可读提示。 */
    private void validate(ScheduleDTO dto) {
        if (dto.getStartSection() > dto.getEndSection()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "开始节次不能大于结束节次");
        }
        if (dto.getStartWeek() > dto.getEndWeek()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "起始周不能大于结束周");
        }
    }

    /**
     * 排课合理性检查：同一教师在同一学期、同一星期，
     * 不能同时出现在两门课的节次重叠区间里。
     */
    private void checkTeacherConflict(Course course, ScheduleDTO dto, Long excludeId) {
        int conflict = scheduleRepo.countTeacherConflict(
                course.getTeacherId(), course.getSemester(), dto.getWeekday(),
                dto.getStartSection(), dto.getEndSection(), excludeId);
        if (conflict > 0) {
            throw new BusinessException(ErrorCode.TIME_CONFLICT,
                    "该教师在" + ScheduleVO.weekdayName(dto.getWeekday())
                            + " " + dto.getStartSection() + "-" + dto.getEndSection()
                            + " 节已有其他课程安排，排课冲突");
        }
    }

    private void applyDto(CourseSchedule schedule, ScheduleDTO dto) {
        schedule.setCourseId(dto.getCourseId());
        schedule.setWeekday(dto.getWeekday());
        schedule.setStartSection(dto.getStartSection());
        schedule.setEndSection(dto.getEndSection());
        schedule.setStartWeek(dto.getStartWeek());
        schedule.setEndWeek(dto.getEndWeek());
        schedule.setClassroom(dto.getClassroom());
    }
}
