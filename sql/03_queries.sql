-- =============================================================================
--  高校选课管理系统 - 演示查询脚本 (Meaningful Queries)
--  用途：答辩时逐条演示，体现「多表连接 / 聚合统计 / 区间重叠 / 执行计划优化」
--  依赖：先执行 01_schema.sql 与 02_seed_data.sql
-- =============================================================================

USE course_selection;

-- 演示参数
SET @student_id  = 1;                    -- 演示学生：张伟（2023001）
SET @course_id   = 3;                    -- 演示课程：计算机网络
SET @semester    = '2026-2027-1';


-- =============================================================================
-- 查询 1：课程列表 + 剩余容量（多表连接）
-- 说明：课程表 JOIN 教师表，并计算剩余容量 remaining，供选课页展示。
-- =============================================================================
SELECT c.id,
       c.course_code,
       c.name,
       c.credit,
       c.course_type,
       t.name AS teacher_name,
       c.capacity,
       c.enrolled_count,
       (c.capacity - c.enrolled_count) AS remaining
FROM course c
JOIN teacher t ON t.id = c.teacher_id
WHERE c.semester = @semester
  AND c.status = 'OPEN'
ORDER BY remaining DESC, c.id
LIMIT 10;


-- =============================================================================
-- 查询 2：学生个人课表（多表连接 + 排序）
-- 说明：从选课记录出发，连接课程、教师、上课时间三张表，按星期与节次排序。
-- =============================================================================
SELECT c.course_code,
       c.name         AS course_name,
       t.name         AS teacher_name,
       cs.weekday,
       CASE cs.weekday
           WHEN 1 THEN '周一' WHEN 2 THEN '周二' WHEN 3 THEN '周三'
           WHEN 4 THEN '周四' WHEN 5 THEN '周五' WHEN 6 THEN '周六'
           ELSE '周日'
       END            AS weekday_name,
       cs.start_section,
       cs.end_section,
       CONCAT(cs.start_week, '-', cs.end_week, ' 周') AS weeks,
       cs.classroom
FROM enrollment e
JOIN course c           ON c.id  = e.course_id
JOIN teacher t          ON t.id  = c.teacher_id
JOIN course_schedule cs ON cs.course_id = c.id
WHERE e.student_id = @student_id
  AND e.status     = 'SELECTED'
  AND c.semester   = @semester
ORDER BY cs.weekday, cs.start_section;


-- =============================================================================
-- 查询 3：时间冲突检测 ★（区间重叠判定）
-- 说明：找出该学生「未选过、且不与已选课程时间冲突」的课程。
--      区间重叠公式：A.start <= B.end AND B.start <= A.end
--      全部在 SQL 内完成，无需把课表捞到应用内存里循环比较。
-- =============================================================================
SELECT c.id,
       c.course_code,
       c.name,
       c.credit,
       t.name AS teacher_name,
       (c.capacity - c.enrolled_count) AS remaining
FROM course c
JOIN teacher t ON t.id = c.teacher_id
WHERE c.semester = @semester
  AND c.status   = 'OPEN'
  AND c.enrolled_count < c.capacity
  -- 排除已选过的课程
  AND NOT EXISTS (
      SELECT 1 FROM enrollment e
      WHERE e.student_id = @student_id
        AND e.course_id  = c.id
        AND e.status     = 'SELECTED'
  )
  -- 排除与已选课程时间冲突的课程
  AND NOT EXISTS (
      SELECT 1
      FROM course_schedule cs_new
      JOIN course_schedule cs_old ON cs_old.weekday = cs_new.weekday
      JOIN enrollment e2
           ON e2.course_id  = cs_old.course_id
          AND e2.student_id = @student_id
          AND e2.status     = 'SELECTED'
      WHERE cs_new.course_id = c.id
        -- 节次区间重叠
        AND cs_new.start_section <= cs_old.end_section
        AND cs_old.start_section <= cs_new.end_section
        -- 周次区间重叠
        AND cs_new.start_week <= cs_old.end_week
        AND cs_old.start_week <= cs_new.end_week
  )
ORDER BY remaining DESC, c.id;


-- =============================================================================
-- 查询 3b：反查——列出与该学生已选课程「确实冲突」的课程（答辩演示用）
-- =============================================================================
SELECT DISTINCT
       c.id,
       c.name AS conflict_course,
       old_c.name AS conflicts_with
FROM course c
JOIN course_schedule cs_new ON cs_new.course_id = c.id
JOIN course_schedule cs_old ON cs_old.weekday    = cs_new.weekday
JOIN enrollment e2
     ON e2.course_id  = cs_old.course_id
    AND e2.student_id = @student_id
    AND e2.status     = 'SELECTED'
JOIN course old_c ON old_c.id = cs_old.course_id
WHERE c.semester = @semester
  AND cs_new.start_section <= cs_old.end_section
  AND cs_old.start_section <= cs_new.end_section
  AND cs_new.start_week    <= cs_old.end_week
  AND cs_old.start_week    <= cs_new.end_week;


-- =============================================================================
-- 查询 4：热门课程 Top 10（聚合 + 排行 + 占比）
-- 说明：按选课人数降序，并计算「满座率」= 已选 / 容量。
-- =============================================================================
SELECT c.course_code,
       c.name AS course_name,
       t.name AS teacher_name,
       c.capacity,
       COUNT(e.id) AS selected_count,
       ROUND(COUNT(e.id) / c.capacity * 100, 1) AS fill_rate
FROM course c
JOIN teacher t            ON t.id = c.teacher_id
LEFT JOIN enrollment e    ON e.course_id = c.id AND e.status = 'SELECTED'
WHERE c.semester = @semester
GROUP BY c.id, c.course_code, c.name, t.name, c.capacity
ORDER BY selected_count DESC, c.id
LIMIT 10;


-- =============================================================================
-- 查询 5：各专业选课分布（多维聚合）
-- 说明：LEFT JOIN 保证没有选课记录的专业也会出现在结果中。
-- =============================================================================
SELECT s.major,
       COUNT(DISTINCT s.id) AS student_count,
       COUNT(e.id)          AS enrollment_count,
       ROUND(COUNT(e.id) / COUNT(DISTINCT s.id), 2) AS avg_courses_per_student
FROM student s
LEFT JOIN enrollment e ON e.student_id = s.id AND e.status = 'SELECTED'
GROUP BY s.major
ORDER BY enrollment_count DESC;


-- =============================================================================
-- 查询 6：学生学分统计（视图 + 聚合）
-- =============================================================================
SELECT student_no, name, major, course_count, total_credit
FROM v_student_credit
WHERE semester = @semester
ORDER BY total_credit DESC
LIMIT 10;


-- =============================================================================
-- 查询 7：某门课程的学生名单与成绩（教师端 / 管理员端）
-- =============================================================================
SELECT s.student_no,
       s.name,
       s.major,
       e.status,
       IFNULL(e.score, '未录入') AS score,
       e.selected_at
FROM enrollment e
JOIN student s ON s.id = e.student_id
WHERE e.course_id = @course_id
  AND e.status    = 'SELECTED'
ORDER BY s.student_no;


-- =============================================================================
-- 查询 8：教师工作量统计（按选课人数汇总）
-- =============================================================================
SELECT t.name AS teacher_name,
       t.title,
       t.dept,
       COUNT(DISTINCT c.id) AS course_count,
       IFNULL(SUM(c.enrolled_count), 0) AS total_students,
       IFNULL(SUM(c.credit), 0) AS total_credit
FROM teacher t
LEFT JOIN course c ON c.teacher_id = t.id AND c.semester = @semester
GROUP BY t.id, t.name, t.title, t.dept
ORDER BY total_students DESC;


-- =============================================================================
-- 执行计划对比（数据库工程实践加分项）
-- 演示：idx_semester_status 索引对课程列表查询的优化效果
-- =============================================================================

-- 8.1 使用索引：type=ref，只扫描符合学期+状态的行
EXPLAIN
SELECT c.id, c.name, c.capacity, c.enrolled_count
FROM course c
WHERE c.semester = '2026-2027-1' AND c.status = 'OPEN';

-- 8.2 强制忽略索引用全表扫描对比：type=ALL，rows 为全表行数
EXPLAIN
SELECT c.id, c.name, c.capacity, c.enrolled_count
FROM course c IGNORE INDEX (idx_semester_status)
WHERE c.semester = '2026-2027-1' AND c.status = 'OPEN';

-- 8.3 冲突检测查询的执行计划：确认命中 idx_cs_weekday 索引
EXPLAIN
SELECT cs_new.course_id
FROM course_schedule cs_new
JOIN course_schedule cs_old ON cs_old.weekday = cs_new.weekday
JOIN enrollment e2
     ON e2.course_id  = cs_old.course_id
    AND e2.student_id = @student_id
    AND e2.status     = 'SELECTED'
WHERE cs_new.course_id = @course_id
  AND cs_new.start_section <= cs_old.end_section
  AND cs_old.start_section <= cs_new.end_section;


-- =============================================================================
-- 并发安全性自检：确认所有 enrolled_count 与真实选课数一致
-- =============================================================================
SELECT '容量一致性检查（应返回空结果集）' AS check_name;
SELECT c.id, c.name, c.capacity, c.enrolled_count,
       (SELECT COUNT(*) FROM enrollment e
        WHERE e.course_id = c.id AND e.status = 'SELECTED') AS actual_count
FROM course c
WHERE c.enrolled_count <> (SELECT COUNT(*) FROM enrollment e
                           WHERE e.course_id = c.id AND e.status = 'SELECTED')
   OR c.enrolled_count > c.capacity;
