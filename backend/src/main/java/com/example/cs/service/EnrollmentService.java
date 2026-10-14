package com.example.cs.service;

import com.example.cs.common.BusinessException;
import com.example.cs.common.ErrorCode;
import com.example.cs.common.PageResult;
import com.example.cs.entity.Course;
import com.example.cs.entity.Enrollment;
import com.example.cs.repository.CourseRepository;
import com.example.cs.repository.EnrollmentRepository;
import com.example.cs.repository.StudentRepository;
import com.example.cs.support.SqlUtils;
import com.example.cs.vo.CourseStudentVO;
import com.example.cs.vo.CourseVO;
import com.example.cs.vo.EnrollmentRecordVO;
import com.example.cs.vo.MyCourseVO;
import com.example.cs.vo.TimetableVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 选课核心业务。
 *
 * <p><b>并发控制是这里的重点。</b>选课本质是「有限资源被并发争抢」：
 * 若不加以控制，两个事务可能同时读到「还剩 1 个名额」，
 * 各自判断通过后都去写入，最终选课人数超过容量（超卖）。
 *
 * <p>本实现采用「原子扣减」而非悲观锁：
 * <pre>
 *   UPDATE course SET enrolled_count = enrolled_count + 1
 *    WHERE id = ? AND status = 'OPEN' AND enrolled_count &lt; capacity
 * </pre>
 * 判断与扣减在同一条 SQL 内完成，InnoDB 的行锁保证其原子性，
 * 调用方只需检查影响行数：为 1 表示抢到名额，为 0 表示已被抢完。
 * 相比「先 SELECT 再 UPDATE」，它把竞态窗口压缩到了零，
 * 同时避免了悲观锁带来的锁等待与死锁风险。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final CourseRepository courseRepo;
    private final EnrollmentRepository enrollRepo;
    private final StudentRepository studentRepo;
    private final SemesterResolver semesterResolver;

    // =========================================================================
    // 核心业务：选课 / 退课
    // =========================================================================

    /**
     * 选课。
     *
     * <p>整个方法是一个事务：任何一步失败都会回滚，
     * 不会出现「容量扣了但选课记录没写」这类脏状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void selectCourse(Long studentId, Long courseId) {
        Course course = courseRepo.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "课程不存在"));

        // 1. 状态校验
        if (!"OPEN".equals(course.getStatus())) {
            throw new BusinessException(ErrorCode.COURSE_CLOSED, "该课程未开放选课");
        }

        // 2. 重复选课校验
        //    唯一索引 uk_student_course 只约束 (student_id, course_id) 这一对值，
        //    退课是把状态置为 WITHDRAWN 而非删除行，因此重新选课时必须复用原记录，
        //    否则会撞上唯一索引。
        Optional<Enrollment> existing = enrollRepo.findByStudentIdAndCourseId(studentId, courseId);
        if (existing.isPresent() && "SELECTED".equals(existing.get().getStatus())) {
            throw new BusinessException(ErrorCode.ALREADY_SELECTED, "已选过该课程，请勿重复选课");
        }

        // 3. 时间冲突校验（区间重叠判定，全部在 SQL 内完成）
        if (enrollRepo.countTimeConflict(studentId, courseId) > 0) {
            throw new BusinessException(ErrorCode.TIME_CONFLICT,
                    "该课程与已选课程上课时间冲突，无法选课");
        }

        // 4. 原子扣减容量 —— 并发安全的关键
        if (courseRepo.increaseEnrolledCount(courseId) == 0) {
            // 影响行数为 0：说明在本次事务读取课程之后、执行更新之前，
            // 名额已被其他事务抢完（或课程被关闭）
            throw new BusinessException(ErrorCode.COURSE_FULL, "课程容量已满或已关闭，选课失败");
        }

        // 5. 写入选课记录
        if (existing.isPresent()) {
            Enrollment e = existing.get();
            e.setStatus("SELECTED");
            e.setScore(null);
            enrollRepo.save(e);
        } else {
            Enrollment e = new Enrollment();
            e.setStudentId(studentId);
            e.setCourseId(courseId);
            e.setStatus("SELECTED");
            enrollRepo.save(e);
        }
    }

    /**
     * 退课。
     */
    @Transactional(rollbackFor = Exception.class)
    public void withdrawCourse(Long studentId, Long courseId) {
        Enrollment enrollment = enrollRepo
                .findByStudentIdAndCourseIdAndStatus(studentId, courseId, "SELECTED")
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_SELECTED, "未选该课程或已退课"));

        enrollment.setStatus("WITHDRAWN");
        enrollRepo.save(enrollment);

        // 归还容量。若返回 0，说明 counters 已经为 0，属于数据不一致，
        // 此时抛异常回滚整个事务，避免"退课成功但计数没还"的脏状态。
        if (courseRepo.decreaseEnrolledCount(courseId) == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "课程容量计数异常，退课已回滚");
        }
    }

    // =========================================================================
    // 查询
    // =========================================================================

    /** 学生已选课程（含成绩） */
    @Transactional(readOnly = true)
    public List<MyCourseVO> myCourses(Long studentId, String semester) {
        return enrollRepo.findMyCourses(studentId, SqlUtils.blankIfNull(semester))
                .stream().map(MyCourseVO::of).toList();
    }

    /** 学生课表（按星期与节次排序） */
    @Transactional(readOnly = true)
    public List<TimetableVO> myTimetable(Long studentId, String semester) {
        String sem = semesterResolver.resolve(semester);
        return enrollRepo.findTimetable(studentId, sem)
                .stream().map(TimetableVO::of).toList();
    }

    /** 可选课程：已排除「已选过」和「时间冲突」的课程 */
    @Transactional(readOnly = true)
    public List<CourseVO> selectableCourses(Long studentId, String semester) {
        String sem = semesterResolver.resolve(semester);
        return courseRepo.findSelectableCourses(studentId, sem)
                .stream().map(CourseVO::ofSelectableRow).toList();
    }

    /** 与该学生已选课程确实冲突的课程（用于前端解释"为什么这门课不可选"） */
    @Transactional(readOnly = true)
    public List<CourseVO> conflictingCourses(Long studentId, String semester) {
        String sem = semesterResolver.resolve(semester);
        return courseRepo.findConflictingCourses(studentId, sem).stream()
                .map(r -> {
                    CourseVO vo = new CourseVO();
                    vo.setId(SqlUtils.toLong(r[0]));
                    vo.setCourseCode(SqlUtils.toStr(r[1]));
                    vo.setName(SqlUtils.toStr(r[2]));
                    vo.setConflictWith(SqlUtils.toStr(r[3]));
                    return vo;
                }).toList();
    }

    /** 课程学生名单 */
    @Transactional(readOnly = true)
    public List<CourseStudentVO> courseStudents(Long courseId) {
        return enrollRepo.findCourseStudents(courseId)
                .stream().map(CourseStudentVO::of).toList();
    }

    // =========================================================================
    // 成绩
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public void recordScore(Long enrollmentId, BigDecimal score) {
        Enrollment enrollment = enrollRepo.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "选课记录不存在"));
        if (!"SELECTED".equals(enrollment.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_SELECTED, "该学生已退课，无法录入成绩");
        }
        enrollment.setScore(score);
        enrollRepo.save(enrollment);
    }

    // =========================================================================
    // 管理员：选课记录管理
    //
    // 这些方法只服务于"管理员直接维护 enrollment 表"的场景，
    // 与上面的学生自助流程并存但互不影响。
    // =========================================================================

    /** 全量选课记录分页查询 */
    @Transactional(readOnly = true)
    public PageResult<EnrollmentRecordVO> adminPage(String keyword, Long courseId,
                                                    String status, int page, int size) {
        Page<Object[]> result = enrollRepo.searchEnrollments(
                SqlUtils.blankIfNull(keyword),
                courseId == null ? 0L : courseId,
                SqlUtils.blankIfNull(status),
                PageRequest.of(Math.max(page - 1, 0), size));
        return PageResult.of(result, EnrollmentRecordVO::of);
    }

    /**
     * 管理员代学生加选课。
     *
     * <p>刻意**复用学生端的 selectCourse**，不改写一套"管理员专用"逻辑：
     * 容量上限、时间冲突、重复选课三类校验完全一致，
     * 保证不管从哪个入口写入，enrollment 表的不变式都成立。
     */
    @Transactional(rollbackFor = Exception.class)
    public void adminEnroll(Long studentId, Long courseId) {
        studentRepo.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "学生不存在"));
        selectCourse(studentId, courseId);
    }

    /** 管理员退课：软退，置为 WITHDRAWN 并归还容量，保留修读历史 */
    @Transactional(rollbackFor = Exception.class)
    public void adminWithdraw(Long enrollmentId) {
        Enrollment enrollment = requireEnrollment(enrollmentId);
        if (!"SELECTED".equals(enrollment.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_SELECTED, "该记录已是退课状态");
        }
        enrollment.setStatus("WITHDRAWN");
        enrollRepo.save(enrollment);
        if (courseRepo.decreaseEnrolledCount(enrollment.getCourseId()) == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "课程容量计数异常，退课已回滚");
        }
    }

    /**
     * 管理员删除记录：硬删整行，用于清理误录数据。
     *
     * <p>与软退的区别是记录不再留存；若原状态为 SELECTED，
     * 必须在删除前把名额还回去，否则容量计数会永久虚高。
     */
    @Transactional(rollbackFor = Exception.class)
    public void adminDelete(Long enrollmentId) {
        Enrollment enrollment = requireEnrollment(enrollmentId);
        if ("SELECTED".equals(enrollment.getStatus())
                && courseRepo.decreaseEnrolledCount(enrollment.getCourseId()) == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "课程容量计数异常，删除已回滚");
        }
        enrollRepo.delete(enrollment);
    }

    private Enrollment requireEnrollment(Long enrollmentId) {
        return enrollRepo.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "选课记录不存在"));
    }
}
