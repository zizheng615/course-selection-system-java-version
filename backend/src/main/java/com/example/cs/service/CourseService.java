package com.example.cs.service;

import com.example.cs.common.BusinessException;
import com.example.cs.common.ErrorCode;
import com.example.cs.common.PageResult;
import com.example.cs.dto.CourseDTO;
import com.example.cs.entity.Course;
import com.example.cs.repository.CourseRepository;
import com.example.cs.repository.CourseScheduleRepository;
import com.example.cs.repository.EnrollmentRepository;
import com.example.cs.repository.TeacherRepository;
import com.example.cs.support.SqlUtils;
import com.example.cs.vo.CourseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepo;
    private final TeacherRepository teacherRepo;
    private final EnrollmentRepository enrollRepo;
    private final CourseScheduleRepository scheduleRepo;
    private final SemesterResolver semesterResolver;

    @Transactional(readOnly = true)
    public PageResult<CourseVO> page(String keyword, String semester, String status, int page, int size) {
        Page<Object[]> result = courseRepo.searchCourses(
                SqlUtils.blankIfNull(keyword),
                SqlUtils.blankIfNull(semester),
                SqlUtils.blankIfNull(status),
                PageRequest.of(Math.max(page - 1, 0), size));
        return PageResult.of(result, CourseVO::ofListRow);
    }

    @Transactional(readOnly = true)
    public CourseVO findById(Long id) {
        Course course = courseRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "课程不存在"));
        CourseVO vo = CourseVO.of(course);
        teacherRepo.findById(course.getTeacherId())
                .ifPresent(t -> vo.setTeacherName(t.getName()));
        return vo;
    }

    @Transactional(readOnly = true)
    public List<String> semesters() {
        return courseRepo.findDistinctSemesters();
    }

    /** 教师本人的授课列表 */
    @Transactional(readOnly = true)
    public List<CourseVO> listByTeacher(Long teacherId, String semester) {
        String sem = semesterResolver.resolve(semester);
        return courseRepo.findByTeacherIdAndSemesterOrderById(teacherId, sem)
                .stream().map(CourseVO::of).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public CourseVO create(CourseDTO dto) {
        if (courseRepo.existsByCourseCodeAndSemester(dto.getCourseCode().trim(), dto.getSemester().trim())) {
            throw new BusinessException(ErrorCode.DUPLICATE_KEY,
                    "该学期下课程代码已存在：" + dto.getCourseCode());
        }
        if (!teacherRepo.existsById(dto.getTeacherId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "授课教师不存在");
        }

        Course course = new Course();
        applyDto(course, dto);
        course.setEnrolledCount(0);
        courseRepo.save(course);
        return findById(course.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public CourseVO update(Long id, CourseDTO dto) {
        Course course = courseRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "课程不存在"));

        boolean codeChanged = !course.getCourseCode().equals(dto.getCourseCode().trim())
                || !course.getSemester().equals(dto.getSemester().trim());
        if (codeChanged && courseRepo.existsByCourseCodeAndSemester(
                dto.getCourseCode().trim(), dto.getSemester().trim())) {
            throw new BusinessException(ErrorCode.DUPLICATE_KEY,
                    "该学期下课程代码已存在：" + dto.getCourseCode());
        }
        if (!teacherRepo.existsById(dto.getTeacherId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "授课教师不存在");
        }
        // 容量可以调大，但不能缩到比已选人数还小 —— 否则 enrolled_count <= capacity 的 CHECK 约束会被破坏
        if (dto.getCapacity() < course.getEnrolledCount()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "容量不能小于当前已选人数 " + course.getEnrolledCount());
        }

        applyDto(course, dto);
        courseRepo.save(course);
        return findById(course.getId());
    }

    /** 开放 / 关闭选课 */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        Course course = courseRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "课程不存在"));
        if (!"OPEN".equals(status) && !"CLOSED".equals(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态只能为 OPEN 或 CLOSED");
        }
        course.setStatus(status);
        courseRepo.save(course);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Course course = courseRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "课程不存在"));

        long enrollCount = enrollRepo.countByCourseId(id);
        if (enrollCount > 0) {
            throw new BusinessException(ErrorCode.COURSE_DELETE_FORBIDDEN,
                    "该课程已有 " + enrollCount + " 条选课记录，无法删除；如需停止选课请改为「关闭」");
        }
        // 外键约束下必须先删子表的上课时间
        scheduleRepo.deleteByCourseId(id);
        courseRepo.delete(course);
    }

    private void applyDto(Course course, CourseDTO dto) {
        course.setCourseCode(dto.getCourseCode().trim());
        course.setName(dto.getName().trim());
        course.setCredit(dto.getCredit());
        course.setHours(dto.getHours());
        course.setCourseType(dto.getCourseType().trim());
        course.setDept(dto.getDept().trim());
        course.setSemester(dto.getSemester().trim());
        course.setTeacherId(dto.getTeacherId());
        course.setCapacity(dto.getCapacity());
        course.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "OPEN");
        course.setIntro(dto.getIntro());
    }
}
