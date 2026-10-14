<template>
  <el-card>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="一门课程可以有多段上课时间（如理论课 + 实验课）。保存时会校验同一教师在同一星期的节次冲突。"
      style="margin-bottom: 16px"
    />

    <el-form :inline="true">
      <el-form-item label="选择课程">
        <el-select
          v-model="courseId"
          filterable
          placeholder="请选择课程"
          style="width: 360px"
          @change="loadSchedules"
        >
          <el-option
            v-for="c in courses"
            :key="c.id"
            :label="`${c.courseCode} ${c.name}（${c.teacherName}）`"
            :value="c.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="success" :disabled="!courseId" @click="openDialog()">新增上课时间</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="schedules" v-loading="loading" border stripe>
      <el-table-column prop="weekdayName" label="星期" width="90" align="center" />
      <el-table-column label="节次" width="120" align="center">
        <template #default="{ row }">第 {{ row.startSection }}-{{ row.endSection }} 节</template>
      </el-table-column>
      <el-table-column label="周次" width="140" align="center">
        <template #default="{ row }">第 {{ row.startWeek }}-{{ row.endWeek }} 周</template>
      </el-table-column>
      <el-table-column prop="classroom" label="教室" min-width="150" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span v-if="!courseId">请先选择课程</span>
        <span v-else>该课程暂无上课时间，点击「新增上课时间」添加</span>
      </template>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑上课时间' : '新增上课时间'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="星期" prop="weekday">
          <el-select v-model="form.weekday" style="width: 100%">
            <el-option v-for="d in 7" :key="d" :label="weekdayLabel(d)" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="节次" prop="startSection">
          <el-col :span="11">
            <el-input-number v-model="form.startSection" :min="1" :max="12" style="width: 100%" />
          </el-col>
          <el-col :span="2" align="center">至</el-col>
          <el-col :span="11">
            <el-input-number v-model="form.endSection" :min="1" :max="12" style="width: 100%" />
          </el-col>
        </el-form-item>
        <el-form-item label="周次" prop="startWeek">
          <el-col :span="11">
            <el-input-number v-model="form.startWeek" :min="1" :max="30" style="width: 100%" />
          </el-col>
          <el-col :span="2" align="center">至</el-col>
          <el-col :span="11">
            <el-input-number v-model="form.endWeek" :min="1" :max="30" style="width: 100%" />
          </el-col>
        </el-form-item>
        <el-form-item label="教室">
          <el-input v-model="form.classroom" />
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
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageCourses } from '@/api/course'
import { createSchedule, deleteSchedule, listSchedules, updateSchedule } from '@/api/schedule'

const route = useRoute()

const loading = ref(false)
const saving = ref(false)
const courses = ref([])
const schedules = ref([])
const courseId = ref(null)

const dialogVisible = ref(false)
const formRef = ref()
const emptyForm = () => ({
  id: null,
  courseId: null,
  weekday: 1,
  startSection: 1,
  endSection: 2,
  startWeek: 1,
  endWeek: 16,
  classroom: ''
})
const form = reactive(emptyForm())

const rules = {
  weekday: [{ required: true, message: '请选择星期', trigger: 'change' }],
  startSection: [{ required: true, message: '请填写节次', trigger: 'blur' }],
  startWeek: [{ required: true, message: '请填写周次', trigger: 'blur' }]
}

const weekdayLabel = (d) =>
  ['周一', '周二', '周三', '周四', '周五', '周六', '周日'][d - 1]

async function loadSchedules() {
  if (!courseId.value) return
  loading.value = true
  try {
    schedules.value = await listSchedules(courseId.value)
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  Object.assign(form, row ? { ...row } : { ...emptyForm(), courseId: courseId.value })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function handleSave() {
  await formRef.value.validate()
  if (form.startSection > form.endSection) {
    ElMessage.warning('开始节次不能大于结束节次')
    return
  }
  if (form.startWeek > form.endWeek) {
    ElMessage.warning('起始周不能大于结束周')
    return
  }

  saving.value = true
  try {
    form.courseId = courseId.value
    if (form.id) {
      await updateSchedule(form.id, form)
      ElMessage.success('修改成功')
    } else {
      await createSchedule(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadSchedules()
  } catch {
    // 冲突等错误已由拦截器提示
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确定删除这条上课时间吗？', '删除确认', { type: 'warning' })
  await deleteSchedule(row.id)
  ElMessage.success('删除成功')
  loadSchedules()
}

onMounted(async () => {
  const data = await pageCourses({ page: 1, size: 200 })
  courses.value = data.list

  // 支持从课程管理页跳转过来时直接定位课程
  const fromQuery = Number(route.query.courseId)
  if (fromQuery && courses.value.some((c) => c.id === fromQuery)) {
    courseId.value = fromQuery
    loadSchedules()
  }
})
</script>
