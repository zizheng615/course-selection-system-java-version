<template>
  <div v-loading="loading">
    <el-card class="block">
      <template #header>
        <div class="card-header">
          <span>本学期课表</span>
          <el-tag type="info" size="small">
            共 {{ timetable.length }} 个上课时段 · {{ myCourses.length }} 门课程 ·
            总学分 {{ totalCredit }}
          </el-tag>
        </div>
      </template>

      <table class="timetable">
        <thead>
          <tr>
            <th class="corner">节次</th>
            <th v-for="(d, i) in weekdays" :key="i">{{ d }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(slot, si) in slots" :key="si">
            <td class="section-cell">{{ slot.label }}</td>
            <td v-for="(d, wi) in weekdays" :key="wi" class="course-cell">
              <div
                v-for="item in cellItems(wi + 1, slot.start)"
                :key="item.courseId + '-' + item.startSection"
                class="course-item"
                :class="typeClass(item.courseType)"
              >
                <div class="course-name">{{ item.courseName }}</div>
                <div class="course-meta">{{ item.teacherName }}</div>
                <div class="course-meta">{{ item.classroom }}</div>
                <div class="course-meta">
                  第 {{ item.startWeek }}-{{ item.endWeek }} 周
                </div>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </el-card>

    <el-card>
      <template #header>
        <span>已选课程明细</span>
      </template>
      <el-table :data="myCourses" border stripe size="small">
        <el-table-column prop="courseCode" label="课程代码" width="110" />
        <el-table-column prop="courseName" label="课程名称" min-width="180" />
        <el-table-column prop="courseType" label="类型" width="80" align="center" />
        <el-table-column prop="credit" label="学分" width="80" align="center" />
        <el-table-column prop="teacherName" label="授课教师" width="120" />
        <el-table-column label="成绩" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.score !== null && row.score !== undefined" type="success">
              {{ row.score }}
            </el-tag>
            <span v-else class="muted">未录入</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getMyCourses, getMyTimetable } from '@/api/enrollment'

const loading = ref(false)
const timetable = ref([])
const myCourses = ref([])

const weekdays = ['周一', '周二', '周三', '周四', '周五']

// 每两节作为一个展示单元
const slots = [
  { label: '第 1-2 节', start: 1 },
  { label: '第 3-4 节', start: 3 },
  { label: '第 5-6 节', start: 5 },
  { label: '第 7-8 节', start: 7 },
  { label: '第 9-10 节', start: 9 },
  { label: '第 11-12 节', start: 11 }
]

const totalCredit = computed(() =>
  myCourses.value.reduce((sum, c) => sum + Number(c.credit || 0), 0)
)

/** 取出落在某个 (星期, 起始节次) 单元里的所有课程 */
function cellItems(weekday, slotStart) {
  return timetable.value.filter(
    (t) => t.weekday === weekday && t.startSection >= slotStart && t.startSection < slotStart + 2
  )
}

function typeClass(courseType) {
  return {
    必修: 'type-required',
    选修: 'type-elective',
    公选: 'type-public'
  }[courseType] || 'type-public'
}

onMounted(async () => {
  loading.value = true
  try {
    const [tt, mine] = await Promise.all([getMyTimetable(), getMyCourses()])
    timetable.value = tt
    myCourses.value = mine
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.block {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.timetable {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.timetable th {
  background-color: var(--c-fill);
  color: var(--c-text-2);
  font-weight: 600;
  padding: 10px 4px;
  border: 1px solid var(--c-border-light);
  font-size: 13px;
}

.corner {
  width: 100px;
}

.section-cell {
  background-color: var(--c-fill);
  text-align: center;
  color: var(--c-text-2);
  font-size: 13px;
  border: 1px solid var(--c-border-light);
}

.course-cell {
  border: 1px solid var(--c-border-light);
  vertical-align: top;
  height: 92px;
  padding: 4px;
}

.course-item {
  border-radius: 4px;
  padding: 6px;
  margin-bottom: 4px;
  font-size: 12px;
  line-height: 1.5;
}

.course-name {
  font-weight: 600;
  margin-bottom: 2px;
}

.course-meta {
  color: var(--c-text-2);
}

/* 三类课程用主色/成功色/警告色的浅色变体区分，取自主题令牌而非硬编码色值 */
.type-required {
  background-color: var(--el-color-primary-light-9);
  border-left: 3px solid var(--brand-500);
}

.type-elective {
  background-color: var(--el-color-success-light-9);
  border-left: 3px solid var(--c-success);
}

.type-public {
  background-color: var(--el-color-warning-light-9);
  border-left: 3px solid var(--c-warning);
}

.muted {
  color: var(--c-text-3);
  font-size: 12px;
}
</style>
