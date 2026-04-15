<template>
  <div class="page-container" v-loading="loading">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h1>仪表盘</h1>
        <p>全局运维状态概览，快速了解系统运行情况。</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-info">
          <div class="stat-label">项目总数</div>
          <div class="stat-value">{{ stats.projectCount || 0 }}</div>
        </div>
        <div class="stat-icon blue">📁</div>
      </div>
      <div class="stat-card">
        <div class="stat-info">
          <div class="stat-label">服务器总数</div>
          <div class="stat-value">{{ stats.serverCount || 0 }}</div>
        </div>
        <div class="stat-icon green">🖥️</div>
      </div>
      <div class="stat-card">
        <div class="stat-info">
          <div class="stat-label">服务实例总数</div>
          <div class="stat-value">{{ stats.instanceCount || 0 }}</div>
          <div class="stat-change up">运行中: {{ stats.runningCount || 0 }}</div>
        </div>
        <div class="stat-icon orange">⚙️</div>
      </div>
      <div class="stat-card">
        <div class="stat-info">
          <div class="stat-label">今日操作</div>
          <div class="stat-value">{{ stats.todayOps || 0 }}</div>
        </div>
        <div class="stat-icon red">📋</div>
      </div>
    </div>

    <!-- 环境分布 + 服务健康 -->
    <div class="dashboard-grid">
      <!-- 环境分布 -->
      <div class="card">
        <div class="card-header">
          <h3>环境分布</h3>
        </div>
        <div class="card-body">
          <div class="env-distribution">
            <div class="env-item dev">
              <div class="env-count">{{ stats.envDistribution?.dev || 0 }}</div>
              <div class="env-label">开发 (dev)</div>
            </div>
            <div class="env-item test">
              <div class="env-count">{{ stats.envDistribution?.test || 0 }}</div>
              <div class="env-label">测试 (test)</div>
            </div>
            <div class="env-item staging">
              <div class="env-count">{{ stats.envDistribution?.staging || 0 }}</div>
              <div class="env-label">预发 (staging)</div>
            </div>
            <div class="env-item prod">
              <div class="env-count">{{ stats.envDistribution?.prod || 0 }}</div>
              <div class="env-label">生产 (prod)</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 服务健康状态 -->
      <div class="card">
        <div class="card-header">
          <h3>服务健康状态</h3>
        </div>
        <div class="card-body">
          <div v-if="stats.serviceHealth && stats.serviceHealth.length" class="health-grid">
            <div v-for="svc in stats.serviceHealth" :key="svc.id" class="health-item" :class="svc.status === 1 ? 'healthy' : 'unhealthy'">
              <span class="dot" :class="svc.status === 1 ? 'green' : 'gray'"></span>
              <span>{{ svc.instanceName }}</span>
            </div>
          </div>
          <div v-else class="empty-text">暂无服务</div>
        </div>
      </div>
    </div>

    <!-- 最近操作记录 -->
    <div class="card" style="margin-bottom: 24px">
      <div class="card-header">
        <h3>最近操作记录</h3>
      </div>
      <div class="card-body">
        <el-table v-if="stats.recentOps && stats.recentOps.length" :data="stats.recentOps" size="small" max-height="300">
          <el-table-column prop="operator" label="操作人" width="100" />
          <el-table-column prop="operation" label="操作" width="120" />
          <el-table-column prop="targetName" label="操作对象" show-overflow-tooltip />
          <el-table-column prop="result" label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="row.result === 'success' ? 'success' : row.result === 'failed' ? 'danger' : 'warning'" size="small">
                {{ row.result === 'success' ? '成功' : row.result === 'failed' ? '失败' : '部分成功' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间" width="170" />
        </el-table>
        <div v-else class="empty-text">暂无操作记录</div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="card">
      <div class="card-header">
        <h3>快捷入口</h3>
      </div>
      <div class="card-body">
        <div class="quick-actions">
          <el-button type="primary" @click="$router.push('/services')">
            <el-icon><Upload /></el-icon> 发版部署
          </el-button>
          <el-button type="success" @click="$router.push('/services')">
            <el-icon><Refresh /></el-icon> 服务重启
          </el-button>
          <el-button type="warning" @click="$router.push('/services')">
            <el-icon><View /></el-icon> 查看日志
          </el-button>
          <el-button type="info" @click="$router.push('/servers')">
            <el-icon><Monitor /></el-icon> 服务器管理
          </el-button>
          <el-button @click="$router.push('/projects')">
            <el-icon><Folder /></el-icon> 项目管理
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Upload, Refresh, View, Monitor, Folder } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import api from '../api'

const router = useRouter()
const loading = ref(false)
const stats = ref({})

const fetchStats = async () => {
  loading.value = true
  try {
    const res = await api.get('/dashboard/stats')
    stats.value = res.data
  } catch (e) {
    // Error handled by interceptor
  } finally {
    loading.value = false
  }
}

onMounted(fetchStats)
</script>

<style scoped>
.page-container {
  padding: 24px;
}
.page-header {
  margin-bottom: 24px;
}
.page-header h1 {
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 0 0 4px;
}
.page-header p {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0;
}
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}
.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  border: 1px solid var(--el-border-color-light);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  transition: box-shadow 0.15s;
}
.stat-card:hover {
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}
.stat-info .stat-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}
.stat-info .stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}
.stat-info .stat-change {
  font-size: 12px;
  margin-top: 4px;
}
.stat-change.up {
  color: #10B981;
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}
.stat-icon.blue { background: #EEF2FF; }
.stat-icon.green { background: #ECFDF5; }
.stat-icon.orange { background: #FFFBEB; }
.stat-icon.red { background: #FEF2F2; }

.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 24px;
}
.card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}
.card-header {
  padding: 16px 24px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 0;
}
.card-body {
  padding: 24px;
}

.env-distribution {
  display: flex;
  gap: 16px;
}
.env-item {
  flex: 1;
  text-align: center;
  padding: 16px;
  border-radius: 8px;
}
.env-item .env-count {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 4px;
}
.env-item .env-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.env-item.dev { background: #EFF6FF; }
.env-item.dev .env-count { color: #3B82F6; }
.env-item.test { background: #FFFBEB; }
.env-item.test .env-count { color: #F59E0B; }
.env-item.staging { background: #F5F3FF; }
.env-item.staging .env-count { color: #8B5CF6; }
.env-item.prod { background: #FEF2F2; }
.env-item.prod .env-count { color: #EF4444; }

.health-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.health-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 13px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.dot.green { background: #10B981; }
.dot.gray { background: #D1D5DB; }
.healthy { border: 1px solid #A7F3D0; }
.unhealthy { border: 1px solid #FECACA; }

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.empty-text {
  color: var(--el-text-color-placeholder);
  text-align: center;
  padding: 20px;
}

@media (max-width: 1440px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
  .dashboard-grid { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .stat-cards { grid-template-columns: 1fr; }
  .env-distribution { flex-direction: column; }
  .quick-actions { flex-direction: column; }
}
</style>
