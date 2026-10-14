<template>
  <el-card>
    <el-table :data="courses" v-loading="loading" border stripe highlight-current-row>
      <el-table-column prop="courseCode" label="课程代码" width="110" />
      <el-table-column prop="name" label="课程名称" min-width="180" />
      <el-table-column prop="courseType" label="类型" width="80" align="center" />
      <el-table-column prop="credit" label="学分" width="70" align="center" />
      <el-table-column prop="dept" label="开课院系" width="140" />
      <el-table-column label="选课人数" width="120" align="center">
        <template #default="{ row }">{{ row.enrolledCount }} / {{ row.capacity }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'OPEN' ? 'success' : 'info'" size="small">
            {{ row.status === 'OPEN' ? '开放' : '关闭' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showStudents(row)">学生名单</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="`学生名单 - ${current?.name || ''}（共 ${students.length} 人）`"
      width="780px"
    >
      <el-table :data="students" v-loading="studentLoading" border stripe size="small">
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="studentName" label="姓名" width="100" />
        <el-table-column prop="major" label="专业" min-width="180" />
        <el-table-column prop="grade" label="年级" width="90" align="center" />
        <el-table-column label="成绩" width="230">
          <template #default="{ row }">
            <div class="score-cell">
              <el-input-number
                v-model="row.score"
                :min="0"
                :max="100"
                :precision="1"
                size="small"
                controls-position="right"
                style="width: 118px"
              />
              <el-button link type="primary" size="small" @click="saveScore(row)">保存</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyCourses } from '@/api/course'
import { getCourseStudents, recordScore } from '@/api/enrollment'

const loading = ref(false)
const courses = ref([])

const dialogVisible = ref(false)
const studentLoading = ref(false)
const students = ref([])
const current = ref(null)

async function loadCourses() {
  loading.value = true
  try {
    courses.value = await getMyCourses()
  } finally {
    loading.value = false
  }
}

async function showStudents(course) {
  current.value = course
  dialogVisible.value = true
  studentLoading.value = true
  try {
    students.value = await getCourseStudents(course.id)
  } finally {
    studentLoading.value = false
  }
}

async function saveScore(row) {
  if (row.score === null || row.score === undefined) {
    ElMessage.warning('请先填写成绩')
    return
  }
  await recordScore({ enrollmentId: row.enrollmentId, score: row.score })
  ElMessage.success(`已保存 ${row.studentName} 的成绩`)
}

onMounted(loadCourses)
</script>

<style scoped>
.score-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
