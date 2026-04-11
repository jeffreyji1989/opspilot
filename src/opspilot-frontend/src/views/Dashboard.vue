<template>
  <div>
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>项目总数</template>
          <div class="stat-value">{{ stats.projectCount || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>服务器总数</template>
          <div class="stat-value">{{ stats.serverCount || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>运行中服务</template>
          <div class="stat-value success">{{ stats.runningCount || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>今日操作</template>
          <div class="stat-value">{{ stats.todayOps || 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="12">
        <el-card>
          <template #header>环境分布</template>
          <div v-for="(count, env) in stats.envDistribution" :key="env" class="env-item">
            <span class="env-label">{{ env }}</span>
            <el-progress :percentage="Math.round(count / totalServers * 100)" :stroke-width="14" />
            <span class="env-count">{{ count }} 台</span>
          </div>
          <div v-if="!stats.envDistribution" class="empty">暂无数据</div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>最近操作</template>
          <el-table :data="stats.recentOps || []" size="small" max-height="300">
            <el-table-column prop="operation" label="操作" width="100" />
            <el-table-column prop="targetName" label="对象" show-overflow-tooltip />
            <el-table-column prop="createTime" label="时间" width="160" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row style="margin-top: 20px;">
      <el-col :span="24">
        <el-card>
          <template #header>服务健康状态</template>
          <div v-if="stats.serviceHealth && stats.serviceHealth.length" class="health-grid">
            <div v-for="svc in stats.serviceHealth" :key="svc.id" class="health-item" :class="svc.processStatus === 1 ? 'healthy' : 'unhealthy'">
              <span class="dot" :class="svc.processStatus === 1 ? 'green' : 'gray'"></span>
              <span>{{ svc.instanceName }}</span>
            </div>
          </div>
          <div v-else class="empty">暂无服务</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import api from '../api'

const stats = ref({})
const totalServers = computed(() => {
  if (!stats.value.envDistribution) return 0
  return Object.values(stats.value.envDistribution).reduce((a, b) => a + b, 0)
})

const fetchStats = async () => {
  try {
    const res = await api.get('/dashboard/stats')
    stats.value = res.data
  } catch (e) {
    // ignore
  }
}

onMounted(fetchStats)
</script>

<style scoped>
.stat-value { font-size: 36px; font-weight: bold; text-align: center; color: #303133; }
.stat-value.success { color: #67c23a; }
.env-item { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.env-label { width: 80px; font-weight: 500; }
.env-count { width: 50px; text-align: right; color: #909399; font-size: 13px; }
.health-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.health-item { display: flex; align-items: center; gap: 6px; padding: 6px 12px; background: #f5f7fa; border-radius: 4px; font-size: 13px; }
.dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.dot.green { background: #67c23a; }
.dot.gray { background: #dcdfe6; }
.healthy { border: 1px solid #e1f3d8; }
.unhealthy { border: 1px solid #fde2e2; }
.empty { color: #909399; text-align: center; padding: 20px; }
</style>
