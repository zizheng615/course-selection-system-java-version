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

# 只断言 code，不撤销副作用；用于"登录应成功/失败"这类检查
function Check($name, $actual, $expected) {
    if ("$actual" -eq "$expected") {
        Write-Host ("  [PASS] {0}  => {1}" -f $name, $actual) -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host ("  [FAIL] {0}  期望={1} 实际={2}" -f $name, $expected, $actual) -ForegroundColor Red
        $script:fail++
    }
}

function Login($u, $p) {
    return (Api POST '/auth/login' @{ username = $u; password = $p } $null).code
}

$admin = Api POST '/auth/login' @{ username = 'admin'; password = '123456' } $null
if ($admin.code -ne 0) { Write-Host "管理员登录失败，终止" -ForegroundColor Red; exit 1 }
$adminToken = $admin.data.token
$adminUserId = $admin.data.userId

$stu = Api POST '/auth/login' @{ username = 'stu001'; password = '123456' } $null
$stuToken = $stu.data.token

Write-Host "`n===== A. 账号列表与筛选 =====" -ForegroundColor Cyan
$r = Api GET '/users?page=1&size=10' $null $adminToken
Check '账号列表' $r.code 0
Check '账号总数（61）' $r.data.total 61
Write-Host ("  首页首条：{0} / {1} / {2} / {3}" -f $r.data.list[0].username, $r.data.list[0].realName, $r.data.list[0].roleName, $r.data.list[0].linkType)

$r = Api GET '/users?role=STUDENT&page=1&size=5' $null $adminToken
Check '按角色筛选-学生(50)' $r.data.total 50
$targetUserId = $r.data.list[0].id
$targetUsername = $r.data.list[0].username
Write-Host ("  目标学生账号：{0}（userId={1}，{2} {3}）" -f $targetUsername, $targetUserId, $r.data.list[0].linkNo, $r.data.list[0].linkExtra)

$r = Api GET '/users?role=TEACHER&page=1&size=5' $null $adminToken
Check '按角色筛选-教师(10)' $r.data.total 10

$r = Api GET '/users?role=ADMIN&page=1&size=5' $null $adminToken
Check '按角色筛选-管理员(1)' $r.data.total 1
Check '管理员无关联业务' $r.data.list[0].linkType '—'

$r = Api GET '/users?keyword=张伟&page=1&size=5' $null $adminToken
Check '按关键词搜索' $r.code 0
Write-Host ("  命中 {0} 条" -f $r.data.total)

$r = Api GET '/users' $null $stuToken
Check '学生访问账号管理被拒(1003)' $r.code 1003

Write-Host "`n===== B. 修改登录账号 =====" -ForegroundColor Cyan
$newName = $targetUsername + 'x'
$r = Api PUT "/users/$targetUserId/credentials" @{ username = $newName; password = $null } $adminToken
Check '改登录名（不改密码）' $r.code 0
Check "新账号 $newName 可登录" (Login $newName '123456') 0
Check "旧账号 $targetUsername 失效" (Login $targetUsername '123456') 1001

$r = Api PUT "/users/$targetUserId/credentials" @{ username = 'tea001'; password = $null } $adminToken
Check '改成已占用的账号被拒(4007)' $r.code 4007

$r = Api PUT "/users/$targetUserId/credentials" @{ username = 'ab'; password = $null } $adminToken
Check '账号过短被拒(2000)' $r.code 2000

$r = Api PUT "/users/$targetUserId/credentials" @{ username = 'bad name!'; password = $null } $adminToken
Check '非法字符被拒(2000)' $r.code 2000

Write-Host "`n===== C. 重置密码 =====" -ForegroundColor Cyan
$r = Api PUT "/users/$targetUserId/credentials" @{ username = $newName; password = 'newpass123' } $adminToken
Check '重置密码' $r.code 0
Check '新密码可登录' (Login $newName 'newpass123') 0
Check '旧密码失效' (Login $newName '123456') 1001

$r = Api PUT "/users/$targetUserId/credentials" @{ username = $newName; password = '123' } $adminToken
Check '密码过短被拒(2000)' $r.code 2000

Write-Host "`n===== D. 还原目标账号 =====" -ForegroundColor Cyan
$r = Api PUT "/users/$targetUserId/credentials" @{ username = $targetUsername; password = '123456' } $adminToken
Check '还原账号与密码' $r.code 0
Check "还原后可登录（$targetUsername/123456）" (Login $targetUsername '123456') 0

Write-Host "`n===== E. 启用 / 禁用 =====" -ForegroundColor Cyan
$r = Api PATCH "/users/$targetUserId/status?status=0" $null $adminToken
Check '禁用账号' $r.code 0
Check '禁用后无法登录(1001)' (Login $targetUsername '123456') 1001
Check '禁用后列表状态位=0' (Api GET "/users?keyword=$targetUsername" $null $adminToken).data.list[0].status 0

$r = Api PATCH "/users/$targetUserId/status?status=1" $null $adminToken
Check '重新启用' $r.code 0
Check '启用后可登录' (Login $targetUsername '123456') 0

$r = Api PATCH "/users/$adminUserId/status?status=0" $null $adminToken
Check '不能禁用当前登录账号(2000)' $r.code 2000

$r = Api PATCH "/users/$targetUserId/status?status=9" $null $adminToken
Check '非法状态值被拒(2000)' $r.code 2000

Write-Host "`n===== F. 删除账号的保护 =====" -ForegroundColor Cyan
$r = Api DELETE "/users/$adminUserId" $null $adminToken
Check '不能删除当前登录账号(2000)' $r.code 2000

$r = Api DELETE "/users/$targetUserId" $null $adminToken
Check '已绑定学生的账号拒绝删除(4006)' $r.code 4006
Write-Host ("  提示：{0}" -f $r.message)

$r = Api DELETE "/users/999999" $null $adminToken
Check '账号不存在(3001)' $r.code 3001

Write-Host "`n===== G. 选课记录：查询与筛选 =====" -ForegroundColor Cyan
$r = Api GET '/enrollments?page=1&size=5' $null $adminToken
Check '全量选课记录' $r.code 0
Check '记录总数（218）' $r.data.total 218
Write-Host ("  首条：{0} {1} → {2} {3} [{4}]" -f $r.data.list[0].studentNo, $r.data.list[0].studentName, $r.data.list[0].courseCode, $r.data.list[0].courseName, $r.data.list[0].status)

$r = Api GET '/enrollments?status=SELECTED&page=1&size=5' $null $adminToken
Check '按状态筛选' $r.code 0

$r = Api GET '/enrollments?courseId=1&page=1&size=100' $null $adminToken
$course1Total = $r.data.total
Check '按课程筛选' $r.code 0
Write-Host ("  课程 1（数据结构）选课记录 {0} 条" -f $course1Total)

$r = Api GET '/enrollments?keyword=2023001&page=1&size=5' $null $adminToken
Check '按学号搜索' $r.code 0

$r = Api GET '/enrollments' $null $stuToken
Check '学生访问选课记录管理被拒(1003)' $r.code 1003

Write-Host "`n===== H. 管理员代学生加选课（严格校验）=====" -ForegroundColor Cyan
$r = Api POST '/students' @{ studentNo = 'T887766'; name = '记录测试生'; gender = 'M'; major = '软件工程'; grade = '2023' } $adminToken
Check '创建测试学生' $r.code 0
$tmpStudentId = $r.data.id

$r = Api GET '/enrollments?keyword=T887766&page=1&size=5' $null $adminToken
Check '新学生初始无选课记录' $r.data.total 0

$beforeCount = (Api GET '/courses/1' $null $adminToken).data.enrolledCount

$r = Api POST '/enrollments/admin' @{ studentId = $tmpStudentId; courseId = 1 } $adminToken
Check '代选课成功' $r.code 0
Check '课程已选人数 +1' (Api GET '/courses/1' $null $adminToken).data.enrolledCount ($beforeCount + 1)

$r = Api POST '/enrollments/admin' @{ studentId = $tmpStudentId; courseId = 1 } $adminToken
Check '重复加选被拒(4002)' $r.code 4002

$r = Api POST '/enrollments/admin' @{ studentId = $tmpStudentId; courseId = 17 } $adminToken
Check '时间冲突被拒(4003)' $r.code 4003
Write-Host ("  提示：{0}" -f $r.message)

$r = Api POST '/enrollments/admin' @{ studentId = 999999; courseId = 1 } $adminToken
Check '学生不存在(3001)' $r.code 3001

$r = Api GET '/enrollments?keyword=T887766&page=1&size=5' $null $adminToken
$tmpEnrollId = $r.data.list[0].id
Check '记录已生成' $r.data.total 1

Write-Host "`n===== I. 管理员退课（软退）=====" -ForegroundColor Cyan
$r = Api PATCH "/enrollments/record/$tmpEnrollId/withdraw" $null $adminToken
Check '软退课' $r.code 0
Check '容量已归还' (Api GET '/courses/1' $null $adminToken).data.enrolledCount $beforeCount
Check '记录状态变为 WITHDRAWN' (Api GET '/enrollments?keyword=T887766&page=1&size=5' $null $adminToken).data.list[0].status 'WITHDRAWN'
Check '记录仍然保留（历史未丢失）' (Api GET '/enrollments?keyword=T887766&page=1&size=5' $null $adminToken).data.total 1

$r = Api PATCH "/enrollments/record/$tmpEnrollId/withdraw" $null $adminToken
Check '重复退课被拒(4005)' $r.code 4005

Write-Host "`n===== J. 管理员删除记录（硬删）=====" -ForegroundColor Cyan
# 先重新加选，验证硬删时也会归还容量
$r = Api POST '/enrollments/admin' @{ studentId = $tmpStudentId; courseId = 1 } $adminToken
Check '重新加选（复用已退记录）' $r.code 0
Check '容量再次 +1' (Api GET '/courses/1' $null $adminToken).data.enrolledCount ($beforeCount + 1)

$tmpEnrollId = (Api GET '/enrollments?keyword=T887766&page=1&size=5' $null $adminToken).data.list[0].id
$r = Api DELETE "/enrollments/record/$tmpEnrollId" $null $adminToken
Check '硬删记录' $r.code 0
Check '容量已归还' (Api GET '/courses/1' $null $adminToken).data.enrolledCount $beforeCount
Check '记录已消失' (Api GET '/enrollments?keyword=T887766&page=1&size=5' $null $adminToken).data.total 0

$r = Api DELETE "/enrollments/record/999999" $null $adminToken
Check '记录不存在(3001)' $r.code 3001

Write-Host "`n===== K. 清理测试数据 =====" -ForegroundColor Cyan
$r = Api DELETE "/students/$tmpStudentId" $null $adminToken
Check '删除测试学生（记录已清空，可删）' $r.code 0

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host ("  通过 {0} 项，失败 {1} 项" -f $script:pass, $script:fail) -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
Write-Host "========================================`n" -ForegroundColor Cyan
