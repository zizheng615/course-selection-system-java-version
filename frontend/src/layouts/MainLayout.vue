<template>
  <el-container class="layout">
    <!-- ============ 侧边栏 ============ -->
    <el-aside width="228px" class="aside">
      <div class="brand">
        <div class="brand__mark">选</div>
        <div class="brand__text">
          <div class="brand__name">选课管理系统</div>
          <div class="brand__sub">Course Selection</div>
        </div>
      </div>

      <el-scrollbar class="menu-scroll">
        <el-menu
          :default-active="route.path"
          router
          class="menu"
          background-color="transparent"
          text-color="var(--sidebar-text)"
          active-text-color="var(--sidebar-text-active)"
        >
          <el-menu-item-group v-for="group in menuGroups" :key="group.title">
            <template #title>
              <span class="menu-group">{{ group.title }}</span>
            </template>
            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>
          </el-menu-item-group>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <!-- AI 抽屉打开时，这一块整体左移，把右侧空间让给对话区（不遮挡页面） -->
    <el-container class="layout__main" :style="{ marginRight: pushRight }">
      <!-- ============ 顶栏 ============ -->
      <el-header class="header">
        <div class="header__title">{{ route.meta.title || '' }}</div>

        <el-dropdown trigger="click" @command="onCommand">
          <div class="user">
            <div class="user__avatar">{{ initial }}</div>
            <div class="user__meta">
              <span class="user__name">{{ userStore.realName }}</span>
              <span class="user__role">{{ roleLabel }}</span>
            </div>
            <el-icon class="user__caret"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>
                {{ userStore.userInfo?.username }}
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <!-- ============ 内容区 ============ -->
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <!-- AI 助手：悬浮按钮 + 右侧对话抽屉 -->
    <AiAssistant v-model:open="aiOpen" />
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import AiAssistant from '@/components/AiAssistant.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// ---------------- AI 抽屉与布局联动 ----------------

/** 抽屉宽度，必须与 AiAssistant.vue 里的 DRAWER_WIDTH 保持一致 */
const AI_DRAWER_WIDTH = 400
/** 低于这个视口宽度就不再挤压主界面，改为抽屉浮在内容之上 */
const AI_OVERLAY_BREAKPOINT = 1200

const aiOpen = ref(false)
const isNarrow = ref(false)

function updateViewport() {
  isNarrow.value = window.innerWidth < AI_OVERLAY_BREAKPOINT
}

/**
 * 宽屏：主界面右移 400px 让位，两边都能完整操作；
 * 窄屏：不做位移，抽屉直接覆盖 —— 否则主界面会被压到没法看。
 */
const pushRight = computed(() =>
  aiOpen.value && !isNarrow.value ? `${AI_DRAWER_WIDTH}px` : '0'
)

onMounted(() => {
  updateViewport()
  window.addEventListener('resize', updateViewport)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateViewport)
})

/**
 * 菜单按角色分组。分组标题让功能层级一眼可见，
 * 比平铺一长串菜单更容易定位（尤其是管理员有 7 个页面时）。
 */
const MENU_GROUPS = [
  {
    title: '教学资源',
    roles: ['ADMIN'],
    items: [
      { path: '/admin/courses', title: '课程管理', icon: 'Notebook' },
      { path: '/admin/schedules', title: '排课管理', icon: 'Clock' }
    ]
  },
  {
    title: '选课管理',
    roles: ['ADMIN'],
    items: [
      { path: '/admin/enrollments', title: '选课记录', icon: 'Tickets' },
      { path: '/admin/stats', title: '统计报表', icon: 'DataAnalysis' }
    ]
  },
  {
    title: '人员管理',
    roles: ['ADMIN'],
    items: [
      { path: '/admin/students', title: '学生管理', icon: 'User' },
      { path: '/admin/teachers', title: '教师管理', icon: 'Avatar' },
      { path: '/admin/users', title: '账号管理', icon: 'Key' }
    ]
  },
  {
    title: '选课',
    roles: ['STUDENT'],
    items: [
      { path: '/student/select', title: '选课中心', icon: 'ShoppingCart' },
      { path: '/student/timetable', title: '我的课表', icon: 'Calendar' }
    ]
  },
  {
    title: '教学',
    roles: ['TEACHER'],
    items: [{ path: '/teacher/courses', title: '我的课程', icon: 'Notebook' }]
  }
]

const menuGroups = computed(() =>
  MENU_GROUPS.filter((g) => g.roles.includes(userStore.role))
)

const roleLabel = computed(
  () => ({ ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' })[userStore.role] || '未知'
)

const initial = computed(() => (userStore.realName || '?').charAt(0))

async function onCommand(command) {
  if (command !== 'logout') return
  await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    type: 'warning',
    confirmButtonText: '退出',
    cancelButtonText: '取消'
  })
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  height: 100vh;
  overflow: hidden;
}

/* 让位给 AI 抽屉时的位移动画，时长与抽屉的滑入保持一致 */
.layout__main {
  transition: margin-right 0.26s cubic-bezier(0.4, 0, 0.2, 1);
}

/* ---------------- 侧边栏 ---------------- */

.aside {
  display: flex;
  flex-direction: column;
  background-color: var(--sidebar-bg);
  overflow: hidden;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 64px;
  padding: 0 18px;
  flex-shrink: 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.brand__mark {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--brand-500), #6b92e9);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand__name {
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
}

.brand__sub {
  color: var(--sidebar-group);
  font-size: 11px;
  line-height: 1.4;
}

.menu-scroll {
  flex: 1;
  min-height: 0;
}

.menu {
  border-right: none;
  padding-bottom: 12px;
}

.menu-group {
  display: block;
  padding: 16px 18px 6px;
  color: var(--sidebar-group);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.6px;
}

.aside :deep(.el-menu-item) {
  height: 42px;
  line-height: 42px;
  margin: 2px 10px;
  padding-left: 14px !important;
  border-radius: var(--radius-ctl);
  font-size: 13.5px;
}

.aside :deep(.el-menu-item:hover) {
  background-color: var(--sidebar-bg-hover) !important;
}

.aside :deep(.el-menu-item.is-active) {
  background-color: var(--brand-500) !important;
  color: #fff !important;
  font-weight: 500;
}

.aside :deep(.el-menu-item .el-icon) {
  margin-right: 10px;
}

/* ---------------- 顶栏 ---------------- */

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 24px;
  background-color: var(--c-surface);
  border-bottom: 1px solid var(--c-border-light);
}

.header__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--c-text);
}

.user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 10px 5px 5px;
  border-radius: 20px;
  cursor: pointer;
  outline: none;
  transition: background-color 0.2s;
}

.user:hover {
  background-color: var(--c-fill);
}

.user__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand-500), #6b92e9);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user__meta {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.user__name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--c-text);
}

.user__role {
  font-size: 11.5px;
  color: var(--c-text-3);
}

.user__caret {
  color: var(--c-text-3);
  font-size: 12px;
}

/* ---------------- 内容区 ---------------- */

.main {
  padding: 20px;
  background-color: var(--c-page-bg);
  overflow-y: auto;
}
</style>
