# 高校选课管理系统

> 课程设计原型 · 全栈实现
> Vue 3 + Spring Boot 3 + MySQL 8

以关系型数据库为核心，围绕**「有限课容量被并发争抢」**这一真实业务问题，
演示规范化数据建模、多表聚合查询、时间冲突的区间重叠判定与事务级并发控制。

---

## 一、快速启动

### 1. 环境要求

| 组件 | 版本 | 本项目验证环境 |
|---|---|---|
| JDK | 17+ | 17.0.7 |
| Maven | 3.8+ | 3.8.8 |
| Node.js | 18+ | 22.14.0 |
| MySQL | 8.0+ | 8.0.33 |

### 2. 初始化数据库

```bash
# 建库建表（6 张表 + 外键 + 索引 + CHECK 约束 + 2 个视图）
mysql -u root -p < sql/01_schema.sql

# 灌入演示数据（1 管理员 + 10 教师 + 50 学生 + 24 门课程 + 选课记录）
mysql -u root -p < sql/02_seed_data.sql
```

> `01_schema.sql` 只会删除并重建本系统自己的 6 张表，不影响库中其他对象。

### 3. 配置数据库密码

编辑 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    username: root
    password: your_password      # ← 改成你的 MySQL 密码
```

也可以不改文件，用环境变量传入：

```bash
# Windows PowerShell
$env:DB_PASSWORD='你的密码'

# Linux / macOS
export DB_PASSWORD='你的密码'
```

### 4. 启动后端

```bash
cd backend
mvn spring-boot:run
```

- 接口地址：<http://localhost:8080>
- **Swagger UI**：<http://localhost:8080/swagger-ui.html>

启动成功后会看到 Hibernate 执行 **schema validation** —— 表结构由 `01_schema.sql`
决定，实体只做校验，这样"数据库设计"是主动产出而非框架自动生成的结果。

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 <http://localhost:5173>（Vite 已配置 `/api` 代理到 8080，无需处理跨域）。

### 6. 演示账号

| 角色 | 账号 | 密码 |
|---|---|---|
| 管理员 | `admin` | `123456` |
| 学生 | `stu001` | `123456` |
| 教师 | `tea001` | `123456` |

---

## 二、功能清单

### 管理员

| 模块 | 功能 |
|---|---|
| 课程管理 | 增删改查、开放/关闭选课、按关键词/学期/状态筛选、余量进度条 |
| 排课管理 | 为一门课配置多段上课时间（星期 + 节次 + 周次 + 教室），**校验教师排课冲突** |
| 学生管理 | 增删改查，新增时自动创建登录账号（账号=学号，密码 123456） |
| 教师管理 | 增删改查，名下有课程时拒绝删除 |
| **账号管理** | 查看全部账号及其关联的学生/教师记录；**修改登录账号、重置密码**；启用/禁用；删除（已绑定业务信息的会拒绝） |
| **选课记录管理** | 全量选课记录的分页与筛选；**代学生加选课**（走与学生端完全相同的容量/冲突/重复校验）；软退课；硬删记录并同步归还容量；录入成绩 |
| 统计报表 | 热门课程 Top10（含满座率柱状图）、各专业选课分布、教师工作量 |

### 学生

| 模块 | 功能 |
|---|---|
| 选课中心 | 可选课程列表（**已自动过滤已选与时间冲突的课**）、选课、退课、查看冲突提示 |
| 我的课表 | 周课表网格 + 已选课程明细 + 总学分 + 成绩 |

### 教师

| 模块 | 功能 |
|---|---|
| 我的课程 | 本人授课列表、查看选课学生名单、录入成绩 |

### AI 助手（三种角色通用）

右下角悬浮按钮 → 右侧展开对话面板，解答**本系统的使用方法**。
面板挤压主界面而非遮挡，两者可同时完整操作。

- 回答**按登录角色区分**：学生问「怎么选课」得到学生端路径，管理员问「怎么新增课程」得到管理端路径
- 学生问管理端问题会明确说明「没把握」，不会把管理员的菜单路径推给学生
- 每条回答标注**来源**（本地知识库 / Claude），并给出可点击的追问建议
- 默认完全离线运行（内置知识库）；配置好 `api-key` + `base-url` 后，知识库未命中的问题自动交给大模型
- 已实测接入 DeepSeek（`deepseek-flash`），回答标注来源，并遵守系统提示词的边界（只答本系统用法）

---

## 三、数据库设计要点

### 3.1 表结构

```
sys_user ──1:1── student ──┐
    │                      ├──< enrollment >──┐
    └─────1:1── teacher ───┘                  │
                    │                         │
                    └──< course >─────────────┘
                            │
                            └──< course_schedule
```

| 表 | 说明 | 关键约束 |
|---|---|---|
| `sys_user` | 统一账号表，认证与业务解耦 | `uk_username` |
| `student` / `teacher` | 各角色业务属性 | `uk_student_no` / `uk_teacher_no`，外键指向 `sys_user` |
| `course` | 课程，含冗余计数字段 | `uk_code_semester`、**`chk_capacity`** |
| `course_schedule` | 上课时间，支持一门课多段 | `chk_section` / `chk_week` |
| `enrollment` | 选课记录，拆解多对多 | **`uk_student_course`**、`chk_score` |

### 3.2 三个值得在答辩讲的设计决策

**① `enrolled_count` 是「有意为之的反范式」**

余量 = 容量 − 已选人数。若每次都 `COUNT(*) FROM enrollment` 聚合，选课页每次
列表查询都要扫选课记录表。把计数冗余在课程表上，余量查询退化为一次简单读取；
一致性由 `chk_capacity` 约束 + Service 层事务共同保证。

**② 退课是改状态，不是删行**

`enrollment` 上有唯一索引 `uk_student_course(student_id, course_id)`。
若退课直接删行，重新选课时插新行没问题；但如果保留历史记录（用 `WITHDRAWN` 状态），
重新选课**必须复用原记录**，否则会撞唯一索引。代码里对这一点做了显式处理。

**③ 时间冲突在 SQL 里解决，而不是在 Java 里**

区间重叠公式：

```sql
A.start <= B.end AND B.start <= A.end
```

节次与周次两个维度都要判。整个判定通过 `NOT EXISTS` 子查询完成，
不需要把学生课表读进内存再循环比较：

```sql
AND NOT EXISTS (
    SELECT 1
      FROM course_schedule cs_new
      JOIN course_schedule cs_old ON cs_old.weekday = cs_new.weekday
      JOIN enrollment e2 ON e2.course_id = cs_old.course_id
                        AND e2.student_id = :studentId AND e2.status = 'SELECTED'
     WHERE cs_new.course_id = c.id
       AND cs_new.start_section <= cs_old.end_section
       AND cs_old.start_section <= cs_new.end_section
       AND cs_new.start_week    <= cs_old.end_week
       AND cs_old.start_week    <= cs_new.end_week
)
```

### 3.3 索引设计

| 索引 | 支撑的查询 |
|---|---|
| `uk_student_course (student_id, course_id)` | 防重复选课 + "我的课程"查询 |
| `idx_enroll_course_status (course_id, status)` | 统计课程已选人数、导出名单 |
| `idx_cs_weekday (weekday, start_section, end_section)` | 时间冲突的区间比对 |
| `idx_semester_status (semester, status)` | 选课页按学期+状态筛选 |

演示执行计划对比：

```bash
mysql -u root -p < sql/03_queries.sql   # 末尾含 EXPLAIN 对比
```

---

## 四、并发控制（核心）

选课本质是**有限资源被并发争抢**。若写成「先查余量、再更新」：

```
事务 A: SELECT enrolled_count → 49   (capacity = 50，判断"还有名额")
事务 B: SELECT enrolled_count → 49   (同样判断"还有名额")
事务 A: UPDATE enrolled_count = 50
事务 B: UPDATE enrolled_count = 51   ← 超卖！
```

本实现把「判断 + 扣减」压进一条 SQL，靠 InnoDB 行锁保证原子性：

```sql
UPDATE course
   SET enrolled_count = enrolled_count + 1
 WHERE id = :id
   AND status = 'OPEN'
   AND enrolled_count < capacity
```

调用方只看影响行数：`1` = 抢到名额，`0` = 已被抢完。

### 并发测试

`backend/src/test/java/com/example/cs/EnrollmentConcurrencyTest.java`
临时创建一门容量为 10 的课程，起 **100 个线程同时抢**，断言：

1. 成功次数**恰好等于 10**（多于即超卖，少于即名额浪费）
2. `enrolled_count <= capacity`
3. `enrolled_count` 与真实选课记录数**完全一致**（反范式字段未失真）

```bash
cd backend
mvn test -Dtest=EnrollmentConcurrencyTest
```

实测输出（已通过）：

```
================ 并发选课结果 ================
  并发线程数      : 100
  课程容量        : 10
  选课成功        : 10
  被拒绝          : 90
  enrolled_count  : 10
  实际选课记录数  : 10
=============================================
```

> 测试自带清理：用自己临时创建的课程，结束后连选课记录一并删除，不污染演示数据。
> 若只想跑不依赖数据库的测试：`mvn test -Dtest='!EnrollmentConcurrencyTest'`

### 接口回归脚本

`scripts/verify-api.ps1` 覆盖 **38 项** 端到端断言：JWT 签发与拦截、角色越权、
CRUD、选课/退课闭环、时间冲突、排课冲突、引用完整性、统计查询。

```powershell
# 需后端已在 8080 运行
powershell -ExecutionPolicy Bypass -File scripts\verify-api.ps1
```

> 注意：脚本含中文，Windows PowerShell 5.1 读取无 BOM 的 UTF-8 脚本会乱码。
> 若自行编辑后报语法错误，先重新保存为「UTF-8 带 BOM」。

---

## 五、项目结构

```
course-selection-system/
├── backend/                             Spring Boot 后端
│   └── src/main/java/com/example/cs/
│       ├── common/                      Result / PageResult / 全局异常 / 错误码
│       ├── config/                      WebMvc（CORS+拦截器）/ OpenAPI
│       ├── security/                    JWT 签发解析 / 拦截器 / UserContext / @RequireRole
│       ├── entity/                      6 个 JPA 实体
│       ├── repository/                  6 个仓储 + StatsRepository（统计专用）
│       ├── service/                     业务层（事务边界在这里）
│       ├── controller/                  7 个 REST 控制器
│       ├── dto/  vo/                    入参 / 出参对象
│       └── support/SqlUtils.java        原生查询结果类型转换
├── frontend/                            Vue 3 前端
│   └── src/
│       ├── styles/theme.css             设计令牌 + Element Plus 变量覆盖（全局换肤层）
│       ├── api/                         Axios 实例 + 按模块拆分的接口封装
│       ├── components/AiAssistant.vue   AI 悬浮按钮 + 右侧对话抽屉
│       ├── router/                      路由 + 登录守卫 + 角色守卫
│       ├── stores/user.js               Pinia：token / 用户信息
│       ├── layouts/MainLayout.vue       按角色渲染分组菜单 + AI 抽屉的布局联动
│       └── views/                       admin(7) / student(2) / teacher(1)
├── sql/
│   ├── 01_schema.sql                    建库建表 + 视图
│   ├── 02_seed_data.sql                 演示数据
│   └── 03_queries.sql                   8 条演示查询 + EXPLAIN 对比
├── scripts/
│   ├── verify-api.ps1                   42 项核心接口回归（自清理）
│   ├── verify-admin.ps1                 56 项管理功能回归（自清理）
│   └── verify-ai.ps1                    50 项 AI 问答回归（自清理）
└── README.md
```

---

### 5.1 前端主题层

`src/styles/theme.css` 是**全局换肤层**，在 `main.js` 中于 Element Plus 样式之后引入。
它做两件事：

1. **定义设计令牌**（`--brand-500`、`--c-text-2`、`--radius-card` 等）与
   **覆盖 Element Plus 的 CSS 变量**（`--el-color-primary` 及其 light/dark 变体，
   变体值按 EP 自身的混色公式预先算好，避免手调产生偏差）
2. **统一组件观感**：卡片去掉硬边框改投影分层、表头改浅灰、弹窗圆角、
   行内表单间距、分页右对齐等

这样新增页面只要用标准的 `el-card` / `el-table` / `el-form` 结构就自动获得一致观感，
不需要在各自的 `<style scoped>` 里重复定义颜色与间距。页面级样式只保留布局类规则。

通用工具类（`.toolbar`、`.cell-main`、`.cell-sub`、`.text-muted` 等）也定义在这一层。

### 5.2 AI 助手的架构

核心是一句话：**能用确定的答案，就不用生成的答案。**

```
用户提问
   │
   ▼
AiService ──按 @Order 依次询问──┐
   │                            │
   │ ① KnowledgeBaseProvider    ② ClaudeProvider
   │    内置知识库，始终可用       配置了 api-key 才可用
   │    关键词匹配 + 角色过滤      知识库没命中时兜底
   │                            │
   └──────── 都没命中 ──────────┘
                │
                ▼
        返回兜底话术 + 角色相关的推荐问题
```

**为什么知识库排在前面**：知识库里的答案是与系统实际行为逐条核对过的，属于确定信息；
模型则可能泛泛而谈甚至编造不存在的菜单。所以只有当问题在知识库里找不到依据时，才交给模型。

**扩展点**：新增一种回答来源只需实现 `AiAnswerProvider` 接口并注册为 Bean，
用 `@Order` 决定优先级 —— 不需要改动 `AiService` 或其他任何提供方。

匹配采用**关键词长度加权**：问题里包含的触发词越长，权重越高。
这条规则解决了实际的歧义 —— 「怎么管理选课记录」里既有「选课记录」（4 分）也有「选课」（2 分），
加权后正确落到「选课记录」条目，而不是泛泛的「怎么选课」。

**回答来源有三种，前端会标出来**，让用户能判断答案的可信度：

| 标签 | 含义 |
|---|---|
| 本地知识库 | 知识库命中，答案与系统实际行为核对过 |
| DeepSeek（或你配置的服务名） | 知识库没命中，由大模型回答 |
| 未匹配 | 都没答上来，返回兜底话术 + 推荐问题 |

**接入大模型（可选）**：这里用的是 **Anthropic 协议兼容**接口，所以官方 Claude、
DeepSeek、OpenRouter、自建网关都能接，改三个配置项即可：

```yaml
ai:
  claude:
    api-key: sk-...                                  # 或用环境变量 ANTHROPIC_API_KEY
    base-url: https://api.deepseek.com/anthropic     # 官方 Claude 填 https://api.anthropic.com
    model: deepseek-flash
    provider-name: DeepSeek                          # 前端来源标签显示的名字
```

> ⚠️ **`base-url` 必须写在配置里，不能只靠环境变量。**
> Anthropic 的 **Java** SDK 不读取 `ANTHROPIC_BASE_URL`（只有 Python / JS SDK 读）。
> 只设环境变量的话，请求会打到官方 `api.anthropic.com`，第三方 Key 会被 403 拒绝，
> 然后静默降级回知识库 —— 表面看"能对话"，实际模型一次都没用上。

`enabled: false` 可强制只走知识库；`api-key` 留空则完全不发起外部请求。

## 六、API 一览

| 方法 | 路径 | 说明 | 角色 |
|---|---|---|---|
| POST | `/api/auth/login` | 登录，返回 JWT | 公开 |
| GET | `/api/auth/me` | 当前用户 | 登录 |
| GET | `/api/courses` | 课程分页列表（含余量） | 登录 |
| GET | `/api/courses/semesters` | 学期选项 | 登录 |
| GET | `/api/courses/my` | 我的授课 | 教师 |
| POST/PUT/DELETE | `/api/courses[/{id}]` | 课程 CRUD | 管理员 |
| PATCH | `/api/courses/{id}/status` | 开放/关闭选课 | 管理员 |
| GET/POST/PUT/DELETE | `/api/students[/{id}]` | 学生 CRUD | 管理员 |
| GET/POST/PUT/DELETE | `/api/teachers[/{id}]` | 教师 CRUD | 管理员 |
| GET/POST/PUT/DELETE | `/api/schedules[/{id}]` | 排课 CRUD | 管理员（查为登录） |
| GET | `/api/users` | 账号分页列表（含关联业务信息） | 管理员 |
| PUT | `/api/users/{id}/credentials` | 改登录账号 / 重置密码（password 留空则不改） | 管理员 |
| PATCH | `/api/users/{id}/status` | 启用 / 禁用账号 | 管理员 |
| DELETE | `/api/users/{id}` | 删除账号（已绑定学生/教师则拒绝） | 管理员 |
| GET | `/api/enrollments` | 选课记录分页查询 | 管理员 |
| POST | `/api/enrollments/admin` | 代学生加选课 | 管理员 |
| PATCH | `/api/enrollments/record/{id}/withdraw` | 软退课（保留历史，归还容量） | 管理员 |
| DELETE | `/api/enrollments/record/{id}` | 硬删记录（同步归还容量） | 管理员 |
| **POST** | **`/api/enrollments`** | **选课** | 学生 |
| **DELETE** | **`/api/enrollments/{courseId}`** | **退课** | 学生 |
| GET | `/api/enrollments/my` | 我的课程（含成绩） | 学生 |
| GET | `/api/enrollments/my/timetable` | 我的课表 | 学生 |
| GET | `/api/enrollments/available` | 可选课程（已过滤冲突） | 学生 |
| GET | `/api/enrollments/conflicts` | 冲突课程列表 | 学生 |
| GET | `/api/courses/{id}/students` | 课程学生名单 | 教师/管理员 |
| PATCH | `/api/enrollments/score` | 录入成绩 | 教师/管理员 |
| GET | `/api/stats/popular-courses` | 热门课程 Top10 | 管理员 |
| GET | `/api/stats/major-distribution` | 专业分布 | 管理员 |
| GET | `/api/stats/teacher-workload` | 教师工作量 | 管理员 |
| POST | `/api/ai/chat` | AI 问答（角色从 JWT 解析） | 登录 |
| GET | `/api/ai/suggestions` | 当前角色的推荐问题 | 登录 |

统一响应格式：

```json
{ "code": 0, "message": "ok", "data": { ... } }
{ "code": 4004, "message": "课程容量已满或已关闭，选课失败", "data": null }
```

错误码分段：`1xxx` 认证权限 / `2xxx` 参数校验 / `3xxx` 资源不存在 /
`4xxx` 业务规则拒绝 / `5xxx` 系统异常。

---

## 七、5 分钟演示脚本

1. **管理员** `admin` 登录 → 课程管理（增删改查、余量进度条、关闭选课）
2. 排课管理 → 选一门课 → 新增上课时间 → 故意选一个与已有排课重叠的时段，
   看**教师排课冲突**被拒绝
3. 学生管理 → 新增一个学生（自动建账号）→ 账号管理里看到这个新账号
4. **账号管理** → 把刚建的学号改成别的登录名、重置密码 → 用新账号密码登录验证；
   顺带演示「不能禁用/删除当前登录账号」
5. **选课记录** → 搜一个学生 → 「加选课程」代选一门课 → 余量实时 +1 →
   再选一门与它冲突的课，看**时间冲突被拒绝** → 删除该记录，余量归还
6. **学生** `stu001` 登录 → 选课中心 → 看到"可选课程"已过滤掉冲突课程；
   顶部黄色告警列出冲突课程 → 选一门课 → 切到"我的课程" → 退课
7. 我的课表 → 查看周课表网格与总学分
8. **教师** `tea001` 登录 → 我的课程 → 学生名单 → 录入成绩
9. **管理员** → 统计报表 → 热门课程柱状图、专业分布、教师工作量
10. **AI 助手** → 点右下角悬浮按钮展开对话面板 →
    先看推荐问题随当前角色变化（管理员 5 条、学生 4 条）→
    点「怎么排课」看回答 → 再输入「怎么新增课程」对比同一问题在三种角色下的不同结果
11. 回到终端跑 `mvn test -Dtest=EnrollmentConcurrencyTest`，
    展示 100 线程抢 10 个名额的成功次数恰好为 10

---

## 八、已知限制与后续扩展

| 限制 | 扩展方向 |
|---|---|
| 单机部署，无缓存 | Redis 缓存余量、消息队列削峰 |
| JWT 无法主动失效 | 引入 Redis 黑名单或改用 Session |
| 选课无时间窗口控制 | 增加选课阶段状态机 + 定时任务 |
| 报表为实时查询 | 增加汇总表 + 定时预计算 |
| 未做分页缓存 | 前端虚拟滚动 / 后端游标分页 |
| 知识库为关键词匹配，不具备语义理解 | 已接入大模型兜底；纯知识库模式适合离线演示 |
| AI 对话不落库，刷新即丢失 | 需要历史记录时增加会话表持久化 |
| 大模型回答未做内容风控 | 系统提示词已限定范围，但生产环境应再加一层输出审核 |

---

## 九、常见问题

**Q: 启动报 `Schema-validation: missing table [xxx]`**
A: 还没执行 `01_schema.sql`，或连到了错误的数据库。

**Q: 启动报 `Schema-validation: wrong column type`**
A: 表结构被改过。重新执行 `01_schema.sql` 覆盖即可。

**Q: 登录返回"账号或密码错误"**
A: 确认 `02_seed_data.sql` 已执行。所有演示账号密码均为 `123456`。

**Q: 前端请求 404 / 跨域**
A: 确认后端已在 8080 启动；前端 dev 模式下由 Vite 代理 `/api`，不要直连 8080。

**Q: 新增学生后无法登录**
A: 默认账号是**学号**（不是姓名），初始密码 `123456`。

**Q: 启动报 `Schema-validation: wrong column type encountered in column [gender]`**
A: 实体与 DDL 的列类型不一致。本项目已通过 `@Column(columnDefinition = "char(1)")`
对齐 `student.gender`，若你改过表结构，需同步调整实体映射。

**Q: `npm install` 报 `The operation was rejected by your operating system`**
A: npm 的全局缓存目录（如 `D:\nodejs\node_cache`）不可写。改用可写目录：

```bash
npm install --cache ./.npm-cache
```

**Q: 为什么实体里没有用 `@ManyToOne` 关联？**
A: 本项目把外键映射为普通 `Long` 列，关联查询统一走原生 SQL。
好处是彻底避开 `open-in-view=false` 下的懒加载异常，
且多表统计查询的语义比 JPQL 更直白（见各类 `@Query`）。
