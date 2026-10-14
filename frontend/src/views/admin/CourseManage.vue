<template>
  <el-card>
    <!-- 搜索栏 -->
    <el-form :inline="true" class="search-bar">
      <el-form-item label="关键词">
        <el-input
          v-model="query.keyword"
          placeholder="课程名称 / 课程代码"
          clearable
          style="width: 220px"
          @keyup.enter="load(1)"
        />
      </el-form-item>
      <el-form-item label="学期">
        <el-select v-model="query.semester" clearable placeholder="全部" style="width: 160px">
          <el-option v-for="s in semesters" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
          <el-option label="开放选课" value="OPEN" />
          <el-option label="已关闭" value="CLOSED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load(1)">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <el-button type="success" @click="openDialog()">新增课程</el-button>
      </el-form-item>
    </el-form>

    <!-- 列表 -->
    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="courseCode" label="课程代码" width="100" />
      <el-table-column prop="name" label="课程名称" min-width="140" show-overflow-tooltip />
      <el-table-column label="授课教师" width="105">
        <template #default="{ row }">
          <div class="cell-main">{{ row.teacherName }}</div>
          <div v-if="row.teacherTitle" class="cell-sub">{{ row.teacherTitle }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="credit" label="学分" width="60" align="center" />
      <el-table-column prop="hours" label="学时" width="60" align="center" />
      <el-table-column prop="courseType" label="类型" width="70" align="center" />
      <el-table-column label="选课情况" width="140">
        <template #default="{ row }">
          <el-progress
            :percentage="percent(row)"
            :status="row.remaining === 0 ? 'exception' : undefined"
            :stroke-width="6"
            :show-text="false"
          />
          <div class="cell-sub">
            {{ row.enrolledCount }} / {{ row.capacity }}（余 {{ row.remaining }}）
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="75" align="center">
        <template #default="{ row }">
          <el-tag
            :type="row.status === 'OPEN' ? 'success' : 'info'"
            size="small"
            effect="light"
          >
            {{ row.status === 'OPEN' ? '开放' : '关闭' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button link type="warning" @click="toggleStatus(row)">
            {{ row.status === 'OPEN' ? '关闭' : '开放' }}
          </el-button>
          <el-button link type="info" @click="goSchedule(row)">排课</el-button>
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

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑课程' : '新增课程'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="课程代码" prop="courseCode">
              <el-input v-model="form.courseCode" placeholder="如 CS101" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="课程名称" prop="name">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="学分" prop="credit">
              <el-input-number v-model="form.credit" :min="0.5" :max="20" :step="0.5" :precision="1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="学时" prop="hours">
              <el-input-number v-model="form.hours" :min="1" :max="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="课程类型" prop="courseType">
              <el-select v-model="form.courseType" style="width: 100%">
                <el-option label="必修" value="必修" />
                <el-option label="选修" value="选修" />
                <el-option label="公选" value="公选" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开课院系" prop="dept">
              <el-input v-model="form.dept" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="学期" prop="semester">
              <el-input v-model="form.semester" placeholder="如 2026-2027-1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="授课教师" prop="teacherId">
              <el-select v-model="form.teacherId" filterable style="width: 100%">
                <el-option
                  v-for="t in teachers"
                  :key="t.id"
                  :label="`${t.name}（${t.dept}）`"
                  :value="t.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="容量" prop="capacity">
              <el-input-number v-model="form.capacity" :min="1" :max="1000" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio label="OPEN">开放</el-radio>
                <el-radio label="CLOSED">关闭</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="课程简介">
              <el-input v-model="form.intro" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createCourse,
  deleteCourse,
  getSemesters,
  pageCourses,
  updateCourse,
  updateCourseStatus
} from '@/api/course'
import { getAllTeachers } from '@/api/teacher'

const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const semesters = ref([])
const teachers = ref([])

const query = reactive({ keyword: '', semester: '', status: '', page: 1, size: 10 })

const dialogVisible = ref(false)
const formRef = ref()
const emptyForm = () => ({
  id: null,
  courseCode: '',
  name: '',
  credit: 3,
  hours: 48,
  courseType: '必修',
  dept: '',
  semester: '2026-2027-1',
  teacherId: null,
  capacity: 50,
  status: 'OPEN',
  intro: ''
})
const form = reactive(emptyForm())

const rules = {
  courseCode: [{ required: true, message: '请输入课程代码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  credit: [{ required: true, message: '请输入学分', trigger: 'blur' }],
  hours: [{ required: true, message: '请输入学时', trigger: 'blur' }],
  courseType: [{ required: true, message: '请选择课程类型', trigger: 'change' }],
  dept: [{ required: true, message: '请输入开课院系', trigger: 'blur' }],
  semester: [{ required: true, message: '请输入学期', trigger: 'blur' }],
  teacherId: [{ required: true, message: '请选择授课教师', trigger: 'change' }],
  capacity: [{ required: true, message: '请输入容量', trigger: 'blur' }]
}

function percent(row) {
  if (!row.capacity) return 0
  return Math.round((row.enrolledCount / row.capacity) * 100)
}

async function load(page) {
  if (page) query.page = page
  loading.value = true
  try {
    const data = await pageCourses(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.semester = ''
  query.status = ''
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
      await updateCourse(form.id, form)
      ElMessage.success('修改成功')
    } else {
      await createCourse(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    load()
  } catch {
    // 错误已由拦截器提示
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  const next = row.status === 'OPEN' ? 'CLOSED' : 'OPEN'
  await updateCourseStatus(row.id, next)
  ElMessage.success(next === 'OPEN' ? '已开放选课' : '已关闭选课')
  load()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(
    `确定删除课程「${row.name}」吗？若已有学生选课将被拒绝。`,
    '删除确认',
    { type: 'warning' }
  )
  await deleteCourse(row.id)
  ElMessage.success('删除成功')
  load()
}

function goSchedule(row) {
  router.push({ path: '/admin/schedules', query: { courseId: row.id } })
}

onMounted(async () => {
  semesters.value = await getSemesters()
  teachers.value = await getAllTeachers()
  load()
})
</script>

<style scoped>
.search-bar {
  margin-bottom: 8px;
}
</style>
