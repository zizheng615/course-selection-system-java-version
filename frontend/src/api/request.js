import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截：自动注入 JWT
request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

// 响应拦截：拆掉统一响应体的外壳，只把 data 交给业务代码
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 0) {
      return res.data
    }

    // 1001 未登录 / 1002 Token 失效 —— 清理本地状态并回登录页。
    //
    // 注意这里判断的是 pathname 而不是 hash：本项目路由用的是 history 模式，
    // window.location.hash 恒为空字符串，用它判断会让条件恒成立，
    // 结果在登录页输错密码时也会触发整页跳转 —— 提示还没渲染就被刷新掉，
    // 用户看到的就是"点了登录没有任何反应"。1001 也包含"账号或密码错误"，
    // 因此在登录页必须原地提示，不能跳转。
    if (res.code === 1001 || res.code === 1002) {
      const userStore = useUserStore()
      userStore.logout()
      if (!window.location.pathname.startsWith('/login')) {
        window.location.replace('/login')
      }
    }

    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    const message =
      error.response?.data?.message || error.message || '网络异常，请检查后端服务是否已启动'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default request
