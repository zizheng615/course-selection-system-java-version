<template>
  <el-card>
    <!-- 工具栏 -->
    <el-form :inline="true">
      <el-form-item label="关键词">
        <el-input
          v-model="query.keyword"
          placeholder="账号 / 姓名"
          clearable
          style="width: 200px"
          @keyup.enter="load(1)"
        />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="query.role" clearable placeholder="全部" style="width: 130px">
          <el-option label="管理员" value="ADMIN" />
          <el-option label="教师" value="TEACHER" />
          <el-option label="学生" value="STUDENT" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load(1)">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 列表 -->
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column label="登录账号" min-width="150">
        <template #default="{ row }">
          <span class="cell-main">{{ row.username }}</span>
          <div v-if="row.status === 0" class="cell-sub">已禁用</div>
        </template>
      </el-table-column>
      <el-table-column prop="realName" label="姓名" width="110" />
      <el-table-column label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="roleTag(row.role)" size="small" effect="light">
            {{ row.roleName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="关联业务" min-width="220">
        <template #default="{ row }">
          <template v-if="row.linkType === '—'">
            <span class="text-muted">无（管理账号）</span>
          </template>
          <template v-else>
            <span class="cell-main">{{ row.linkNo }}</span>
            <div class="cell-sub">{{ row.linkExtra }}</div>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="light">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">改账号密码</el-button>
          <el-button
            link
            :type="row.status === 1 ? 'warning' : 'success'"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      background
      layout="total, sizes, prev, pager, next"
      :total="total"
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :page-sizes="[10, 20, 50]"
      @current-change="load()"
      @size-change="load(1)"
    />

    <!-- 改账号 / 重置密码 -->
    <el-dialog v-model="dialogVisible" title="修改登录凭据" width="460px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="新密码留空表示只修改登录账号，不改变原密码。"
        style="margin-bottom: 16px"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="姓名">
          <span class="readonly-text">{{ current?.realName }}（{{ current?.roleName }}）</span>
        </el-form-item>
        <el-form-item label="登录账号" prop="username">
          <el-input v-model="form.username" placeholder="3-50 位字母、数字或 _ . -" />
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="留空则不修改"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            placeholder="再次输入新密码"
            :disabled="!form.password"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteUser, pageUsers, updateUserCredentials, updateUserStatus } from '@/api/user'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)

const query = reactive({ keyword: '', role: '', page: 1, size: 10 })

const dialogVisible = ref(false)
const formRef = ref()
const current = ref(null)
const form = reactive({ id: null, username: '', password: '', confirmPassword: '' })

const rules = {
  username: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    { min: 3, max: 50, message: '长度需在 3-50 之间', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_.-]+$/,
      message: '只能包含字母、数字、下划线、点或短横线',
      trigger: 'blur'
    }
  ],
  password: [
    {
      validator(_rule, value, callback) {
        if (!value) return callback()
        if (value.length < 6 || value.length > 32) {
          return callback(new Error('密码长度需在 6-32 之间'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    {
      validator(_rule, value, callback) {
        if (!form.password) return callback()
        if (value !== form.password) return callback(new Error('两次输入的密码不一致'))
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const roleTag = (role) => ({ ADMIN: 'danger', TEACHER: 'warning', STUDENT: 'primary' })[role] || 'info'

async function load(page) {
  if (page) query.page = page
  loading.value = true
  try {
    const data = await pageUsers(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.role = ''
  load(1)
}

function openEdit(row) {
  current.value = row
  Object.assign(form, {
    id: row.id,
    username: row.username,
    password: '',
    confirmPassword: ''
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function handleSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    await updateUserCredentials(form.id, {
      username: form.username,
      // 空字符串会让后端的 @Size(min=6) 校验失败，必须显式传 null 表示"不改密码"
      password: form.password ? form.password : null
    })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch {
    // 错误提示已由 axios 拦截器统一处理
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 1 ? '启用' : '禁用'
  await ElMessageBox.confirm(
    `确定${action}账号「${row.username}」吗？${next === 0 ? '禁用后该账号将无法登录。' : ''}`,
    `${action}确认`,
    { type: 'warning' }
  )
  await updateUserStatus(row.id, next)
  ElMessage.success(`已${action}`)
  load()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(
    `确定删除账号「${row.username}」吗？已绑定学生或教师信息的账号会被拒绝。`,
    '删除确认',
    { type: 'warning' }
  )
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(() => load())
</script>

<style scoped>
.readonly-text {
  color: var(--c-text-2);
}
</style>
