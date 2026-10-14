import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

/** 各角色的默认首页 */
export function homePath(role) {
  switch (role) {
    case 'ADMIN':
      return '/admin/courses'
    case 'TEACHER':
      return '/teacher/courses'
    case 'STUDENT':
      return '/student/select'
    default:
      return '/login'
  }
}

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/login',
    children: [
      // ---------- 管理员 ----------
      {
        path: 'admin/courses',
        component: () => import('@/views/admin/CourseManage.vue'),
        meta: { title: '课程管理', roles: ['ADMIN'] }
      },
      {
        path: 'admin/schedules',
        component: () => import('@/views/admin/ScheduleManage.vue'),
        meta: { title: '排课管理', roles: ['ADMIN'] }
      },
      {
        path: 'admin/students',
        component: () => import('@/views/admin/StudentManage.vue'),
        meta: { title: '学生管理', roles: ['ADMIN'] }
      },
      {
        path: 'admin/teachers',
        component: () => import('@/views/admin/TeacherManage.vue'),
        meta: { title: '教师管理', roles: ['ADMIN'] }
      },
      {
        path: 'admin/users',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '账号管理', roles: ['ADMIN'] }
      },
      {
        path: 'admin/enrollments',
        component: () => import('@/views/admin/EnrollmentManage.vue'),
        meta: { title: '选课记录', roles: ['ADMIN'] }
      },
      {
        path: 'admin/stats',
        component: () => import('@/views/admin/StatsReport.vue'),
        meta: { title: '统计报表', roles: ['ADMIN'] }
      },
      // ---------- 学生 ----------
      {
        path: 'student/select',
        component: () => import('@/views/student/CourseSelect.vue'),
        meta: { title: '选课中心', roles: ['STUDENT'] }
      },
      {
        path: 'student/timetable',
        component: () => import('@/views/student/MyTimetable.vue'),
        meta: { title: '我的课表', roles: ['STUDENT'] }
      },
      // ---------- 教师 ----------
      {
        path: 'teacher/courses',
        component: () => import('@/views/teacher/MyCourse.vue'),
        meta: { title: '我的课程', roles: ['TEACHER'] }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const userStore = useUserStore()

  if (to.meta.public) {
    // 已登录用户访问登录页，直接送回各自首页
    return userStore.isLogin ? homePath(userStore.role) : true
  }

  if (!userStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (to.meta.roles && !to.meta.roles.includes(userStore.role)) {
    ElMessage.error('无权访问该页面')
    return homePath(userStore.role)
  }

  return true
})

export default router
