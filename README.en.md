# University Course Selection Management System

> Course project prototype · Full-stack implementation
> Vue 3 + Spring Boot 3 + MySQL 8

Built around a relational database, this project centers on the real-world problem of **limited course capacity being contested concurrently**. It demonstrates normalized data modeling, multi-table aggregate queries, interval-overlap detection for schedule conflicts, and transaction-level concurrency control.

---

## 1. Quick Start

### 1.1 Prerequisites

| Component | Version | Verified with |
|---|---|---|
| JDK | 17+ | 17.0.7 |
| Maven | 3.8+ | 3.8.8 |
| Node.js | 18+ | 22.14.0 |
| MySQL | 8.0+ | 8.0.33 |

### 1.2 Initialize the Database

```bash
# Create the database and tables (6 tables + foreign keys + indexes + CHECK constraints + 2 views)
mysql -u root -p < sql/01_schema.sql

# Load demo data (1 administrator + 10 teachers + 50 students + 24 courses + enrollment records)
mysql -u root -p < sql/02_seed_data.sql
```

> `01_schema.sql` drops and recreates only this system's own 6 tables; other objects in the database are left untouched.

### 1.3 Configure the Database Password

Edit `backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    username: root
    password: your_password      # ← replace with your MySQL password
```

Alternatively, pass the password through an environment variable without editing the file:

```bash
# Windows PowerShell
$env:DB_PASSWORD='your_password'

# Linux / macOS
export DB_PASSWORD='your_password'
```

### 1.4 Start the Backend

```bash
cd backend
mvn spring-boot:run
```

- API base URL: <http://localhost:8080>
- **Swagger UI**: <http://localhost:8080/swagger-ui.html>

On successful startup you will see Hibernate run **schema validation** — the table structure is defined by `01_schema.sql`, and the entities only validate against it. This makes the database design an intentional deliverable rather than something generated automatically by the framework.

### 1.5 Start the Frontend

```bash
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>. (Vite proxies `/api` to port 8080 in development, so no CORS handling is needed.)

### 1.6 Demo Accounts

| Role | Username | Password |
|---|---|---|
| Administrator | `admin` | `123456` |
| Student | `stu001` | `123456` |
| Teacher | `tea001` | `123456` |

---

## 2. Feature Overview

### Administrator

| Module | Features |
|---|---|
| Course management | Create, read, update, and delete; open/close enrollment; filter by keyword/semester/status; capacity-usage progress bars |
| Schedule management | Configure multiple class-time slots per course (weekday + periods + weeks + classroom); **validates teacher schedule conflicts** |
| Student management | CRUD; automatically creates a login account when adding a student (username = student number, password `123456`) |
| Teacher management | CRUD; deletion is rejected while the teacher still has courses |
| **Account management** | View all accounts and their linked student/teacher records; **change login username or reset password**; enable/disable; delete (rejected if business records are linked) |
| **Enrollment record management** | Paginated, filterable view of all enrollment records; **enroll on behalf of a student** (subject to exactly the same capacity/conflict/duplicate checks as the student flow); soft withdrawal; hard delete with capacity returned; grade entry |
| Reports | Top 10 popular courses (with fill-rate bar chart), enrollment distribution by major, teacher workload |

### Student

| Module | Features |
|---|---|
| Enrollment center | Selectable course list (**already filtered to exclude courses the student has enrolled in and any that conflict in time**), enroll, withdraw, conflict hints |
| My timetable | Weekly timetable grid + enrolled course details + total credits + grades |

### Teacher

| Module | Features |
|---|---|
| My courses | Courses taught by the teacher, enrolled student roster, grade entry |

### AI Assistant (available to all three roles)

A floating button in the bottom-right corner opens a chat panel on the right that answers questions about **how to use this system**.
The panel compresses the main layout instead of overlaying it, so both can be used fully at the same time.

- Answers are **tailored to the signed-in role**: a student asking "how do I enroll" gets the student-side path, while an administrator asking "how do I add a course" gets the admin-side path.
- When a student asks an admin-side question, the assistant explicitly says it is **not sure** rather than pushing administrator menu paths onto a student.
- Every answer is labeled with its **source** (local knowledge base / Claude) and includes clickable follow-up suggestions.
- Runs fully offline by default (built-in knowledge base). Once `api-key` and `base-url` are configured, questions the knowledge base cannot answer are automatically forwarded to the LLM.
- Tested with DeepSeek (`deepseek-flash`): answers are labeled with their source and stay within the boundaries set by the system prompt (only questions about this system are answered).

---

## 3. Database Design Highlights

### 3.1 Schema

```
sys_user ──1:1── student ──┐
    │                      ├──< enrollment >──┐
    └─────1:1── teacher ───┘                  │
                    │                         │
                    └──< course >─────────────┘
                            │
                            └──< course_schedule
```

| Table | Description | Key constraints |
|---|---|---|
| `sys_user` | Unified account table; authentication is decoupled from business data | `uk_username` |
| `student` / `teacher` | Role-specific business attributes | `uk_student_no` / `uk_teacher_no`; foreign keys to `sys_user` |
| `course` | Courses, including a denormalized counter | `uk_code_semester`, **`chk_capacity`** |
| `course_schedule` | Class times; a course may have multiple slots | `chk_section` / `chk_week` |
| `enrollment` | Enrollment records, resolving the many-to-many relationship | **`uk_student_course`**, `chk_score` |

### 3.2 Three Design Decisions Worth Highlighting in Your Defense

**① `enrolled_count` is a deliberate denormalization**

Remaining capacity = capacity − number of enrolled students. If it were recomputed with `COUNT(*) FROM enrollment` on every request, the course list would scan the enrollment table each time it is loaded. Keeping the counter denormalized on the course table turns the remaining-capacity query into a simple read; consistency is guaranteed by the `chk_capacity` constraint together with the service-layer transaction.

**② Withdrawal changes state; it does not delete the row**

`enrollment` has a unique index `uk_student_course(student_id, course_id)`. If withdrawal deleted rows outright, re-enrolling via a fresh insert would be fine; but because we retain history (using the `WITHDRAWN` status), re-enrolling **must reuse the original record** — otherwise it would violate the unique index. The code handles this case explicitly.

**③ Time conflicts are resolved in SQL, not in Java**

Interval-overlap condition:

```sql
A.start <= B.end AND B.start <= A.end
```

Both the period and week dimensions must be checked. The entire check is performed through a `NOT EXISTS` subquery, so the student's timetable never needs to be loaded into memory and compared in a loop:

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

### 3.3 Index Design

| Index | Query it supports |
|---|---|
| `uk_student_course (student_id, course_id)` | Prevent duplicate enrollment + "my courses" queries |
| `idx_enroll_course_status (course_id, status)` | Count enrolled students per course, export rosters |
| `idx_cs_weekday (weekday, start_section, end_section)` | Interval comparison for time conflicts |
| `idx_semester_status (semester, status)` | Enrollment-page filtering by semester + status |

Demo execution-plan comparison:

```bash
mysql -u root -p < sql/03_queries.sql   # includes an EXPLAIN comparison at the end
```

---

## 4. Concurrency Control (Core)

Course enrollment is, at its heart, **concurrent contention over a finite resource**. If implemented as "read remaining capacity, then update":

```
Transaction A: SELECT enrolled_count → 49   (capacity = 50, decides "a seat is available")
Transaction B: SELECT enrolled_count → 49   (likewise decides "a seat is available")
Transaction A: UPDATE enrolled_count = 50
Transaction B: UPDATE enrolled_count = 51   ← oversold!
```

This implementation collapses the check and the decrement into a single SQL statement, relying on InnoDB row locks to guarantee atomicity:

```sql
UPDATE course
   SET enrolled_count = enrolled_count + 1
 WHERE id = :id
   AND status = 'OPEN'
   AND enrolled_count < capacity
```

The caller only inspects the affected-row count: `1` = seat acquired, `0` = sold out.

### Concurrency Test

`backend/src/test/java/com/example/cs/EnrollmentConcurrencyTest.java` creates a temporary course with a capacity of 10, launches **100 threads competing simultaneously**, and asserts that:

1. The number of successes is **exactly 10** (more means overselling, fewer means wasted seats).
2. `enrolled_count <= capacity`.
3. `enrolled_count` **exactly matches** the actual number of enrollment records (the denormalized field has not drifted).

```bash
cd backend
mvn test -Dtest=EnrollmentConcurrencyTest
```

Observed output (passing):

```
================ Concurrency Test Result ================
  Concurrent threads    : 100
  Course capacity       : 10
  Successful enrollments: 10
  Rejected              : 90
  enrolled_count        : 10
  Actual enrollment rows: 10
=========================================================
```

> The test cleans up after itself: it uses a course it created temporarily and deletes both the course and its enrollment records when finished, so demo data is not polluted.
> To run only the tests that do not require a database: `mvn test -Dtest='!EnrollmentConcurrencyTest'`

### API Regression Scripts

`scripts/verify-api.ps1` covers **42 end-to-end assertions**: JWT issuance and interception, role-based access violations, CRUD, the enroll/withdraw cycle, time conflicts, schedule conflicts, referential integrity, and statistical queries.

```powershell
# Requires the backend to be running on port 8080
powershell -ExecutionPolicy Bypass -File scripts\verify-api.ps1
```

> Note: the script contains Chinese characters. Windows PowerShell 5.1 misreads UTF-8 scripts without a BOM and reports syntax errors.
> If you edit it yourself and hit a syntax error, re-save it as "UTF-8 with BOM".

---

## 5. Project Structure

```
course-selection-system/
├── backend/                             Spring Boot backend
│   └── src/main/java/com/example/cs/
│       ├── common/                      Result / PageResult / global exception handling / error codes
│       ├── config/                      WebMvc (CORS + interceptors) / OpenAPI
│       ├── security/                    JWT issuance & parsing / interceptor / UserContext / @RequireRole
│       ├── entity/                      6 JPA entities
│       ├── repository/                  6 repositories + StatsRepository (statistics only)
│       ├── service/                     Business layer (transaction boundaries live here)
│       ├── controller/                  7 REST controllers
│       ├── dto/  vo/                    Request / response objects
│       └── support/SqlUtils.java        Type conversion for native query results
├── frontend/                            Vue 3 frontend
│   └── src/
│       ├── styles/theme.css             Design tokens + Element Plus variable overrides (global theming layer)
│       ├── api/                         Axios instance + per-module API wrappers
│       ├── components/AiAssistant.vue   AI floating button + right-side chat drawer
│       ├── router/                      Routes + auth guard + role guard
│       ├── stores/user.js               Pinia: token / user info
│       ├── layouts/MainLayout.vue       Role-based grouped menu + AI drawer layout coordination
│       └── views/                       admin (7) / student (2) / teacher (1)
├── sql/
│   ├── 01_schema.sql                    Database and table creation + views
│   ├── 02_seed_data.sql                 Demo data
│   └── 03_queries.sql                   8 demo queries + EXPLAIN comparison
├── scripts/
│   ├── verify-api.ps1                   42 core API regression checks (self-cleaning)
│   ├── verify-admin.ps1                 56 admin feature regression checks (self-cleaning)
│   └── verify-ai.ps1                    50 AI Q&A regression checks (self-cleaning)
└── README.md
```

---

### 5.1 Frontend Theme Layer

`src/styles/theme.css` is the **global theming layer**, imported in `main.js` after the Element Plus styles.
It does two things:

1. **Defines design tokens** (`--brand-500`, `--c-text-2`, `--radius-card`, etc.) and
   **overrides Element Plus CSS variables** (`--el-color-primary` and its light/dark variants;
   the variant values are precomputed with EP's own color-mixing formula to avoid drift from manual tuning).
2. **Unifies the look and feel of components**: cards drop hard borders in favor of layered shadows, table headers switch to light gray, dialogs get rounded corners, inline form spacing is normalized, pagination is right-aligned, and so on.

As a result, new pages built with the standard `el-card` / `el-table` / `el-form` structures automatically inherit a consistent appearance, with no need to redefine colors and spacing in each `<style scoped>` block. Page-level styles are limited to layout rules.

Shared utility classes (`.toolbar`, `.cell-main`, `.cell-sub`, `.text-muted`, etc.) are also defined in this layer.

### 5.2 AI Assistant Architecture

The core idea in one sentence: **when a deterministic answer exists, prefer it over a generated one.**

```
User question
   │
   ▼
AiService ──asks each provider in @Order──┐
   │                                      │
   │ ① KnowledgeBaseProvider              ② ClaudeProvider
   │    built-in knowledge base,          available only when api-key
   │    always available                  is configured
   │    keyword matching + role filter    fallback when the KB misses
   │                                      │
   └────────── neither matched ───────────┘
                │
                ▼
        fallback message + role-specific suggested questions
```

**Why the knowledge base comes first**: its answers have been cross-checked line by line against the system's actual behavior, so they are deterministic. The model, by contrast, may generalize or even invent menus that do not exist. The model is consulted only when the knowledge base has no supporting entry.

**Extension point**: to add a new answer source, implement the `AiAnswerProvider` interface and register it as a bean; `@Order` determines priority. No changes to `AiService` or any other provider are needed.

Matching uses **keyword-length weighting**: the longer the trigger keyword contained in the question, the higher the weight.
This rule resolves a real ambiguity — "how do I manage enrollment records" contains both "enrollment records" (4 points) and "enrollment" (2 points); with weighting, it correctly lands on the "enrollment records" entry instead of the generic "how do I enroll" one.

**There are three answer sources, and the frontend labels them** so users can judge credibility:

| Label | Meaning |
|---|---|
| Local knowledge base | Knowledge-base hit; the answer has been verified against actual system behavior |
| DeepSeek (or your configured provider name) | Knowledge-base miss; answered by the LLM |
| Not matched | Neither source answered; fallback message plus suggested questions |

**Connecting an LLM (optional)**: this uses an **Anthropic-protocol-compatible** endpoint, so official Claude, DeepSeek, OpenRouter, and self-hosted gateways all work. Only three configuration items need to change:

```yaml
ai:
  claude:
    api-key: sk-...                                  # or use the ANTHROPIC_API_KEY environment variable
    base-url: https://api.deepseek.com/anthropic     # for official Claude use https://api.anthropic.com
    model: deepseek-flash
    provider-name: DeepSeek                          # name shown in the frontend source label
```

> ⚠️ **`base-url` must be set in the configuration; do not rely on an environment variable alone.**
> The Anthropic **Java** SDK does not read `ANTHROPIC_BASE_URL` (only the Python / JS SDKs do).
> If only the environment variable is set, requests go to the official `api.anthropic.com`, third-party keys are rejected with a 403,
> and the system silently falls back to the knowledge base — superficially "chat works", but the model is never actually used.

Setting `enabled: false` forces knowledge-base-only mode; leaving `api-key` empty means no external requests are made at all.

## 6. API Reference

| Method | Path | Description | Role |
|---|---|---|---|
| POST | `/api/auth/login` | Sign in; returns a JWT | Public |
| GET | `/api/auth/me` | Current user | Authenticated |
| GET | `/api/courses` | Paginated course list (including remaining capacity) | Authenticated |
| GET | `/api/courses/semesters` | Semester options | Authenticated |
| GET | `/api/courses/my` | Courses I teach | Teacher |
| POST/PUT/DELETE | `/api/courses[/{id}]` | Course CRUD | Administrator |
| PATCH | `/api/courses/{id}/status` | Open/close enrollment | Administrator |
| GET/POST/PUT/DELETE | `/api/students[/{id}]` | Student CRUD | Administrator |
| GET/POST/PUT/DELETE | `/api/teachers[/{id}]` | Teacher CRUD | Administrator |
| GET/POST/PUT/DELETE | `/api/schedules[/{id}]` | Schedule CRUD | Administrator (read: authenticated) |
| GET | `/api/users` | Paginated account list (including linked business records) | Administrator |
| PUT | `/api/users/{id}/credentials` | Change login name / reset password (leave password blank to keep it unchanged) | Administrator |
| PATCH | `/api/users/{id}/status` | Enable / disable an account | Administrator |
| DELETE | `/api/users/{id}` | Delete an account (rejected if a student/teacher is linked) | Administrator |
| GET | `/api/enrollments` | Paginated enrollment-record query | Administrator |
| POST | `/api/enrollments/admin` | Enroll on behalf of a student | Administrator |
| PATCH | `/api/enrollments/record/{id}/withdraw` | Soft withdrawal (keeps history, returns capacity) | Administrator |
| DELETE | `/api/enrollments/record/{id}` | Hard delete (returns capacity in sync) | Administrator |
| **POST** | **`/api/enrollments`** | **Enroll** | Student |
| **DELETE** | **`/api/enrollments/{courseId}`** | **Withdraw** | Student |
| GET | `/api/enrollments/my` | My courses (including grades) | Student |
| GET | `/api/enrollments/my/timetable` | My timetable | Student |
| GET | `/api/enrollments/available` | Selectable courses (conflicts filtered out) | Student |
| GET | `/api/enrollments/conflicts` | List of conflicting courses | Student |
| GET | `/api/courses/{id}/students` | Course roster | Teacher/Administrator |
| PATCH | `/api/enrollments/score` | Enter grades | Teacher/Administrator |
| GET | `/api/stats/popular-courses` | Top 10 popular courses | Administrator |
| GET | `/api/stats/major-distribution` | Distribution by major | Administrator |
| GET | `/api/stats/teacher-workload` | Teacher workload | Administrator |
| POST | `/api/ai/chat` | AI Q&A (role parsed from the JWT) | Authenticated |
| GET | `/api/ai/suggestions` | Suggested questions for the current role | Authenticated |

Uniform response format:

```json
{ "code": 0, "message": "ok", "data": { ... } }
{ "code": 4004, "message": "Course capacity is full or enrollment is closed; enrollment failed", "data": null }
```

Error-code ranges: `1xxx` authentication/authorization / `2xxx` parameter validation / `3xxx` resource not found /
`4xxx` business-rule rejection / `5xxx` system exception.

---

## 7. Five-Minute Demo Script

1. **Administrator** — sign in as `admin` → Course Management (CRUD, capacity progress bars, close enrollment).
2. Schedule Management → pick a course → add a class-time slot → deliberately choose a slot that overlaps an existing schedule and watch the **teacher schedule conflict** be rejected.
3. Student Management → add a student (an account is created automatically) → find the new account under Account Management.
4. **Account Management** → change the new student's number to a different login name, reset the password → sign in with the new credentials to verify; also demonstrate that the **currently signed-in account cannot be disabled or deleted**.
5. **Enrollment Records** → search for a student → "Add course" to enroll on their behalf → remaining capacity increments immediately → enroll in a course that conflicts with it and watch the **time conflict be rejected** → delete the record and observe the capacity being returned.
6. **Student** — sign in as `stu001` → Enrollment Center → the "Selectable Courses" list already excludes conflicting courses; the yellow warning at the top lists the conflicting courses → enroll in a course → switch to "My Courses" → withdraw.
7. My Timetable → view the weekly timetable grid and total credits.
8. **Teacher** — sign in as `tea001` → My Courses → student roster → enter grades.
9. **Administrator** → Reports → popular-course bar chart, major distribution, teacher workload.
10. **AI Assistant** → click the floating button in the bottom-right corner to open the chat panel →
    first note how the suggested questions change with the current role (5 for administrators, 4 for students) →
    click "How do I schedule classes" to see the answer → then type "How do I add a course" and compare how the same question is answered for each of the three roles.
11. Back in the terminal, run `mvn test -Dtest=EnrollmentConcurrencyTest` to show that 100 threads competing for 10 seats produce exactly 10 successes.

---

## 8. Known Limitations and Future Work

| Limitation | Future direction |
|---|---|
| Single-node deployment, no caching | Redis caching for remaining capacity, message queue for load shedding |
| JWTs cannot be revoked proactively | Redis blacklist or switch to sessions |
| No enrollment time-window control | Add an enrollment-phase state machine + scheduled jobs |
| Reports are real-time queries | Add summary tables + scheduled precomputation |
| No pagination caching | Frontend virtual scrolling / backend cursor pagination |
| Knowledge base is keyword matching, without semantic understanding | LLM fallback is already wired in; knowledge-base-only mode is suitable for offline demos |
| AI conversations are not persisted and are lost on refresh | Add a conversation table when history is needed |
| LLM answers have no content moderation | The system prompt constrains the scope, but production should add an output-review layer |

---

## 9. FAQ

**Q: Startup fails with `Schema-validation: missing table [xxx]`**
A: `01_schema.sql` has not been run, or you are connected to the wrong database.

**Q: Startup fails with `Schema-validation: wrong column type`**
A: The table structure has been modified. Re-run `01_schema.sql` to overwrite it.

**Q: Login returns "incorrect username or password"**
A: Make sure `02_seed_data.sql` has been run. All demo accounts use the password `123456`.

**Q: Frontend requests return 404 / CORS errors**
A: Make sure the backend is running on port 8080. In frontend dev mode, Vite proxies `/api`; do not connect directly to port 8080.

**Q: A newly added student cannot log in**
A: The default username is the **student number** (not the name), and the initial password is `123456`.

**Q: Startup fails with `Schema-validation: wrong column type encountered in column [gender]`**
A: The entity and DDL column types disagree. This project aligns `student.gender` via `@Column(columnDefinition = "char(1)")`; if you have changed the table structure, update the entity mapping accordingly.

**Q: `npm install` fails with `The operation was rejected by your operating system`**
A: npm's global cache directory (e.g., `D:\nodejs\node_cache`) is not writable. Use a writable directory:

```bash
npm install --cache ./.npm-cache
```

**Q: Why are entities not using `@ManyToOne` associations?**
A: This project maps foreign keys as plain `Long` columns and routes all join queries through native SQL.
The benefit is that it completely avoids lazy-loading exceptions under `open-in-view=false`,
and it makes multi-table statistical queries more explicit than JPQL (see the various `@Query` methods).
