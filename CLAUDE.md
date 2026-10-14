# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

高校选课管理系统（课程设计原型）。Vue 3 + Spring Boot 3 + MySQL 8，
核心是演示**有限课容量被并发争抢**场景下的数据建模、聚合查询与事务级并发控制。

## 常用命令

```bash
# ---- 后端（端口 8080）----
# 注意：PATH 上的 java.exe 指向失效的 javapath，必须显式设置 JAVA_HOME
cd backend
JAVA_HOME=D:\JDK17 DB_PASSWORD=<密码> mvn -B spring-boot:run
mvn -B compile

# 并发测试（自建临时课程，跑完自清理，不污染演示数据）
mvn -B test -Dtest=EnrollmentConcurrencyTest
# 跳过需要数据库的测试
mvn -B test -Dtest='!EnrollmentConcurrencyTest'

# ---- 前端（端口 5173，dev 下将 /api 代理到 8080）----
cd frontend
npm run dev
npm run build
# 若报 "operation was rejected by your operating system"，是全局 npm 缓存目录不可写：
npm install --cache ./.npm-cache

# ---- 数据库 ----
mysql -u root -p < sql/01_schema.sql
mysql -u root -p course_selection < sql/02_seed_data.sql
mysql -u root -p course_selection < sql/03_queries.sql   # 8 条演示查询 + EXPLAIN 对比

# ---- 接口回归（需后端已在 8080 启动；两个脚本都会自清理，可反复跑）----
powershell -ExecutionPolicy Bypass -File scripts/verify-api.ps1     # 42 项：认证/权限/CRUD/选课闭环/冲突/统计
powershell -ExecutionPolicy Bypass -File scripts/verify-admin.ps1   # 56 项：账号管理/选课记录管理
powershell -ExecutionPolicy Bypass -File scripts/verify-ai.ps1      # 50 项：AI 问答、角色隔离、推荐问题自洽
```

数据库密码通过环境变量 `DB_PASSWORD` 注入，`application.yml` 里保留的是占位符。
演示账号 `admin` / `stu001` / `tea001`，密码均为 `123456`。

## 架构

### 分层与数据流

`Controller → Service → Repository`，事务边界**只在 Service 层**。

- `common/` — `Result<T>` 统一响应体、`ErrorCode` 分段错误码、`@RestControllerAdvice` 全局异常
- `security/` — JWT 签发解析、拦截器、`UserContext`(ThreadLocal)、`@RequireRole`
- `repository/` — 6 个实体仓储 + `StatsRepository`（只承载统计查询，不继承 `JpaRepository`）
- `support/SqlUtils` — 原生查询 `Object[]` 的类型转换

**实体不使用 JPA 关联**。外键一律映射为普通 `Long` 列，所有多表查询走原生 SQL。
这是有意选择：彻底避开 `open-in-view=false` 下的懒加载异常，也让统计查询语义更直白。

### 认证

`JwtInterceptor` 解析 `Authorization: Bearer <token>` → 写入 `UserContext` →
校验方法/类上的 `@RequireRole`（**方法注解优先于类注解**）→ `afterCompletion` 清理 ThreadLocal。

JWT 里携带业务主键 `refId`（学生为 `student.id`，教师为 `teacher.id`），
所以 Service 拿学生身份不必再查库：`UserContext.requireStudentId()`。
放行路径在 `WebMvcConfig#addInterceptors` 中维护。

### 并发选课（本项目的核心，改动前务必理解）

`EnrollmentService.selectCourse` 把「判断 + 扣减」压进一条原子 UPDATE：

```sql
UPDATE course SET enrolled_count = enrolled_count + 1
 WHERE id = ? AND status = 'OPEN' AND enrolled_count < capacity
```

调用方只看影响行数（1 = 抢到，0 = 已满）。**不要改回「先 SELECT 查余量再 UPDATE」**，
那样会有多个事务读到同一余量值而超卖。

配套约束，改这两个 `@Modifying` 方法时不能丢：
- `clearAutomatically = true` —— 批量更新绕过持久化上下文，不清空的话紧接着的 `findById` 会读到一级缓存里的陈旧实体
- 退课归还容量返回 0 时**抛异常回滚**，避免留下"退课成功但计数没还"的脏状态

**退课是置 `WITHDRAWN` 而非删行**。因为 `uk_student_course(student_id, course_id)`
唯一索引的存在，重新选课**必须复用原记录**，否则撞索引。`selectCourse` 里的 `existing` 分支就是干这个的。

### 时间冲突检测（区间重叠）

判定公式 `A.start <= B.end AND B.start <= A.end`，节次与周次两个维度都要判。
三处使用，逻辑必须保持一致：

| 位置 | 用途 |
|---|---|
| `CourseRepository#findSelectableCourses` | 可选课程列表（已排除已选 + 冲突） |
| `CourseRepository#findConflictingCourses` | 反查冲突课程，供前端提示 |
| `EnrollmentRepository#countTimeConflict` | 选课时校验 |

前两者都**必须排除学生已选中的课程本身** —— 区间重叠判定中，
一门课的课表与它自己必然重叠，不排除的话每门已选课都会「与自己冲突」。
另外 `countTimeConflict` 用了 `cs_old.course_id <> :courseId` 做自我排除。

### 数据库契约

`spring.jpa.hibernate.ddl-auto: validate` —— **表结构由 `sql/01_schema.sql` 决定，
实体只做校验**。这是刻意的：数据库设计是主动产出，而非 JPA 自动生成的产物。
因此任何实体改动都必须同步改 DDL，否则启动即失败。

由此产生几处不能随手改的写法：

- `student.gender` 用 `@Column(columnDefinition = "char(1)")`：DDL 是 `CHAR(1)`，
  Hibernate 对 String 默认映射 `VARCHAR`，不声明就校验失败
- `sys_user.status`、`course_schedule` 的星期/节次/周次用 `INT` 而非 `TINYINT`，
  同样是为了让列类型与实体上的 `Integer` 对齐

`enrolled_count` 是**有意为之的反范式字段**（避免每次查余量都 COUNT 聚合），
一致性由 `chk_capacity` 约束 + Service 事务保证。批量导入选课记录后必须
重新同步该字段，`02_seed_data.sql` 末尾和 `03_queries.sql` 末尾都有同步语句。

### 前端约定

**颜色与间距一律走 `src/styles/theme.css` 的设计令牌**，不要在组件的
`<style scoped>` 里写死十六进制色值。这一层在 `main.js` 中于 Element Plus 样式
**之后**引入，通过覆盖 `--el-color-primary` 等 CSS 变量 + 少量全局类实现全局换肤，
新增页面用标准 `el-card` / `el-table` / `el-form` 结构即自动获得一致观感。
EP 的 light-3/5/7/8/9 变体值是按官方混色公式预计算的，改主色时需一并重算。

**管理员接口的路径约定**：学生自助与管理员操作都挂在 `/api/enrollments` 下，
两者都有删除语义。为避免 Spring 映射冲突（同路径同方法），
管理员操作统一加 `record` 段：`/api/enrollments/record/{id}`，
学生退课保持 `/api/enrollments/{courseId}`。新增管理端接口时沿用这个约定。

**管理员改选课记录必须复用 `selectCourse`**（见 `EnrollmentService#adminEnroll`），
不要另写一套"管理员专用"校验 —— 容量、时间冲突、重复选课三类不变式
必须只有一处实现，否则两个入口的规则会逐渐漂移。

### AI 助手模块

> ⚠️ **`ai.claude.base-url` 必须显式配置，不能只依赖环境变量。**
> Anthropic 的 **Java** SDK 不读取 `ANTHROPIC_BASE_URL`（只有 Python / JS SDK 读）。
> 只设环境变量时请求会打到官方 `api.anthropic.com`，非官方 Key 会被 403 拒绝，
> 然后静默降级回知识库 —— 表现为「对话正常但模型从未被调用」，很难排查。
> `ClaudeProvider#client()` 里已经显式调用 `.baseUrl(...)`，**不要删掉那段**。
>
> 它接的是「Anthropic 协议兼容」接口，所以 DeepSeek / OpenRouter / 自建网关都能用，
> 换服务商只需要改 `base-url` / `model` / `provider-name` 三项配置。
> `provider-name` 决定前端「来源」标签显示什么。

回答来源有三种，**改兜底逻辑时不要把它们合并成一种**：
`本地知识库`（知识库命中）/ `未匹配`（都没答上来）/ 其他（模型名）。
回归测试靠区分这三个来定位问题 —— 合并后无法分辨"角色隔离生效"和"知识库条目写错"。


`AiService` 只做编排：**按 `@Order` 依次询问所有可用的 `AiAnswerProvider`，谁先给出答案就用谁**。
它本身不含任何回答逻辑 —— 新增回答来源（新模型、新数据源）只需实现接口并注册 Bean，
不要往 `AiService` 里加 if-else。

当前两个提供方，顺序是关键：

| 顺序 | 提供方 | 可用条件 | 定位 |
|---|---|---|---|
| 1 | `KnowledgeBaseProvider` | 始终 | 答案与系统实际行为逐条核对过，属于确定信息 |
| 2 | `ClaudeProvider` | 配置了 `ai.claude.api-key` | 知识库没命中时才兜底 |

**为什么知识库排前面**：模型会泛泛而谈甚至编造不存在的菜单。能用确定答案就不用生成式答案。
改动顺序前先想清楚这一点。

**关键词长度加权**（`KnowledgeBaseProvider#score`）：匹配得分 = 命中关键词的长度之和。
这不是随意定的 —— 「怎么管理选课记录」同时命中「选课记录」(4) 和「选课」(2)，
不加权会落到泛泛的「怎么选课」条目上。**新增触发词时要检查是否与既有条目产生歧义**，
`verify-ai.ps1` 的 I 组（推荐问题必须全部命中）就是防这个的。

**兜底话术里不要举具体菜单例子**：不同角色能看到的菜单不同，
举「课程管理」会把学生和教师引到他们根本没有的页面上。兜底只给角色相关的推荐问题。

`ClaudeProvider` 的任何异常都必须捕获并返回 `Optional.empty()` 以降级，
不能把异常抛给用户 —— 外部依赖不可用时，AI 功能整体仍要能用（退化为知识库）。

### 原生查询 → VO 的映射

`@Query(nativeQuery = true)` 返回 `Object[]`，由 VO 的**具名工厂方法**按下标映射：

- `CourseVO.ofListRow`（16 列，课程列表）
- `CourseVO.ofSelectableRow`（11 列，可选课程）

用命名而非重载区分，就是为了在改 SQL 列顺序时能立刻发现对应关系。
改原生查询的 SELECT 列表时，**必须同步改对应工厂方法的下标**。
可取空值一律走 `SqlUtils`（MySQL 驱动对整型/布尔返回的 Java 类型并不统一）。

## 已知坑

- 提交前确认没把演示数据跑脏。跑完测试/手测可重跑 `sql/02_seed_data.sql` 恢复，
  它会先按外键反序 `DELETE` 再灌数据（只影响本系统这 6 张表）。
- 删除有选课记录的学生/课程会被拒（外键 RESTRICT），
  且**已退课的历史记录同样计入** —— 这是有意保留修读记录的。
- `scripts/*.ps1` 含中文。Windows PowerShell 5.1 按 GBK 读无 BOM 的 `.ps1`
  会乱码并报语法错误，编辑后需另存为「UTF-8 带 BOM」。
- **`request.js` 里判断"是否已在登录页"必须用 `pathname` 而非 `hash`**。
  本项目路由是 history 模式，`window.location.hash` 恒为空串，
  用它判断会让条件永远成立、触发整页跳转 —— 曾经因此导致
  在登录页输错密码时提示还没渲染就被刷新掉，表现为"点了登录毫无反应"。
- `spring.jackson.date-format` **只对 `java.util.Date` 生效**，
  对 `LocalDateTime` 无效（会输出 ISO 的 `2026-09-27T22:47:55`）。
  本项目由 `JacksonConfig` 统一成 `yyyy-MM-dd HH:mm:ss`；
  新增时间字段走 `LocalDateTime` 即可，无需再加注解。
- 新增/调整前端表格列宽后，用浏览器量一下 `.el-table__body-wrapper` 的
  `scrollWidth - clientWidth`，确认没有横向溢出把右侧列挤出可视区。
- **AI 抽屉宽度有两处定义**：`AiAssistant.vue` 的 `DRAWER_WIDTH` 与
  `MainLayout.vue` 的 `AI_DRAWER_WIDTH`。抽屉是靠主界面加 `margin-right` 让位的，
  两个值不一致会出现缝隙或重叠。
- `add_student_or_teacher` 等知识库条目的答案涉及具体菜单路径，
  **改动了前端菜单名或页面结构后要同步更新 `KnowledgeBase.java`**，否则 AI 会把用户指错地方。
- 回归脚本里的精确计数断言（如「选课记录总数回到种子状态(218)」）是有意保持严格的 ——
  它抓过 UI 测试残留的 WITHDRAWN 记录。**不要把这类断言放宽成范围比较**。
- `02_seed_data.sql` 里刻意制造了一处时间冲突（数据结构 与 线性代数 同为周三 1-2 节），
  用于演示冲突检测，不是数据错误。其余 (星期, 节次) 组合互不重叠，保证仍可选到课。
