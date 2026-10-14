<template>
  <div class="login">
    <!-- 左侧品牌区：窄屏自动隐藏 -->
    <aside class="brand">
      <div class="brand__inner">
        <div class="brand__logo">选</div>
        <h1 class="brand__title">高校选课管理系统</h1>
        <p class="brand__sub">Course Selection Management System</p>

        <ul class="brand__points">
          <li v-for="p in highlights" :key="p.title">
            <el-icon class="brand__icon"><component :is="p.icon" /></el-icon>
            <div>
              <div class="brand__point-title">{{ p.title }}</div>
              <div class="brand__point-desc">{{ p.desc }}</div>
            </div>
          </li>
        </ul>
      </div>
      <div class="brand__foot">课程设计原型 · 全栈实现</div>
    </aside>

    <!-- 右侧表单区 -->
    <main class="panel">
      <div class="panel__inner">
        <h2 class="panel__title">登录</h2>
        <p class="panel__sub">请使用学校下发的账号登录</p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          @keyup.enter="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="登录账号"
              :prefix-icon="User"
              autocomplete="username"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="登录密码"
              :prefix-icon="Lock"
              show-password
              autocomplete="current-password"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="panel__submit"
            :loading="loading"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form>

        <p class="panel__tip">
          忘记密码请联系教务处管理员重置
        </p>
      </div>
    </main>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { homePath } from '@/router'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入登录密码', trigger: 'blur' }]
}

/** 左侧品牌区的要点，只讲系统能力，不泄露任何账号信息 */
const highlights = [
  { icon: 'Tickets', title: '选课与退课', desc: '事务化的容量扣减，并发下不会超卖' },
  { icon: 'AlarmClock', title: '时间冲突检测', desc: '选课时自动排除与已选课程冲突的课' },
  { icon: 'DataAnalysis', title: '统计报表', desc: '热门课程排行与专业选课分布' }
]

async function handleLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = await userStore.login(form)
    ElMessage.success(`欢迎回来，${data.realName}`)
    router.push(route.query.redirect || homePath(data.role))
  } catch {
    // 错误提示已由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  display: flex;
  height: 100vh;
  background-color: var(--c-surface);
}

/* ---------------- 左侧品牌区 ---------------- */

.brand {
  position: relative;
  flex: 0 0 46%;
  max-width: 560px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 56px 52px 36px;
  background: linear-gradient(150deg, #22304a 0%, var(--sidebar-bg) 55%, #16202e 100%);
  overflow: hidden;
}

/* 右上角的柔光，避免大块深色显得沉闷 */
.brand::after {
  content: '';
  position: absolute;
  top: -140px;
  right: -140px;
  width: 380px;
  height: 380px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(59, 111, 224, 0.28), transparent 68%);
}

.brand__inner {
  position: relative;
  z-index: 1;
}

.brand__logo {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--brand-500), #6b92e9);
  color: #fff;
  font-size: 21px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 18px rgba(59, 111, 224, 0.35);
}

.brand__title {
  margin: 26px 0 6px;
  color: #fff;
  font-size: 26px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.brand__sub {
  margin: 0;
  color: var(--sidebar-group);
  font-size: 12.5px;
  letter-spacing: 0.6px;
}

.brand__points {
  list-style: none;
  margin: 44px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.brand__points li {
  display: flex;
  gap: 13px;
  align-items: flex-start;
}

.brand__icon {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background-color: rgba(255, 255, 255, 0.08);
  color: #9db7f0;
  font-size: 16px;
}

.brand__point-title {
  color: #e2e8f0;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
}

.brand__point-desc {
  margin-top: 3px;
  color: #8494a8;
  font-size: 12.5px;
  line-height: 1.5;
}

.brand__foot {
  position: relative;
  z-index: 1;
  color: #5c6b7f;
  font-size: 12px;
}

/* ---------------- 右侧表单区 ---------------- */

.panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.panel__inner {
  width: 100%;
  max-width: 340px;
}

.panel__title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 600;
  color: var(--c-text);
}

.panel__sub {
  margin: 0 0 30px;
  font-size: 13px;
  color: var(--c-text-3);
}

.panel__submit {
  width: 100%;
  margin-top: 4px;
  letter-spacing: 4px;
}

.panel__tip {
  margin: 22px 0 0;
  text-align: center;
  font-size: 12px;
  color: var(--c-text-3);
}

/* ---------------- 响应式 ---------------- */

@media (max-width: 900px) {
  .brand {
    display: none;
  }
}
</style>
