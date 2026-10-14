[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = 'http://localhost:8080/api'
$script:pass = 0
$script:fail = 0

function Api($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{
        Uri         = "$base$path"
        Method      = $method
        Headers     = $headers
        ContentType = 'application/json; charset=utf-8'
    }
    if ($null -ne $body) {
        $json = $body | ConvertTo-Json -Compress
        $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    return Invoke-RestMethod @params
}

function Check($name, $actual, $expected) {
    if ("$actual" -eq "$expected") {
        Write-Host ("  [PASS] {0}  => {1}" -f $name, $actual) -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host ("  [FAIL] {0}  期望={1} 实际={2}" -f $name, $expected, $actual) -ForegroundColor Red
        $script:fail++
    }
}

Write-Host "`n===== 1. 认证与 JWT =====" -ForegroundColor Cyan
$r = Api POST '/auth/login' @{ username = 'admin'; password = '123456' } $null
Check '管理员登录' $r.code 0
$adminToken = $r.data.token
Check '管理员角色' $r.data.role 'ADMIN'

$r = Api POST '/auth/login' @{ username = 'stu001'; password = '123456' } $null
Check '学生登录' $r.code 0
$stuToken = $r.data.token
Check '学生带 refId' ($null -ne $r.data.refId) 'True'

$r = Api POST '/auth/login' @{ username = 'admin'; password = 'wrong-password' } $null
Check '错误密码被拒(1001)' $r.code 1001

$r = Api POST '/auth/login' @{ username = 'no-such-user'; password = '123456' } $null
Check '不存在账号同一提示(1001)' $r.code 1001

$r = Api GET '/courses' $null $null
Check '未带 Token 被拦截(1001)' $r.code 1001

$r = Api GET '/courses' $null 'garbage.token.value'
Check '伪造 Token 被拒(1002)' $r.code 1002

Write-Host "`n===== 2. 角色权限 =====" -ForegroundColor Cyan
$r = Api GET '/students' $null $stuToken
Check '学生访问管理员接口被拒(1003)' $r.code 1003

$r = Api GET '/stats/popular-courses' $null $stuToken
Check '学生访问统计接口被拒(1003)' $r.code 1003

Write-Host "`n===== 3. 管理员 CRUD =====" -ForegroundColor Cyan
$r = Api POST '/students' @{ studentNo = 'T998877'; name = '验证学生'; gender = 'F'; major = '软件工程'; grade = '2023'; phone = '13900000000' } $adminToken
Check '新增学生（自动建账号）' $r.code 0
$testStudentId = $r.data.id

$r = Api POST '/students' @{ studentNo = 'T998877'; name = '重复学号'; gender = 'M'; major = '软件工程'; grade = '2023' } $adminToken
Check '重复学号被拒(4007)' $r.code 4007

$r = Api PUT "/students/$testStudentId" @{ studentNo = 'T998877'; name = '验证学生改名'; gender = 'F'; major = '软件工程'; grade = '2023'; phone = '13900000001' } $adminToken
Check '修改学生' $r.code 0
Check '修改已生效' $r.data.name '验证学生改名'

$r = Api POST '/teachers' @{ teacherNo = 'T990000'; name = '验证教师'; title = '讲师'; dept = '测试学院' } $adminToken
Check '新增教师' $r.code 0
$testTeacherId = $r.data.id
$r = Api DELETE "/teachers/$testTeacherId" $null $adminToken
Check '删除无课程教师' $r.code 0

Write-Host "`n===== 4. 选课核心流程 =====" -ForegroundColor Cyan
$r = Api POST '/auth/login' @{ username = 'T998877'; password = '123456' } $null
Check '新学生登录（账号=学号）' $r.code 0
$newToken = $r.data.token

$r = Api GET '/enrollments/available?semester=2026-2027-1' $null $newToken
$availableCount = $r.data.Count
Write-Host ("  初始可选课程数：{0}" -f $availableCount)

# 课程 1 数据结构(周三1-2) 与 课程 17 线性代数(周三1-2) 是刻意制造的冲突对
$r = Api POST '/enrollments' @{ courseId = 1 } $newToken
Check '选课成功(code=0)' $r.code 0

$r = Api GET '/enrollments/my?semester=2026-2027-1' $null $newToken
Check '我的课程数=1' $r.data.Count 1

$r = Api POST '/enrollments' @{ courseId = 1 } $newToken
Check '重复选课被拒(4002)' $r.code 4002

$r = Api POST '/enrollments' @{ courseId = 17 } $newToken
Check '时间冲突被拒(4003)' $r.code 4003
Write-Host ("  提示：{0}" -f $r.message)

$r = Api POST '/enrollments' @{ courseId = 999999 } $newToken
Check '不存在的课程(3001)' $r.code 3001

$r = Api GET '/enrollments/conflicts?semester=2026-2027-1' $null $newToken
Check '冲突课程查询' $r.code 0
Write-Host ("  冲突课程：{0}" -f (($r.data | ForEach-Object { $_.name }) -join '、'))

$r = Api GET '/enrollments/available?semester=2026-2027-1' $null $newToken
$afterSelect = $r.data.Count
Write-Host ("  选课并排除冲突后可选数：{0}" -f $afterSelect)

Write-Host "`n===== 5. 退课与容量归还 =====" -ForegroundColor Cyan
$r = Api DELETE '/enrollments/1' $null $newToken
Check '退课' $r.code 0

$r = Api GET '/enrollments/my?semester=2026-2027-1' $null $newToken
Check '退课后我的课程数=0' $r.data.Count 0

$r = Api POST '/enrollments' @{ courseId = 17 } $newToken
Check '退课后原冲突课程变为可选' $r.code 0

$r = Api DELETE '/enrollments/17' $null $newToken
Check '再次退课' $r.code 0

$r = Api DELETE '/enrollments/17' $null $newToken
Check '重复退课被拒(4005)' $r.code 4005

Write-Host "`n===== 6. 课表查询 =====" -ForegroundColor Cyan
$r = Api GET '/enrollments/my/timetable?semester=2026-2027-1' $null $stuToken
Check 'stu001 课表' $r.code 0
Write-Host ("  stu001 课表条目数：{0}" -f $r.data.Count)
foreach ($t in $r.data) {
    Write-Host ("    {0} 第{1}-{2}节 {3} {4}" -f $t.weekdayName, $t.startSection, $t.endSection, $t.courseName, $t.classroom)
}

Write-Host "`n===== 7. 排课与教师时间冲突 =====" -ForegroundColor Cyan
# 课程 22 云计算技术，教师 = 周文博。他已有 计算机网络 周四 1-2 节
$r = Api POST '/schedules' @{ courseId = 22; weekday = 4; startSection = 1; endSection = 2; startWeek = 1; endWeek = 16; classroom = '测试教室' } $adminToken
Check '教师排课冲突被拒(4003)' $r.code 4003
Write-Host ("  提示：{0}" -f $r.message)

# 换一个教师空闲的时段：周五 9-10 节
$r = Api POST '/schedules' @{ courseId = 22; weekday = 5; startSection = 9; endSection = 10; startWeek = 1; endWeek = 8; classroom = '测试教室' } $adminToken
Check '空闲时段排课成功' $r.code 0
$testScheduleId = $r.data.id

$r = Api POST '/schedules' @{ courseId = 22; weekday = 5; startSection = 10; endSection = 11; startWeek = 1; endWeek = 8; classroom = '重叠测试' } $adminToken
Check '同教师节次重叠被拒(4003)' $r.code 4003

$r = Api GET "/schedules?courseId=22" $null $adminToken
Check '查询课程上课时间' $r.code 0

$r = Api DELETE "/schedules/$testScheduleId" $null $adminToken
Check '删除测试排课' $r.code 0

Write-Host "`n===== 8. 统计报表 =====" -ForegroundColor Cyan
$r = Api GET '/stats/popular-courses?semester=2026-2027-1&limit=5' $null $adminToken
Check '热门课程 Top5' $r.code 0
$rank = 1
foreach ($c in $r.data) {
    Write-Host ("    {0}. {1}（{2}）已选 {3} 人，满座率 {4}%" -f $rank, $c.courseName, $c.teacherName, $c.selectedCount, $c.fillRate)
    $rank++
}

$r = Api GET '/stats/major-distribution' $null $adminToken
Check '专业分布' $r.code 0
foreach ($m in $r.data) {
    Write-Host ("    {0}：{1} 人，{2} 人次，人均 {3} 门" -f $m.major, $m.studentCount, $m.enrollmentCount, $m.avgCoursesPerStudent)
}

$r = Api GET '/stats/teacher-workload?semester=2026-2027-1' $null $adminToken
Check '教师工作量' $r.code 0
Write-Host ("  统计到教师 {0} 位" -f $r.data.Count)

Write-Host "`n===== 9. 引用完整性保护 =====" -ForegroundColor Cyan
# 该学生选过课又退课，留下 WITHDRAWN 历史记录，外键 RESTRICT 应阻止删除
$r = Api DELETE "/students/$testStudentId" $null $adminToken
Check '有选课历史时拒绝删除(4006)' $r.code 4006
Write-Host ("  提示：{0}" -f $r.message)

Write-Host "`n===== 10. 自清理 =====" -ForegroundColor Cyan
# 第 9 节刻意让测试学生"删不掉"，所以这里必须先把它的选课记录清空再删学生。
# 否则每跑一次就留下一条需要手工 SQL 清理的脏数据，也会让后续断言的总数对不上。
$r = Api GET '/enrollments?keyword=T998877&page=1&size=100' $null $adminToken
$testEnrollmentIds = @($r.data.list | ForEach-Object { $_.id })
foreach ($eid in $testEnrollmentIds) {
    Api DELETE "/enrollments/record/$eid" $null $adminToken | Out-Null
}
Check '清空测试学生的选课记录' (Api GET '/enrollments?keyword=T998877&page=1&size=100' $null $adminToken).data.total 0
Check '删除测试学生' (Api DELETE "/students/$testStudentId" $null $adminToken).code 0
Check '账号总数回到种子状态(61)' (Api GET '/users?page=1&size=1' $null $adminToken).data.total 61
Check '选课记录总数回到种子状态(218)' (Api GET '/enrollments?page=1&size=1' $null $adminToken).data.total 218

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host ("  通过 {0} 项，失败 {1} 项" -f $script:pass, $script:fail) -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
Write-Host "========================================`n" -ForegroundColor Cyan
