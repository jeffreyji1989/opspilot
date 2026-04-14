import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '仪表盘' } },
      { path: 'projects', name: 'Projects', component: () => import('../views/Projects.vue'), meta: { title: '项目管理' } },
      { path: 'servers', name: 'Servers', component: () => import('../views/Servers.vue'), meta: { title: '服务器管理' } },
      { path: 'git-credentials', name: 'GitCredentials', component: () => import('../views/GitCredentials.vue'), meta: { title: 'Git认证管理' } },
      { path: 'services', name: 'Services', component: () => import('../views/Services.vue'), meta: { title: '服务管理' } },
      { path: 'services/:id', name: 'ServiceDetail', component: () => import('../views/ServiceDetail.vue'), meta: { title: '服务详情' } },
      { path: 'operation-logs', name: 'OperationLogs', component: () => import('../views/OperationLogs.vue'), meta: { title: '操作日志' } },
      { path: 'schedule-tasks', name: 'ScheduleTasks', component: () => import('../views/ScheduleTasks.vue'), meta: { title: '定时任务' } },
      { path: 'system-settings', name: 'SystemSettings', component: () => import('../views/SystemSettings.vue'), meta: { title: '系统设置' } },
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (to.path !== '/login' && !userStore.token) {
    next('/login')
  } else {
    next()
  }
})

export default router
