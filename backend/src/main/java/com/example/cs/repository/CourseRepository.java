package com.example.cs.repository;

import com.example.cs.entity.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByCourseCodeAndSemester(String courseCode, String semester);

    boolean existsByCourseCodeAndSemester(String courseCode, String semester);

    List<Course> findByTeacherIdAndSemesterOrderById(Long teacherId, String semester);

    List<Course> findBySemesterOrderById(String semester);

    /** 供前端学期下拉框使用 */
    @Query("SELECT DISTINCT c.semester FROM Course c ORDER BY c.semester DESC")
    List<String> findDistinctSemesters();

    long countByTeacherId(Long teacherId);

    // =========================================================================
    // 并发控制核心：原子扣减 / 归还容量
    //
    // 「判断」与「扣减」在一条 UPDATE 内完成，依赖 InnoDB 行锁保证原子性，
    // 调用方只需看返回值 affectedRows 是否为 1 即可判断是否抢到名额。
    // 对比「先 SELECT 查余量、再 UPDATE」的写法：两个事务可能读到同一个余量值，
    // 导致最终选课人数超过容量上限（超卖）。
    //
    // clearAutomatically = true 是必需的：@Modifying 的批量更新绕过持久化上下文，
    // 若不清空，紧接着的 findById 会命中一级缓存里的陈旧实体。
    // =========================================================================

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE course
               SET enrolled_count = enrolled_count + 1
             WHERE id = :id
               AND status = 'OPEN'
               AND enrolled_count < capacity
            """, nativeQuery = true)
    int increaseEnrolledCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE course
               SET enrolled_count = enrolled_count - 1
             WHERE id = :id
               AND enrolled_count > 0
            """, nativeQuery = true)
    int decreaseEnrolledCount(@Param("id") Long id);

    // =========================================================================
    // 课程分页列表（多表连接，带教师姓名与剩余容量）
    //
    // 条件参数统一在 Service 层归一化为「空字符串」而非 null，
    // 从而可以用 :param = '' 这种简单比较，避开 NULL 比较的三值逻辑陷阱。
    // =========================================================================

    @Query(value = """
            SELECT c.id,
                   c.course_code,
                   c.name,
                   c.credit,
                   c.hours,
                   c.course_type,
                   c.dept,
                   c.semester,
                   c.teacher_id,
                   t.name  AS teacher_name,
                   t.title AS teacher_title,
                   c.capacity,
                   c.enrolled_count,
                   (c.capacity - c.enrolled_count) AS remaining,
                   c.status,
                   c.intro
              FROM course c
              JOIN teacher t ON t.id = c.teacher_id
             WHERE (:semester = '' OR c.semester = :semester)
               AND (:status   = '' OR c.status   = :status)
               AND (:keyword  = '' OR c.name LIKE CONCAT('%', :keyword, '%')
                                   OR c.course_code LIKE CONCAT('%', :keyword, '%'))
             ORDER BY c.id
            """,
            countQuery = """
            SELECT COUNT(*)
              FROM course c
             WHERE (:semester = '' OR c.semester = :semester)
               AND (:status   = '' OR c.status   = :status)
               AND (:keyword  = '' OR c.name LIKE CONCAT('%', :keyword, '%')
                                   OR c.course_code LIKE CONCAT('%', :keyword, '%'))
            """,
            nativeQuery = true)
    Page<Object[]> searchCourses(@Param("keyword") String keyword,
                                 @Param("semester") String semester,
                                 @Param("status") String status,
                                 Pageable pageable);

    /**
     * 可选课程：排除已选过的、且与已选课程时间不冲突的课程。
     *
     * <p>时间冲突判定采用区间重叠公式：<br>
     * {@code A.start <= B.end AND B.start <= A.end} <br>
     * 节次与周次两个维度都需满足。整个判定在 SQL 内完成，
     * 无需把学生课表读取到应用内存中循环比较。
     */
    @Query(value = """
            SELECT c.id,
                   c.course_code,
                   c.name,
                   c.credit,
                   c.course_type,
                   c.dept,
                   t.name  AS teacher_name,
                   t.title AS teacher_title,
                   c.capacity,
                   c.enrolled_count,
                   (c.capacity - c.enrolled_count) AS remaining
              FROM course c
              JOIN teacher t ON t.id = c.teacher_id
             WHERE c.semester = :semester
               AND c.status   = 'OPEN'
               AND c.enrolled_count < c.capacity
               AND NOT EXISTS (
                     SELECT 1 FROM enrollment e
                      WHERE e.student_id = :studentId
                        AND e.course_id  = c.id
                        AND e.status     = 'SELECTED'
                   )
               AND NOT EXISTS (
                     SELECT 1
                       FROM course_schedule cs_new
                       JOIN course_schedule cs_old ON cs_old.weekday = cs_new.weekday
                       JOIN enrollment e2
                            ON e2.course_id  = cs_old.course_id
                           AND e2.student_id = :studentId
                           AND e2.status     = 'SELECTED'
                      WHERE cs_new.course_id = c.id
                        AND cs_new.start_section <= cs_old.end_section
                        AND cs_old.start_section <= cs_new.end_section
                        AND cs_new.start_week    <= cs_old.end_week
                        AND cs_old.start_week    <= cs_new.end_week
                   )
             ORDER BY remaining DESC, c.id
            """, nativeQuery = true)
    List<Object[]> findSelectableCourses(@Param("studentId") Long studentId,
                                         @Param("semester") String semester);

    /**
     * 反向查询：与该学生已选课程真正冲突的课程（用于演示与前端提示）。
     *
     * <p>必须排除「学生已选中的课程本身」：区间重叠判定中，一门课的课表与它自己的
     * 课表必然满足 {@code A.start <= B.end AND B.start <= A.end}，
     * 若不排除，已选课程会全部作为「与自己冲突」出现在结果里。
     */
    @Query(value = """
            SELECT DISTINCT
                   c.id,
                   c.course_code,
                   c.name,
                   old_c.name AS conflict_with
              FROM course c
              JOIN course_schedule cs_new ON cs_new.course_id = c.id
              JOIN course_schedule cs_old ON cs_old.weekday    = cs_new.weekday
              JOIN enrollment e2
                   ON e2.course_id  = cs_old.course_id
                  AND e2.student_id = :studentId
                  AND e2.status     = 'SELECTED'
              JOIN course old_c ON old_c.id = cs_old.course_id
             WHERE c.semester = :semester
               AND cs_new.start_section <= cs_old.end_section
               AND cs_old.start_section <= cs_new.end_section
               AND cs_new.start_week    <= cs_old.end_week
               AND cs_old.start_week    <= cs_new.end_week
               -- 排除学生已选中的课程本身（否则每门已选课都会"与自己冲突"）
               AND NOT EXISTS (
                     SELECT 1 FROM enrollment e3
                      WHERE e3.student_id = :studentId
                        AND e3.course_id  = c.id
                        AND e3.status     = 'SELECTED'
                   )
            """, nativeQuery = true)
    List<Object[]> findConflictingCourses(@Param("studentId") Long studentId,
                                          @Param("semester") String semester);
}
