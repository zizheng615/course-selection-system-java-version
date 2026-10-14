<template>
  <el-card>
    <!-- 工具栏 -->
    <div class="toolbar">
      <el-form :inline="true" class="toolbar__filters">
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="学生姓名 / 学号 / 课程"
            clearable
            style="width: 200px"
            @keyup.enter="load(1)"
          />
        </el-form-item>
        <el-form-item label="课程">
          <el-select
            v-model="query.courseId"
            clearable
            filterable
            placeholder="全部"
            style="width: 200px"
          >
            <el-option
              v-for="c in courses"
              :key="c.id"
              :label="`${c.courseCode} ${c.name}`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
            <el-option label="已选" value="SELECTED" />
            <el-option label="已退课" value="WITHDRAWN" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load(1)">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="toolbar__actions">
        <el-button type="success" @click="openEnrollDialog">加选课程</el-button>
      </div>
    </div>

    <!-- 列表 -->
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column label="学生" min-width="150">
        <template #default="{ row }">
          <span class="cell-main">{{ row.studentName }}</span>
          <div class="cell-sub">{{ row.studentNo }} · {{ row.major }}</div>
        </template>
      </el-table-column>
      <el-table-column label="课程" min-width="200">
        <template #default="{ row }">
          <span class="cell-main">{{ row.courseName }}</span>
          <div class="cell-sub">{{ row.courseCode }} · {{ row.teacherName }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="semester" label="学期" width="130" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'SELECTED' ? 'success' : 'info'" size="small" effect="light">
            {{ row.status === 'SELECTED' ? '已选' : '已退课' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="成绩" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.score !== null && row.score !== undefined" type="primary" size="small">
            {{ row.score }}
          </el-tag>
          <span v-else class="text-muted">未录入</span>
        </template>
      </el-table-column>
      <el-table-column prop="selectedAt" label="选课时间" width="170" />
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openScoreDialog(row)">成绩</el-button>
          <el-button
            link
            type="warning"
            :disabled="row.status !== 'SELECTED'"
            @click="handleWithdraw(row)"
          >
            退课
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

    <!-- 加选课程 -->
    <el-dialog v-model="enrollVisible" title="代学生加选课程" width="520px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="与学生自助选课走完全相同的校验：容量已满、时间冲突、重复选课都会被拒绝。"
        style="margin-bottom: 16px"
      />
      <el-form ref="enrollFormRef" :model="enrollForm" :rules="enrollRules" label-width="80px">
        <el-form-item label="学生" prop="studentId">
          <el-select
            v-model="enrollForm.studentId"
            filterable
            placeholder="按姓名或学号搜索"
            style="width: 100%"
          >
            <el-option
              v-for="s in students"
              :key="s.id"
              :label="`${s.name}（${s.studentNo} · ${s.major}）`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="课程" prop="courseId">
          <el-select
            v-model="enrollForm.courseId"
            filterable
            placeholder="选择课程"
            style="width: 100%"
          >
            <el-option
              v-for="c in courses"
              :key="c.id"
              :label="`${c.courseCode} ${c.name}（余 ${c.remaining}/${c.capacity}）`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="enrollVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleEnroll">确定加选</el-button>
      </template>
    </el-dialog>

    <!-- 录入成绩 -->
    <el-dialog v-model="scoreVisible" title="录入成绩" width="400px">
      <el-form label-width="80px">
        <el-form-item label="学生">
          <span class="readonly-text">{{ scoreRow?.studentName }}（{{ scoreRow?.studentNo }}）</span>
        </el-form-item>
        <el-form-item label="课程">
          <span class="readonly-text">{{ scoreRow?.courseName }}</span>
        </el-form-item>
        <el-form-item label="成绩">
          <el-input-number v-model="scoreValue" :min="0" :max="100" :precision="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scoreVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveScore">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageCourses } from '@/api/course'
import { pageStudents } from '@/api/student'
import {
  adminDeleteEnrollment,
  adminEnroll,
  adminWithdraw,
  pageEnrollments,
  recordScore
} from '@/api/enrollment'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const courses = ref([])
const students = ref([])

const query = reactive({ keyword: '', courseId: null, status: '', page: 1, size: 10 })

const enrollVisible = ref(false)
const enrollFormRef = ref()
const enrollForm = reactive({ studentId: null, courseId: null })
const enrollRules = {
  studentId: [{ required: true, message: '请选择学生', trigger: 'change' }],
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }]
}

const scoreVisible = ref(false)
const scoreRow = ref(null)
const scoreValue = ref(null)

async function load(page) {
  if (page) query.page = page
  loading.value = true
  try {
    const data = await pageEnrollments(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.courseId = null
  query.status = ''
  load(1)
}

function openEnrollDialog() {
  Object.assign(enrollForm, { studentId: null, courseId: null })
  enrollVisible.value = true
  enrollFormRef.value?.clearValidate()
}

async function handleEnroll() {
  await enrollFormRef.value.validate()
  saving.value = true
  try {
    await adminEnroll(enrollForm)
    ElMessage.success('加选成功')
    enrollVisible.value = false
    refreshDropdowns()
    load()
  } catch {
    // 容量已满 / 时间冲突等提示已由拦截器给出
  } finally {
    saving.value = false
  }
}

function openScoreDialog(row) {
  scoreRow.value = row
  scoreValue.value = row.score ?? null
  scoreVisible.value = true
}

async function handleSaveScore() {
  if (scoreValue.value === null || scoreValue.value === undefined) {
    ElMessage.warning('请填写成绩')
    return
  }
  saving.value = true
  try {
    await recordScore({ enrollmentId: scoreRow.value.id, score: scoreValue.value })
    ElMessage.success('成绩已保存')
    scoreVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function handleWithdraw(row) {
  await ElMessageBox.confirm(
    `确定让「${row.studentName}」退选「${row.courseName}」吗？\n退课会保留记录并归还课程容量。`,
    '退课确认',
    { type: 'warning' }
  )
  await adminWithdraw(row.id)
  ElMessage.success('已退课')
  refreshDropdowns()
  load()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(
    `确定永久删除「${row.studentName}」的「${row.courseName}」选课记录吗？\n`
      + '该操作不可恢复；若记录为已选状态，会同步归还课程容量。',
    '删除确认',
    { type: 'warning', confirmButtonText: '永久删除' }
  )
  await adminDeleteEnrollment(row.id)
  ElMessage.success('已删除')
  refreshDropdowns()
  load()
}

/** 选课记录变动后余量会变，重新拉一次下拉数据保证显示的余量准确 */
async function refreshDropdowns() {
  const [courseData, studentData] = await Promise.all([
    pageCourses({ page: 1, size: 200 }),
    pageStudents({ page: 1, size: 200 })
  ])
  courses.value = courseData.list
  students.value = studentData.list
}

onMounted(() => {
  refreshDropdowns()
  load()
})
</script>

<style scoped>
.readonly-text {
  color: var(--c-text-2);
}
</style>
