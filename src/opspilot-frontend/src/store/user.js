import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(parseInt(localStorage.getItem('userId') || '0'))
  const username = ref(localStorage.getItem('username') || '')
  const displayName = ref(localStorage.getItem('displayName') || '')
  const role = ref(parseInt(localStorage.getItem('role') || '0'))

  function setUserInfo(info) {
    token.value = info.token
    userId.value = info.userId
    username.value = info.username
    displayName.value = info.displayName
    role.value = info.role
    localStorage.setItem('token', info.token)
    localStorage.setItem('userId', info.userId)
    localStorage.setItem('username', info.username)
    localStorage.setItem('displayName', info.displayName)
    localStorage.setItem('role', info.role)
  }

  function logout() {
    token.value = ''
    userId.value = 0
    username.value = ''
    displayName.value = ''
    role.value = 0
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    localStorage.removeItem('displayName')
    localStorage.removeItem('role')
  }

  return { token, userId, username, displayName, role, setUserInfo, logout }
})
