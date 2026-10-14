import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as loginApi } from '@/api/auth'

const TOKEN_KEY = 'cs_token'
const USER_KEY = 'cs_user'

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const userInfo = ref(readUser())

  const isLogin = computed(() => !!token.value)
  const role = computed(() => userInfo.value?.role || '')
  const realName = computed(() => userInfo.value?.realName || '')

  async function login(form) {
    const data = await loginApi(form)
    token.value = data.token
    userInfo.value = data
    localStorage.setItem(TOKEN_KEY, data.token)
    localStorage.setItem(USER_KEY, JSON.stringify(data))
    return data
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, userInfo, isLogin, role, realName, login, logout }
})
