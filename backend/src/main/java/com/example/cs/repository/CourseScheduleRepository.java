package com.example.cs.repository;

import com.example.cs.entity.CourseSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CourseScheduleRepository extends JpaRepository<CourseSchedule, Long> {

    List<CourseSchedule> findByCourseId(Long courseId);

    List<CourseSchedule> findByCourseIdInOrderByWeekdayAscStartSectionAsc(Collection<Long> courseIds);

    void deleteByCourseId(Long courseId);

    /**
     * 排课时的教师时间冲突检查：同一教师在同一学期、同一星期，
     * 是否已存在节次区间重叠的排班。
     *
     * <p>区间重叠公式：{@code A.start <= B.end AND B.start <= A.end}。
     * 新增时 excludeId 传 0，修改时传自身 id 以排除自己。
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                  FROM course_schedule cs
                  JOIN course c ON c.id = cs.course_id
                 WHERE c.teacher_id = :teacherId
                   AND c.semester   = :semester
                   AND cs.weekday   = :weekday
                   AND cs.id       <> :excludeId
                   AND cs.start_section <= :endSection
                   AND :startSection    <= cs.end_section
            )
            """, nativeQuery = true)
    int countTeacherConflict(@Param("teacherId") Long teacherId,
                             @Param("semester") String semester,
                             @Param("weekday") Integer weekday,
                             @Param("startSection") Integer startSection,
                             @Param("endSection") Integer endSection,
                             @Param("excludeId") Long excludeId);
}
