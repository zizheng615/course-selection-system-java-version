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

# 提问并断言回答包含某个关键词
function AskContains($who, $token, $question, $keyword) {
    $r = Api POST '/ai/chat' @{ question = $question } $token
    $hit = $r.data.answer -like "*$keyword*"
    if ($hit) {
        Write-Host ("  [PASS] [{0}] 「{1}」→ 命中「{2}」" -f $who, $question, $keyword) -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host ("  [FAIL] [{0}] 「{1}」未命中「{2}」；来源={3}" -f $who, $question, $keyword, $r.data.source) -ForegroundColor Red
        Write-Host ("         实际回答：{0}" -f ($r.data.answer -replace "`n", ' ').Substring(0, [Math]::Min(90, $r.data.answer.Length)))
        $script:fail++
    }
}

# 断言该角色问这个问题时，**知识库没有命中**（说明角色隔离生效，没拿到别的角色的条目）
#
# 判断依据是「来源」字段而不是回答文本：
#   · 来源 = 本地知识库  → 知识库命中了条目
#   · 来源 = 未匹配      → 知识库没命中，且没配置模型
#   · 来源 = 其他        → 知识库没命中，由模型兜底回答
# 配了模型之后回答文本可能恰好提到管理端菜单（模型在解释"你没有这个权限"），
# 用关键词断言会误报，所以这里只看来源。
function AskKbMiss($who, $token, $question) {
    $r = Api POST '/ai/chat' @{ question = $question } $token
    if ($r.data.source -ne '本地知识库') {
        Write-Host ("  [PASS] [{0}] 「{1}」未命中知识库（来源={2}）" -f $who, $question, $r.data.source) -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host ("  [FAIL] [{0}] 「{1}」本不该命中知识库，却命中了" -f $who, $question) -ForegroundColor Red
        Write-Host ("         实际回答：{0}" -f (($r.data.answer -replace "`n", ' ')))
        $script:fail++
    }
}

# 断言知识库命中（来源必须是「本地知识库」，而不是被模型接走）
function AskKbHit($who, $token, $question) {
    $r = Api POST '/ai/chat' @{ question = $question } $token
    if ($r.data.source -eq '本地知识库') {
        Write-Host ("  [PASS] [{0}] 「{1}」由知识库命中" -f $who, $question) -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host ("  [FAIL] [{0}] 「{1}」应由知识库命中，实际来源={2}" -f $who, $question, $r.data.source) -ForegroundColor Red
        $script:fail++
    }
}

$admin = Api POST '/auth/login' @{ username = 'admin'; password = '123456' } $null
$stu   = Api POST '/auth/login' @{ username = 'stu001'; password = '123456' } $null
$tea   = Api POST '/auth/login' @{ username = 'tea001'; password = '123456' } $null
$adminToken = $admin.data.token
$stuToken   = $stu.data.token
$teaToken   = $tea.data.token

Write-Host "`n===== A. 认证与鉴权 =====" -ForegroundColor Cyan
$r = Api GET '/ai/suggestions' $null $null
Check '未登录访问被拦截(1001)' $r.code 1001

$r = Api POST '/ai/chat' @{ question = '' } $stuToken
Check '空问题被拒(2000)' $r.code 2000

$r = Api POST '/ai/chat' @{ question = ('x' * 600) } $stuToken
Check '超长问题被拒(2000)' $r.code 2000

Write-Host "`n===== B. 学生提问 =====" -ForegroundColor Cyan
AskContains '学生' $stuToken '怎么选课' '选课中心'
AskContains '学生' $stuToken '怎么退课' '我的课程'
AskContains '学生' $stuToken '为什么有的课选不了' '时间冲突'
AskContains '学生' $stuToken '在哪看课表' '我的课表'
AskContains '学生' $stuToken '余量是什么意思' '容量'
AskContains '学生' $stuToken '在哪看成绩' '成绩'
AskContains '学生' $stuToken '你好' '智能助手'
AskContains '学生' $stuToken '系统有哪些功能' '我的课表'

Write-Host "`n===== C. 角色隔离（学生问管理端问题不应命中知识库）=====" -ForegroundColor Cyan
AskKbMiss '学生' $stuToken '怎么新增课程'
AskKbMiss '学生' $stuToken '怎么排课'
AskKbMiss '学生' $stuToken '怎么重置学生密码'

Write-Host "`n===== D. 教师提问 =====" -ForegroundColor Cyan
AskContains '教师' $teaToken '怎么查看学生名单' '学生名单'
AskContains '教师' $teaToken '怎么录入成绩' '成绩'
AskContains '教师' $teaToken '系统有哪些功能' '我的课程'
AskKbMiss '教师' $teaToken '怎么新增课程'

Write-Host "`n===== E. 管理员提问 =====" -ForegroundColor Cyan
AskContains '管理员' $adminToken '怎么新增课程' '课程管理'
AskContains '管理员' $adminToken '怎么排课' '排课管理'
AskContains '管理员' $adminToken '排课冲突怎么办' '教师'
AskContains '管理员' $adminToken '怎么重置学生密码' '账号管理'
AskContains '管理员' $adminToken '统计报表在哪里' '热门课程'
AskContains '管理员' $adminToken '怎么管理选课记录' '代'
AskContains '管理员' $adminToken '怎么调整课程容量' '已选人数'
AskContains '管理员' $adminToken '为什么删不掉学生' '选课记录'
AskContains '管理员' $adminToken '系统有哪些功能' '统计报表'

Write-Host "`n===== F. 通用问题 =====" -ForegroundColor Cyan
AskContains '学生' $stuToken '忘记密码怎么办' '管理员'
AskContains '学生' $stuToken '初始密码是什么' '123456'
AskContains '学生' $stuToken '会不会超卖' '原子'

Write-Host "`n===== G. 来源标记 =====" -ForegroundColor Cyan
# 来源必须能区分三种情况，否则用户无法判断答案的可信度，测试也无法定位问题：
#   本地知识库 = 知识库命中 ；未匹配 = 都没答上来 ；其他 = 模型回答
$r = Api POST '/ai/chat' @{ question = '今天天气怎么样' } $stuToken
Check '无关问题返回成功' $r.code 0
Check '无关问题未命中知识库' ($r.data.source -ne '本地知识库') 'True'
Write-Host ("  无关问题的来源: {0}" -f $r.data.source)

$r = Api POST '/ai/chat' @{ question = '怎么选课' } $stuToken
Check '知识库命中时来源为「本地知识库」' $r.data.source '本地知识库'
Check '返回了追问建议' ($r.data.suggestions.Count -gt 0) 'True'

$r = Api GET '/ai/suggestions' $null $stuToken
Check '推荐问题接口正常' $r.code 0

Write-Host "`n===== H. 推荐问题按角色区分 =====" -ForegroundColor Cyan
$r = Api GET '/ai/suggestions' $null $stuToken
Check '学生建议数' $r.data.Count 4
Write-Host ("  学生：{0}" -f ($r.data -join ' / '))
$r = Api GET '/ai/suggestions' $null $teaToken
Check '教师建议数' $r.data.Count 3
Write-Host ("  教师：{0}" -f ($r.data -join ' / '))
$r = Api GET '/ai/suggestions' $null $adminToken
Check '管理员建议数' $r.data.Count 5
Write-Host ("  管理员：{0}" -f ($r.data -join ' / '))

Write-Host "`n===== I. 推荐问题必须全部由知识库命中 =====" -ForegroundColor Cyan
# 推荐问题是引导用户入门的，如果自己都命中不了知识库，就会被模型接走或落兜底，
# 说明触发词没写好 —— 这组断言就是防这个的。
foreach ($pair in @(@('学生', $stuToken), @('教师', $teaToken), @('管理员', $adminToken))) {
    $who = $pair[0]; $tk = $pair[1]
    $list = (Api GET '/ai/suggestions' $null $tk).data
    foreach ($q in $list) {
        AskKbHit $who $tk $q
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host ("  通过 {0} 项，失败 {1} 项" -f $script:pass, $script:fail) -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
Write-Host "========================================`n" -ForegroundColor Cyan
