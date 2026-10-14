<template>
  <el-card>
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- ---------------- 可选课程 ---------------- -->
      <el-tab-pane :label="`可选课程（${available.length}）`" name="available">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          title="列表已自动排除「已选过」和「与已选课程上课时间冲突」的课程 —— 过滤逻辑全部在 SQL 中完成。"
          style="margin-bottom: 12px"
        />

        <el-alert
          v-if="conflicts.length"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
        >
          <template #title>
            以下课程因时间冲突不可选：
            <el-tag
              v-for="c in conflicts"
              :key="c.id"
              type="warning"
              size="small"
              style="margin-right: 6px"
            >
              {{ c.name }}（与「{{ c.conflictWith }}」时间冲突）
            </el-tag>
          </template>
        </el-alert>

        <el-table :data="available" v-loading="loading" border stripe>
          <el-table-column prop="courseCode" label="课程代码" width="110" />
          <el-table-column prop="name" label="课程名称" min-width="160" />
          <el-table-column prop="courseType" label="类型" width="80" align="center" />
          <el-table-column prop="credit" label="学分" width="70" align="center" />
          <el-table-column prop="dept" label="开课院系" width="140" />
          <el-table-column label="授课教师" width="130">
            <template #default="{ row }">
              {{ row.teacherName }}
              <el-tag v-if="row.teacherTitle" size="small" type="info">
                {{ row.teacherTitle }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="余量" width="160">
            <template #default="{ row }">
              <el-progress
                :percentage="percent(row)"
                :status="row.remaining === 0 ? 'exception' : 'success'"
                :stroke-width="12"
              />
              <span class="remaining-text">剩余 {{ row.remaining }} / {{ row.capacity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button
                type="primary"
                size="small"
                :disabled="row.remaining === 0"
                @click="handleSelect(row)"
              >
                {{ row.remaining === 0 ? '已满' : '选课' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ---------------- 我的课程 ---------------- -->
      <el-tab-pane :label="`我的课程（${myCourses.length}）`" name="my">
        <div class="summary">
          <el-statistic title="已选门数" :value="myCourses.length" />
          <el-statistic title="已选总学分" :value="totalCredit" :precision="1" />
        </div>

        <el-table :data="myCourses" v-loading="loading" border stripe>
          <el-table-column prop="courseCode" label="课程代码" width="110" />
          <el-table-column prop="courseName" label="课程名称" min-width="160" />
          <el-table-column prop="courseType" label="类型" width="80" align="center" />
          <el-table-column prop="credit" label="学分" width="70" align="center" />
          <el-table-column prop="teacherName" label="授课教师" width="120" />
          <el-table-column label="成绩" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.score !== null && row.score !== undefined" type="success">
                {{ row.score }}
              </el-tag>
              <span v-else class="muted">未录入</span>
            </template>
          </el-table-column>
          <el-table-column prop="selectedAt" label="选课时间" width="170" />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button type="danger" size="small" link @click="handleWithdraw(row)">
                退课
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAvailableCourses,
  getConflictingCourses,
  getMyCourses,
  selectCourse,
  withdrawCourse
} from '@/api/enrollment'

const activeTab = ref('available')
const loading = ref(false)
const available = ref([])
const myCourses = ref([])
const conflicts = ref([])

const totalCredit = computed(() =>
  myCourses.value.reduce((sum, c) => sum + Number(c.credit || 0), 0)
)

function percent(row) {
  if (!row.capacity) return 0
  return Math.round((row.enrolledCount / row.capacity) * 100)
}

async function loadAll() {
  loading.value = true
  try {
    const [avail, mine, conflict] = await Promise.all([
      getAvailableCourses(),
      getMyCourses(),
      getConflictingCourses()
    ])
    available.value = avail
    myCourses.value = mine
    conflicts.value = conflict
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  loadAll()
}

async function handleSelect(row) {
  await ElMessageBox.confirm(
    `确定选择课程「${row.name}」吗？\n学分：${row.credit}  授课教师：${row.teacherName}`,
    '选课确认',
    { type: 'info' }
  )
  await selectCourse(row.id)
  ElMessage.success(`选课成功：${row.name}`)
  loadAll()
}

async function handleWithdraw(row) {
  await ElMessageBox.confirm(`确定退选课程「${row.courseName}」吗？`, '退课确认', {
    type: 'warning'
  })
  await withdrawCourse(row.courseId)
  ElMessage.success(`已退课：${row.courseName}`)
  loadAll()
}

onMounted(loadAll)
</script>

<style scoped>
.summary {
  display: flex;
  gap: 60px;
  padding: 12px 0 20px;
}

.remaining-text {
  font-size: 12px;
  color: var(--c-text-2);
}

.muted {
  color: var(--c-text-3);
  font-size: 12px;
}
</style>
