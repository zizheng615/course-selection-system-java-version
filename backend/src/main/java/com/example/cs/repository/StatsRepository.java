package com.example.cs.repository;

import com.example.cs.entity.Course;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 统计报表专用仓储。
 *
 * <p>这些查询跨越 course / student / enrollment / teacher 多张表并大量使用聚合函数，
 * 用 JPQL 表达反而绕，因此统一用原生 SQL 集中在这里，便于对照 SQL 与执行计划。
 * 该接口不继承 JpaRepository —— 它只承载查询，不提供实体的增删改。
 */
public interface StatsRepository extends Repository<Course, Long> {

    /**
     * 热门课程 Top-N：按选课人数降序，并计算满座率。
     */
    @Query(value = """
            SELECT c.course_code,
                   c.name       AS course_name,
                   t.name       AS teacher_name,
                   c.capacity,
                   COUNT(e.id)  AS selected_count,
                   ROUND(COUNT(e.id) / c.capacity * 100, 1) AS fill_rate
              FROM course c
              JOIN teacher t         ON t.id = c.teacher_id
              LEFT JOIN enrollment e ON e.course_id = c.id AND e.status = 'SELECTED'
             WHERE c.semester = :semester
             GROUP BY c.id, c.course_code, c.name, t.name, c.capacity
             ORDER BY selected_count DESC, c.id
             LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findPopularCourses(@Param("semester") String semester, @Param("limit") int limit);

    /**
     * 各专业选课分布。
     * LEFT JOIN 保证「没有选课记录的专业」也会出现在结果中，避免统计口径漏项。
     */
    @Query(value = """
            SELECT s.major,
                   COUNT(DISTINCT s.id) AS student_count,
                   COUNT(e.id)          AS enrollment_count,
                   ROUND(COUNT(e.id) / COUNT(DISTINCT s.id), 2) AS avg_courses_per_student
              FROM student s
              LEFT JOIN enrollment e ON e.student_id = s.id AND e.status = 'SELECTED'
             GROUP BY s.major
             ORDER BY enrollment_count DESC
            """, nativeQuery = true)
    List<Object[]> findMajorDistribution();

    /**
     * 教师工作量统计。
     */
    @Query(value = """
            SELECT t.name,
                   t.title,
                   t.dept,
                   COUNT(DISTINCT c.id)             AS course_count,
                   IFNULL(SUM(c.enrolled_count), 0) AS total_students,
                   IFNULL(SUM(c.credit), 0)         AS total_credit
              FROM teacher t
              LEFT JOIN course c ON c.teacher_id = t.id AND c.semester = :semester
             GROUP BY t.id, t.name, t.title, t.dept
             ORDER BY total_students DESC
            """, nativeQuery = true)
    List<Object[]> findTeacherWorkload(@Param("semester") String semester);
}
