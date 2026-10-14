<template>
  <div v-loading="loading">
    <!-- 热门课程 -->
    <el-card class="block">
      <template #header>
        <div class="card-header">
          <span>热门课程 Top 10（按选课人数）</span>
          <el-tag type="info" size="small">
            聚合查询：GROUP BY + COUNT + 满座率计算
          </el-tag>
        </div>
      </template>
      <div ref="chartRef" class="chart"></div>
      <el-table :data="popular" border stripe size="small">
        <el-table-column type="index" label="排名" width="70" align="center" />
        <el-table-column prop="courseCode" label="课程代码" width="110" />
        <el-table-column prop="courseName" label="课程名称" min-width="180" />
        <el-table-column prop="teacherName" label="授课教师" width="120" />
        <el-table-column prop="capacity" label="容量" width="80" align="center" />
        <el-table-column prop="selectedCount" label="已选" width="80" align="center" />
        <el-table-column label="满座率" width="180">
          <template #default="{ row }">
            <el-progress
              :percentage="Number(row.fillRate)"
              :status="Number(row.fillRate) >= 100 ? 'exception' : undefined"
            />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 专业分布 -->
    <el-card class="block">
      <template #header>
        <div class="card-header">
          <span>各专业选课分布</span>
          <el-tag type="info" size="small">多表 LEFT JOIN + 多维聚合</el-tag>
        </div>
      </template>
      <el-table :data="majors" border stripe size="small">
        <el-table-column prop="major" label="专业" min-width="200" />
        <el-table-column prop="studentCount" label="学生人数" width="120" align="center" />
        <el-table-column prop="enrollmentCount" label="选课总人次" width="130" align="center" />
        <el-table-column prop="avgCoursesPerStudent" label="人均选课门数" width="140" align="center" />
      </el-table>
    </el-card>

    <!-- 教师工作量 -->
    <el-card class="block">
      <template #header>
        <div class="card-header">
          <span>教师工作量统计</span>
          <el-tag type="info" size="small">LEFT JOIN 保证无课教师也被统计</el-tag>
        </div>
      </template>
      <el-table :data="workload" border stripe size="small">
        <el-table-column prop="teacherName" label="教师" width="120" />
        <el-table-column prop="title" label="职称" width="110" />
        <el-table-column prop="dept" label="院系" min-width="150" />
        <el-table-column prop="courseCount" label="授课门数" width="110" align="center" />
        <el-table-column prop="totalStudents" label="学生总数" width="110" align="center" />
        <el-table-column prop="totalCredit" label="总学分" width="110" align="center" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getMajorDistribution, getPopularCourses, getTeacherWorkload } from '@/api/stats'

const loading = ref(false)
const popular = ref([])
const majors = ref([])
const workload = ref([])
const chartRef = ref()

async function renderChart() {
  if (!chartRef.value || popular.value.length === 0) return
  await nextTick()

  const chart = echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 60, right: 30, top: 30, bottom: 70 },
    xAxis: {
      type: 'category',
      data: popular.value.map((c) => c.courseName),
      axisLabel: { rotate: 30, interval: 0 }
    },
    yAxis: { type: 'value', name: '选课人数' },
    series: [
      {
        name: '选课人数',
        type: 'bar',
        data: popular.value.map((c) => c.selectedCount),
        // 与主题主色保持一致，避免图表与界面两张皮
        itemStyle: { color: '#3b6fe0' },
        barMaxWidth: 36,
        label: { show: true, position: 'top' }
      }
    ]
  })

  window.addEventListener('resize', () => chart.resize())
}

onMounted(async () => {
  loading.value = true
  try {
    const [p, m, w] = await Promise.all([
      getPopularCourses({ limit: 10 }),
      getMajorDistribution(),
      getTeacherWorkload()
    ])
    popular.value = p
    majors.value = m
    workload.value = w
    renderChart()
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

.chart {
  height: 280px;
  margin-bottom: 16px;
}
</style>
