<template>
  <el-card>
    <el-form :inline="true" class="search-bar">
      <el-form-item label="关键词">
        <el-input
          v-model="query.keyword"
          placeholder="姓名 / 学号"
          clearable
          style="width: 200px"
          @keyup.enter="load(1)"
        />
      </el-form-item>
      <el-form-item label="专业">
        <el-select v-model="query.major" clearable placeholder="全部" style="width: 220px">
          <el-option v-for="m in majors" :key="m" :label="m" :value="m" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load(1)">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <el-button type="success" @click="openDialog()">新增学生</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="studentNo" label="学号" width="120" />
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column label="性别" width="70" align="center">
        <template #default="{ row }">{{ row.gender === 'F' ? '女' : '男' }}</template>
      </el-table-column>
      <el-table-column prop="major" label="专业" min-width="180" />
      <el-table-column prop="grade" label="年级" width="90" align="center" />
      <el-table-column prop="phone" label="联系电话" width="140" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pagination"
      background
      layout="total, sizes, prev, pager, next"
      :total="total"
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :page-sizes="[10, 20, 50]"
      @current-change="load()"
      @size-change="load(1)"
    />

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑学生' : '新增学生'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="学号" prop="studentNo">
          <el-input v-model="form.studentNo" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio label="M">男</el-radio>
            <el-radio label="F">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="专业" prop="major">
          <el-input v-model="form.major" />
        </el-form-item>
        <el-form-item label="年级" prop="grade">
          <el-input v-model="form.grade" placeholder="如 2023" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" />
        </el-form-item>
        <template v-if="!form.id">
          <el-alert
            type="info"
            :closable="false"
            title="新增学生时会同时创建登录账号：默认账号为学号，初始密码 123456"
            style="margin-bottom: 12px"
          />
        </template>
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
import {
  createStudent,
  deleteStudent,
  getMajors,
  pageStudents,
  updateStudent
} from '@/api/student'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const majors = ref([])

const query = reactive({ keyword: '', major: '', page: 1, size: 10 })

const dialogVisible = ref(false)
const formRef = ref()
const emptyForm = () => ({
  id: null,
  studentNo: '',
  name: '',
  gender: 'M',
  major: '',
  grade: '2023',
  phone: ''
})
const form = reactive(emptyForm())

const rules = {
  studentNo: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  major: [{ required: true, message: '请输入专业', trigger: 'blur' }],
  grade: [{ required: true, message: '请输入年级', trigger: 'blur' }]
}

async function load(page) {
  if (page) query.page = page
  loading.value = true
  try {
    const data = await pageStudents(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.major = ''
  load(1)
}

function openDialog(row) {
  Object.assign(form, row ? { ...row } : emptyForm())
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function handleSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await updateStudent(form.id, form)
      ElMessage.success('修改成功')
    } else {
      await createStudent(form)
      ElMessage.success('新增成功，初始密码 123456')
    }
    dialogVisible.value = false
    load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(
    `确定删除学生「${row.name}」吗？若存在选课记录将被拒绝。`,
    '删除确认',
    { type: 'warning' }
  )
  await deleteStudent(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(async () => {
  majors.value = await getMajors()
  load()
})
</script>

<style scoped>
.search-bar {
  margin-bottom: 8px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
