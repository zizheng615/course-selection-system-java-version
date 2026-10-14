package com.example.cs.repository;

import com.example.cs.entity.Enrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByStudentIdAndCourseId(Long studentId, Long courseId);

    Optional<Enrollment> findByStudentIdAndCourseIdAndStatus(Long studentId, Long courseId, String status);

    boolean existsByStudentIdAndCourseIdAndStatus(Long studentId, Long courseId, String status);

    List<Enrollment> findByStudentIdAndStatus(Long studentId, String status);

    List<Enrollment> findByCourseIdAndStatus(Long courseId, String status);

    long countByCourseIdAndStatus(Long courseId, String status);

    /** 用于删除前的引用检查：外键为 RESTRICT，存在记录时删除会失败，提前给出友好提示 */
    long countByStudentId(Long studentId);

    long countByCourseId(Long courseId);

    /**
     * 时间冲突检测：判断该学生已选课程中是否存在与目标课程时间重叠的课程。
     *
     * <p>区间重叠公式：{@code A.start <= B.end AND B.start <= A.end}。
     * 返回 1 表示存在冲突，0 表示不冲突。
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                  FROM course_schedule cs_new
                  JOIN course_schedule cs_old ON cs_old.weekday = cs_new.weekday
                  JOIN enrollment e2
                       ON e2.course_id  = cs_old.course_id
                      AND e2.student_id = :studentId
                      AND e2.status     = 'SELECTED'
                 WHERE cs_new.course_id = :courseId
                   -- 排除自己与自己的区间重叠（重复选课的判定由调用方单独负责）
                   AND cs_old.course_id <> :courseId
                   AND cs_new.start_section <= cs_old.end_section
                   AND cs_old.start_section <= cs_new.end_section
                   AND cs_new.start_week    <= cs_old.end_week
                   AND cs_old.start_week    <= cs_new.end_week
            )
            """, nativeQuery = true)
    int countTimeConflict(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    /**
     * 学生个人课表：连接选课记录、课程、教师、上课时间四张表。
     */
    @Query(value = """
            SELECT c.id            AS course_id,
                   c.course_code,
                   c.name          AS course_name,
                   c.credit,
                   c.course_type,
                   t.name          AS teacher_name,
                   cs.weekday,
                   cs.start_section,
                   cs.end_section,
                   cs.start_week,
                   cs.end_week,
                   cs.classroom
              FROM enrollment e
              JOIN course c           ON c.id  = e.course_id
              JOIN teacher t          ON t.id  = c.teacher_id
              JOIN course_schedule cs ON cs.course_id = c.id
             WHERE e.student_id = :studentId
               AND e.status     = 'SELECTED'
               AND c.semester   = :semester
             ORDER BY cs.weekday, cs.start_section
            """, nativeQuery = true)
    List<Object[]> findTimetable(@Param("studentId") Long studentId,
                                 @Param("semester") String semester);

    /**
     * 学生已选课程列表（含成绩），用于「我的课程」页面。
     */
    @Query(value = """
            SELECT e.id            AS enrollment_id,
                   c.id            AS course_id,
                   c.course_code,
                   c.name          AS course_name,
                   c.credit,
                   c.course_type,
                   c.semester,
                   t.name          AS teacher_name,
                   e.status,
                   e.score,
                   e.selected_at
              FROM enrollment e
              JOIN course c  ON c.id = e.course_id
              JOIN teacher t ON t.id = c.teacher_id
             WHERE e.student_id = :studentId
               AND e.status     = 'SELECTED'
               AND (:semester = '' OR c.semester = :semester)
             ORDER BY c.id
            """, nativeQuery = true)
    List<Object[]> findMyCourses(@Param("studentId") Long studentId,
                                 @Param("semester") String semester);

    /**
     * 课程学生名单（教师端 / 管理员端），含成绩。
     */
    @Query(value = """
            SELECT e.id AS enrollment_id,
                   s.id AS student_id,
                   s.student_no,
                   s.name AS student_name,
                   s.major,
                   s.grade,
                   IFNULL(e.score, -1) AS score
              FROM enrollment e
              JOIN student s ON s.id = e.student_id
             WHERE e.course_id = :courseId
               AND e.status    = 'SELECTED'
             ORDER BY s.student_no
            """, nativeQuery = true)
    List<Object[]> findCourseStudents(@Param("courseId") Long courseId);

    /**
     * 选课记录管理（管理员）：全量记录分页查询，支持按学生/课程关键词、
     * 指定课程、选课状态筛选。
     *
     * <p>条件参数统一由 Service 归一化：文本用空字符串、courseId 用 0 表示"不限"，
     * 这样 SQL 里只做简单比较，避开 NULL 的三值逻辑。
     * score 不做 IFNULL —— 保持 null 语义，前端据此显示"未录入"。
     */
    @Query(value = """
            SELECT e.id,
                   s.id,
                   s.student_no,
                   s.name AS student_name,
                   s.major,
                   c.id,
                   c.course_code,
                   c.name AS course_name,
                   t.name AS teacher_name,
                   c.semester,
                   e.status,
                   e.score,
                   e.selected_at
              FROM enrollment e
              JOIN student s ON s.id = e.student_id
              JOIN course  c ON c.id = e.course_id
              JOIN teacher t ON t.id = c.teacher_id
             WHERE (:keyword = ''
                    OR s.name         LIKE CONCAT('%', :keyword, '%')
                    OR s.student_no   LIKE CONCAT('%', :keyword, '%')
                    OR c.name         LIKE CONCAT('%', :keyword, '%')
                    OR c.course_code  LIKE CONCAT('%', :keyword, '%'))
               AND (:courseId = 0 OR c.id = :courseId)
               AND (:status = '' OR e.status = :status)
             ORDER BY e.id DESC
            """,
            countQuery = """
            SELECT COUNT(*)
              FROM enrollment e
              JOIN student s ON s.id = e.student_id
              JOIN course  c ON c.id = e.course_id
             WHERE (:keyword = ''
                    OR s.name         LIKE CONCAT('%', :keyword, '%')
                    OR s.student_no   LIKE CONCAT('%', :keyword, '%')
                    OR c.name         LIKE CONCAT('%', :keyword, '%')
                    OR c.course_code  LIKE CONCAT('%', :keyword, '%'))
               AND (:courseId = 0 OR c.id = :courseId)
               AND (:status = '' OR e.status = :status)
            """,
            nativeQuery = true)
    Page<Object[]> searchEnrollments(@Param("keyword") String keyword,
                                     @Param("courseId") Long courseId,
                                     @Param("status") String status,
                                     Pageable pageable);
}
