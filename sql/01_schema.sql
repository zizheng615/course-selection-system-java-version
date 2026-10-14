-- =============================================================================
--  高校选课管理系统 - 数据库结构脚本 (DDL)
--  数据库：MySQL 8.0+
--  字符集：utf8mb4 / utf8mb4_0900_ai_ci     引擎：InnoDB
--
--  说明：
--    1. 本脚本只删除并重建本系统自己的 6 张表，不会影响库中其他对象。
--    2. 表结构由本脚本决定，JPA 侧使用 ddl-auto=validate 仅做校验。
--    3. 执行顺序：01_schema.sql -> 02_seed_data.sql
-- =============================================================================

CREATE DATABASE IF NOT EXISTS course_selection
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE course_selection;

-- 按外键依赖的反序删除（子表在前，父表在后）
DROP TABLE IF EXISTS enrollment;
DROP TABLE IF EXISTS course_schedule;
DROP TABLE IF EXISTS course;
DROP TABLE IF EXISTS student;
DROP TABLE IF EXISTS teacher;
DROP TABLE IF EXISTS sys_user;
DROP VIEW  IF EXISTS v_course_detail;
DROP VIEW  IF EXISTS v_student_credit;


-- =============================================================================
-- 1. sys_user  统一用户表
--    认证与业务解耦：学生/教师/管理员共用一张账号表，业务属性放在各自业务表
-- =============================================================================
CREATE TABLE sys_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(50)  NOT NULL COMMENT '登录账号（学号/工号）',
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码',
  role          VARCHAR(20)  NOT NULL COMMENT 'STUDENT / TEACHER / ADMIN',
  real_name     VARCHAR(50)  NOT NULL COMMENT '真实姓名',
  -- 说明：这里用 INT 而非 TINYINT。TINYINT 与 Hibernate 实体上的 Integer 类型
  -- 在 ddl-auto=validate 下会被判定为列类型不匹配而导致启动失败；
  -- 这张表只有几十行，INT 与 TINYINT 的存储差异无实际意义。
  status        INT          NOT NULL DEFAULT 1 COMMENT '1 启用 0 禁用',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  KEY idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一用户表';


-- =============================================================================
-- 2. student  学生表
-- =============================================================================
CREATE TABLE student (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  user_id    BIGINT      NOT NULL,
  student_no VARCHAR(20) NOT NULL COMMENT '学号',
  name       VARCHAR(50) NOT NULL,
  gender     CHAR(1)     NOT NULL DEFAULT 'M' COMMENT 'M 男 / F 女',
  major      VARCHAR(50) NOT NULL COMMENT '专业',
  grade      VARCHAR(10) NOT NULL COMMENT '年级，如 2023',
  phone      VARCHAR(20) COMMENT '联系电话',
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_no (student_no),
  UNIQUE KEY uk_student_user (user_id),
  KEY idx_major_grade (major, grade),
  CONSTRAINT fk_student_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生表';


-- =============================================================================
-- 3. teacher  教师表
-- =============================================================================
CREATE TABLE teacher (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  user_id    BIGINT      NOT NULL,
  teacher_no VARCHAR(20) NOT NULL COMMENT '工号',
  name       VARCHAR(50) NOT NULL,
  title      VARCHAR(20) COMMENT '职称：教授/副教授/讲师',
  dept       VARCHAR(50) NOT NULL COMMENT '所属院系',
  PRIMARY KEY (id),
  UNIQUE KEY uk_teacher_no (teacher_no),
  UNIQUE KEY uk_teacher_user (user_id),
  CONSTRAINT fk_teacher_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师表';


-- =============================================================================
-- 4. course  课程表
--    enrolled_count 是「有意为之的反范式」：避免每次查余量都要 COUNT 聚合，
--    其一致性由 CHECK 约束 + Service 层事务共同保证。
-- =============================================================================
CREATE TABLE course (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  course_code    VARCHAR(20)  NOT NULL COMMENT '课程代码',
  name           VARCHAR(100) NOT NULL COMMENT '课程名称',
  credit         DECIMAL(3,1) NOT NULL COMMENT '学分',
  hours          INT          NOT NULL COMMENT '学时',
  course_type    VARCHAR(20)  NOT NULL COMMENT '必修/选修/公选',
  dept           VARCHAR(50)  NOT NULL COMMENT '开课院系',
  semester       VARCHAR(20)  NOT NULL COMMENT '学期，如 2026-2027-1',
  teacher_id     BIGINT       NOT NULL COMMENT '授课教师',
  capacity       INT          NOT NULL COMMENT '容量上限',
  enrolled_count INT          NOT NULL DEFAULT 0 COMMENT '已选人数（冗余字段）',
  status         VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN 开放选课 / CLOSED 已关闭',
  intro          VARCHAR(500) COMMENT '课程简介',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code_semester (course_code, semester),
  KEY idx_teacher (teacher_id),
  KEY idx_semester_status (semester, status),
  CONSTRAINT fk_course_teacher FOREIGN KEY (teacher_id) REFERENCES teacher (id),
  CONSTRAINT chk_capacity CHECK (enrolled_count >= 0 AND enrolled_count <= capacity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';


-- =============================================================================
-- 5. course_schedule  上课时间表
--    独立成表的原因：一门课可能有多段上课时间（理论课+实验课），
--    且时间冲突检测需要按 (weekday, 节次区间) 做区间重叠比较。
-- =============================================================================
CREATE TABLE course_schedule (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  course_id     BIGINT      NOT NULL,
  -- 取值域很小（星期 1-7、节次 1-12、周次 1-30），但仍用 INT：
  -- 与实体上的 Integer 保持类型一致，避免 ddl-auto=validate 报列类型不匹配。
  weekday       INT         NOT NULL COMMENT '1=周一 ... 7=周日',
  start_section INT         NOT NULL COMMENT '开始节次 1-12',
  end_section   INT         NOT NULL COMMENT '结束节次 1-12',
  start_week    INT         NOT NULL COMMENT '起始周',
  end_week      INT         NOT NULL COMMENT '结束周',
  classroom     VARCHAR(50) COMMENT '上课教室',
  PRIMARY KEY (id),
  KEY idx_cs_course (course_id),
  KEY idx_cs_weekday (weekday, start_section, end_section),
  CONSTRAINT fk_cs_course FOREIGN KEY (course_id) REFERENCES course (id),
  CONSTRAINT chk_section CHECK (start_section > 0 AND end_section >= start_section),
  CONSTRAINT chk_week    CHECK (start_week > 0 AND end_week >= start_week)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程上课时间';


-- =============================================================================
-- 6. enrollment  选课记录表
--    student 与 course 的多对多关系由本表拆解。
--    uk_student_course 唯一索引是「防重复选课」的数据库级兜底。
-- =============================================================================
CREATE TABLE enrollment (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  student_id  BIGINT       NOT NULL,
  course_id   BIGINT       NOT NULL,
  status      VARCHAR(20)  NOT NULL DEFAULT 'SELECTED' COMMENT 'SELECTED 已选 / WITHDRAWN 已退',
  score       DECIMAL(5,1) COMMENT '成绩，NULL 表示未录入',
  selected_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_course (student_id, course_id),
  KEY idx_enroll_course_status (course_id, status),
  KEY idx_enroll_student_status (student_id, status),
  CONSTRAINT fk_enroll_student FOREIGN KEY (student_id) REFERENCES student (id),
  CONSTRAINT fk_enroll_course  FOREIGN KEY (course_id)  REFERENCES course (id),
  CONSTRAINT chk_score CHECK (score IS NULL OR (score >= 0 AND score <= 100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选课记录表';


-- =============================================================================
-- 视图（课程要求加分项：用视图封装高频的多表连接）
-- =============================================================================

-- 课程详情 + 剩余容量（前端课程列表直接查这个视图）
CREATE VIEW v_course_detail AS
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
JOIN teacher t ON t.id = c.teacher_id;

-- 学生学分统计
CREATE VIEW v_student_credit AS
SELECT s.id         AS student_id,
       s.student_no,
       s.name,
       s.major,
       c.semester,
       COUNT(e.id)  AS course_count,
       IFNULL(SUM(c.credit), 0) AS total_credit
FROM student s
LEFT JOIN enrollment e ON e.student_id = s.id AND e.status = 'SELECTED'
LEFT JOIN course c     ON c.id = e.course_id
GROUP BY s.id, s.student_no, s.name, s.major, c.semester;


-- =============================================================================
-- 结构校验：确认表已创建
-- =============================================================================
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'course_selection' AND TABLE_TYPE = 'BASE TABLE'
ORDER BY TABLE_NAME;
