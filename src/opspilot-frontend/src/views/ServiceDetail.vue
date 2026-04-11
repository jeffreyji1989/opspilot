<template>
  <div v-loading="loading">
    <div class="page-header">
      <h2>
        <el-button @click="$router.back()" icon="ArrowLeft" circle size="small" style="margin-right: 12px;" />
        {{ service?.instanceName || '服务详情' }}
      </h2>
      <div>
        <el-button type="primary" @click="showDeployDialog">🚀 发版部署</el-button>
        <el-button type="warning" @click="restartService" :disabled="service?.processStatus !== 1">🔄 重启</el-button>
        <el-button type="danger" @click="showRollbackDialog" :disabled="!service?.currentVersion">⏪ 回退</el-button>
      </div>
    </div>

    <!-- Status bar -->
    <el-card style="margin-bottom: 20px;">
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="状态">
          <el-tag :type="service?.processStatus === 1 ? 'success' : 'info'">
            {{ service?.processStatus === 1 ? '运行中' : '已停止' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="端口">{{ service?.listenPort }}</el-descriptions-item>
        <el-descriptions-item label="当前版本">{{ service?.currentVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="部署路径">{{ service?.deployPath }}</el-descriptions-item>
        <el-descriptions-item label="运行时">{{ service?.runtimeType }} {{ service?.runtimeVersion }}</el-descriptions-item>
        <el-descriptions-item label="JVM参数">{{ service?.jvmOptions || '-' }}</el-descriptions-item>
        <el-descriptions-item label="健康检查">{{ service?.healthCheckPath || '/' }}</el-descriptions-item>
        <el-descriptions-item label="PID">{{ service?.pid || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-tabs v-model="activeTab">
      <!-- Deploy Records Tab -->
      <el-tab-pane label="📜 发版记录" name="records">
        <el-table :data="deploys" size="small">
          <el-table-column prop="deployNo" label="发版编号" width="180" />
          <el-table-column prop="version" label="版本号" width="180" />
          <el-table-column prop="gitBranch" label="分支" width="120" />
          <el-table-column prop="gitCommit" label="Commit" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="deployStatusType(row.status)" size="small">{{ deployStatusName(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="durationSeconds" label="耗时" width="80">
            <template #default="{ row }">{{ row.durationSeconds }}s</template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间" width="160" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" @click="viewDeployLog(row)">日志</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          style="margin-top: 12px; justify-content: flex-end;"
          :current-page="deployPage"
          :page-size="10"
          :total="deployTotal"
          @current-change="deployPage = $event; fetchDeploys()"
          layout="prev, pager, next"
        />
      </el-tab-pane>

      <!-- Real-time Log Tab -->
      <el-tab-pane label="📋 实时日志" name="log">
        <div class="log-container">
          <div class="log-toolbar">
            <el-button size="small" :type="wsConnected ? 'danger' : 'success'" @click="toggleLog">
              {{ wsConnected ? '⏹ 断开' : '▶ 连接' }}
            </el-button>
            <el-button size="small" @click="logLines = ''">清空</el-button>
            <span style="margin-left: auto; color: #909399; font-size: 12px;">
              共 {{ logLines.split('\n').filter(l => l).length }} 行
            </span>
          </div>
          <pre class="log-content">{{ logLines }}</pre>
        </div>
      </el-tab-pane>

      <!-- Historical Log Tab -->
      <el-tab-pane label="📁 历史日志" name="history">
        <el-button @click="fetchHistoryLog" size="small">拉取最近 500 行</el-button>
        <pre class="log-content" style="margin-top: 12px;">{{ historyLog }}</pre>
      </el-tab-pane>
    </el-tabs>

    <!-- Deploy Dialog -->
    <el-dialog v-model="deployDialogVisible" title="发版部署" width="500px">
      <el-form :model="deployForm" label-width="100px">
        <el-form-item label="目标分支" required>
          <el-input v-model="deployForm.gitBranch" placeholder="main / develop" />
        </el-form-item>
        <el-form-item label="Commit (可选)">
          <el-input v-model="deployForm.gitCommit" placeholder="指定 commit hash" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deployDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="doDeploy">开始部署</el-button>
      </template>
    </el-dialog>

    <!-- Rollback Dialog -->
    <el-dialog v-model="rollbackDialogVisible" title="版本回退" width="500px">
      <el-form>
        <el-form-item label="目标版本" required>
          <el-input v-model="rollbackVersion" placeholder="输入要回退的版本号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rollbackDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="doRollback">确认回退</el-button>
      </template>
    </el-dialog>

    <!-- Deploy Log Dialog -->
    <el-dialog v-model="deployLogVisible" title="发版日志" width="800px">
      <pre class="log-content">{{ deployLogContent }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const instanceId = ref(parseInt(route.params.id))
const loading = ref(false)
const service = ref(null)
const activeTab = ref('records')
const deploys = ref([])
const deployPage = ref(1)
const deployTotal = ref(0)
const deployDialogVisible = ref(false)
const deployForm = ref({ gitBranch: 'main', gitCommit: '' })
const rollbackDialogVisible = ref(false)
const rollbackVersion = ref('')
const deployLogVisible = ref(false)
const deployLogContent = ref('')

// Log
const logLines = ref('')
const historyLog = ref('')
const ws = ref(null)
const wsConnected = ref(false)

const deployStatusType = (s) => {
  const map = { 0: 'info', 1: '', 2: '', 3: '', 4: '', 5: 'success', 6: 'danger', 7: 'warning' }
  return map[s] || 'info'
}
const deployStatusName = (s) => {
  const map = { 0: '等待中', 1: '拉代码', 2: '构建中', 3: '部署中', 4: '健康检查', 5: '成功', 6: '失败', 7: '已回滚' }
  return map[s] || '未知'
}

const fetchService = async () => {
  const res = await api.get(`/services/${instanceId.value}`)
  service.value = res.data
}

const fetchDeploys = async () => {
  const res = await api.get('/deploys', { params: { pageNum: deployPage.value, pageSize: 10, instanceId: instanceId.value } })
  deploys.value = res.data.records
  deployTotal.value = res.data.total
}

const showDeployDialog = () => {
  deployForm.value = { gitBranch: 'main', gitCommit: '' }
  deployDialogVisible.value = true
}

const doDeploy = async () => {
  try {
    await ElMessageBox.confirm(
      `确认发版到分支 ${deployForm.value.gitBranch}？`,
      '确认发版',
      { type: 'warning', confirmButtonText: '开始部署' }
    )
    await api.post('/deploys', { instanceId: instanceId.value, ...deployForm.value })
    ElMessage.success('发版任务已提交')
    deployDialogVisible.value = false
    fetchDeploys()
  } catch {}
}

const restartService = async () => {
  try {
    await ElMessageBox.confirm('确认重启服务？', '确认', { type: 'warning' })
    await api.post(`/services/${instanceId.value}/restart`)
    ElMessage.success('重启成功')
    fetchService()
  } catch {}
}

const showRollbackDialog = () => {
  rollbackVersion.value = service.value?.currentVersion || ''
  rollbackDialogVisible.value = true
}

const doRollback = async () => {
  try {
    await api.post(`/services/${instanceId.value}/rollback`, { targetVersion: rollbackVersion.value })
    ElMessage.success('回退成功')
    rollbackDialogVisible.value = false
    fetchService()
    fetchDeploys()
  } catch {}
}

const viewDeployLog = async (row) => {
  const res = await api.get(`/deploys/${row.id}/log`)
  deployLogContent.value = res.data
  deployLogVisible.value = true
}

// WebSocket log
const connectLog = () => {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws.value = new WebSocket(`${protocol}//${location.host}/api/ws/log/${instanceId.value}`)
  ws.value.onmessage = (e) => {
    logLines.value += e.data + '\n'
  }
  ws.value.onopen = () => { wsConnected.value = true }
  ws.value.onclose = () => { wsConnected.value = false }
  ws.value.onerror = () => { wsConnected.value = false }
}

const disconnectLog = () => {
  if (ws.value) {
    ws.value.close()
    ws.value = null
  }
}

const toggleLog = () => {
  wsConnected.value ? disconnectLog() : connectLog()
}

const fetchHistoryLog = async () => {
  const res = await api.get(`/services/${instanceId.value}/logs`, { params: { lines: 500 } })
  historyLog.value = res.data
}

onMounted(() => {
  loading.value = true
  Promise.all([fetchService(), fetchDeploys()]).finally(() => { loading.value = false })
  connectLog()
})

onUnmounted(() => {
  disconnectLog()
})
</script>

<style scoped>
.log-container {
  background: #1e1e1e;
  border-radius: 6px;
  overflow: hidden;
}
.log-toolbar {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  background: #2d2d2d;
  gap: 8px;
}
.log-content {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  margin: 0;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  max-height: 500px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
