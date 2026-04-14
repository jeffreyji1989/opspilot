<template>
  <div v-loading="loading">
    <div class="page-header">
      <div>
        <div class="back-btn" @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon> 返回服务列表
        </div>
        <h2>
          <StatusDot :status="processStatusKey(service?.processStatus)" show-label />
          {{ service?.instanceName || '服务详情' }}
        </h2>
        <p class="subtitle">{{ moduleName }} · <EnvTag :env-type="serverEnvType" /></p>
      </div>
      <div class="action-buttons">
        <el-button type="primary" @click="showDeployDialog">🚀 发版部署</el-button>
        <el-button type="warning" @click="handleRestart" :disabled="service?.processStatus !== 1">🔄 重启</el-button>
        <el-button type="danger" @click="showRollbackDialog" :disabled="!service?.currentVersion">⏪ 回退</el-button>
      </div>
    </div>

    <!-- Status bar -->
    <el-card style="margin-bottom: 20px;">
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="端口">{{ service?.listenPort || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前版本">{{ service?.currentVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="PID">{{ service?.pid || '-' }}</el-descriptions-item>
        <el-descriptions-item label="部署路径" :span="2">{{ service?.deployPath || '-' }}</el-descriptions-item>
        <el-descriptions-item label="运行时">{{ service?.runtimeType }} {{ service?.runtimeVersion }}</el-descriptions-item>
        <el-descriptions-item label="JVM参数" :span="2">{{ service?.jvmOptions || '-' }}</el-descriptions-item>
        <el-descriptions-item label="健康检查">{{ service?.healthCheckPath || '/' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-tabs v-model="activeTab" type="card">
      <!-- Tab 1: 发版记录 -->
      <el-tab-pane label="📜 发版记录" name="records">
        <el-table :data="deploys" size="default" stripe>
          <el-table-column prop="version" label="版本号" width="180" />
          <el-table-column prop="deployType" label="类型" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.deployType === 'rollback' ? 'warning' : ''">
                {{ row.deployType === 'rollback' ? '回退' : '发版' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="deployStatusType(row.status)" size="small">{{ deployStatusName(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="80">
            <template #default="{ row }">{{ row.durationSeconds }}s</template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间" width="170" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" @click="viewDeployDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!deployLoading && deploys.length === 0" description="暂无发版记录" />
        <el-pagination
          v-if="deploys.length > 0"
          style="margin-top: 12px; justify-content: flex-end;"
          :current-page="deployPage"
          :page-size="10"
          :total="deployTotal"
          @current-change="deployPage = $event; fetchDeploys()"
          layout="total, prev, pager, next"
        />
      </el-tab-pane>

      <!-- Tab 2: 实时日志 -->
      <el-tab-pane label="📋 实时日志" name="log">
        <div class="log-container">
          <div class="log-toolbar">
            <el-radio-group v-model="logFilter" size="small" @change="onLogFilterChange">
              <el-radio-button label="all">全部</el-radio-button>
              <el-radio-button label="INFO">INFO</el-radio-button>
              <el-radio-button label="WARN">WARN</el-radio-button>
              <el-radio-button label="ERROR">ERROR</el-radio-button>
            </el-radio-group>
            <el-input
              v-model="logKeyword"
              placeholder="关键字搜索"
              size="small"
              style="width: 200px;"
              clearable
              @input="onLogKeywordChange"
            />
            <el-switch v-model="autoScroll" active-text="自动滚动" size="small" style="margin-left: 12px;" />
            <el-button size="small" :type="wsConnected ? 'danger' : 'success'" @click="toggleLog">
              {{ wsConnected ? '⏹ 断开' : '▶ 连接' }}
            </el-button>
            <el-button size="small" @click="logLines = ''">清空</el-button>
          </div>
          <pre class="log-content" ref="logRef"><span v-for="(line, idx) in filteredLogLines" :key="idx" :class="logLevelClass(line)">{{ line }}
</span></pre>
          <div class="log-footer">
            <span>共 {{ logLines.split('\n').filter(l => l).length }} 行 | 过滤后 {{ filteredLogLines.length }} 行</span>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 3: 配置管理 -->
      <el-tab-pane label="⚙️ 配置管理" name="config">
        <div v-loading="configLoading">
          <el-form :model="configForm" label-width="140px">
            <el-form-item label="JVM 启动参数">
              <el-input v-model="configForm.jvmOptions" type="textarea" :rows="4" placeholder="-Xms512m -Xmx1024m -XX:+UseG1GC" />
            </el-form-item>
            <el-form-item label="自定义启动命令">
              <el-input v-model="configForm.startCommand" type="textarea" :rows="2" placeholder="留空则使用默认 java -jar" />
            </el-form-item>
            <el-form-item label="健康检查路径">
              <el-input v-model="configForm.healthCheckPath" placeholder="/actuator/health" style="width: 300px;" />
            </el-form-item>
            <el-form-item label="环境变量">
              <el-table :data="configForm.envVars" size="small" style="width: 100%;">
                <el-table-column label="变量名" width="200">
                  <template #default="{ row }">
                    <el-input v-model="row.key" size="small" />
                  </template>
                </el-table-column>
                <el-table-column label="变量值">
                  <template #default="{ row }">
                    <el-input v-model="row.value" size="small" />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="80">
                  <template #default="{ $index }">
                    <el-button size="small" type="danger" @click="configForm.envVars.splice($index, 1)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-button size="small" @click="configForm.envVars.push({ key: '', value: '' })" style="margin-top: 8px;">
                添加变量
              </el-button>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveConfig" :loading="configSaving">保存（下次重启生效）</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- Tab 4: 服务监控 -->
      <el-tab-pane label="📊 服务监控" name="monitor">
        <div class="monitor-toolbar">
          <el-button size="small" @click="fetchMonitor" :loading="monitorLoading">🔄 刷新</el-button>
          <el-switch v-model="autoRefresh" active-text="自动刷新 (30s)" size="small" style="margin-left: 12px;" />
        </div>
        <div class="monitor-grid">
          <div class="monitor-card">
            <div class="mc-label">CPU 使用率</div>
            <div class="mc-value">{{ monitorData.cpuUsage?.toFixed(1) || '-' }}<span class="mc-unit">%</span></div>
          </div>
          <div class="monitor-card">
            <div class="mc-label">内存使用率</div>
            <div class="mc-value">{{ monitorData.memoryUsage?.toFixed(1) || '-' }}<span class="mc-unit">%</span></div>
            <div class="mc-detail">{{ formatBytes(monitorData.memoryUsedMb) }} / {{ formatBytes(monitorData.memoryTotalMb) }}</div>
          </div>
          <div class="monitor-card">
            <div class="mc-label">磁盘使用率</div>
            <div class="mc-value">{{ monitorData.diskUsage?.toFixed(1) || '-' }}<span class="mc-unit">%</span></div>
          </div>
          <div class="monitor-card">
            <div class="mc-label">进程状态</div>
            <div class="mc-value">
              <StatusDot :status="processStatusKey(service?.processStatus)" show-label />
            </div>
            <div class="mc-detail" v-if="service?.pid">PID: {{ service.pid }}</div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 5: 历史日志 -->
      <el-tab-pane label="📁 历史日志" name="history">
        <el-button @click="fetchHistoryLog" size="small" :loading="historyLoading">拉取最近 500 行</el-button>
        <pre class="log-content" style="margin-top: 12px;">{{ historyLog || '点击按钮拉取历史日志' }}</pre>
      </el-tab-pane>
    </el-tabs>

    <!-- Deploy Dialog -->
    <el-dialog v-model="deployDialogVisible" title="发版部署" width="500px" align-center>
      <el-form :model="deployForm" label-width="100px">
        <el-form-item label="目标分支" required>
          <el-select v-model="deployForm.gitBranch" placeholder="选择或输入分支" filterable allow-create style="width: 100%;">
            <el-option v-for="b in branches" :key="b" :label="b" :value="b" />
          </el-select>
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
    <el-dialog v-model="rollbackDialogVisible" title="版本回退" width="500px" align-center>
      <el-form label-width="100px">
        <el-form-item label="目标版本" required>
          <el-select v-model="rollbackVersion" placeholder="选择目标版本" style="width: 100%;" filterable>
            <el-option v-for="v in versions" :key="v" :label="v" :value="v" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rollbackDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="doRollback" :loading="rollbackLoading">确认回退</el-button>
      </template>
    </el-dialog>

    <!-- Deploy Detail Dialog -->
    <el-dialog v-model="deployDetailVisible" title="部署详情" width="800px">
      <div v-if="deployDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="版本号">{{ deployDetail.version }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="deployStatusType(deployDetail.status)" size="small">{{ deployStatusName(deployDetail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ deployDetail.durationSeconds }}s</el-descriptions-item>
          <el-descriptions-item label="时间">{{ deployDetail.createTime }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="deploySteps.length > 0" style="margin-top: 16px;">
          <h4>部署步骤</h4>
          <el-table :data="deploySteps" size="small" stripe>
            <el-table-column prop="stepName" label="步骤" width="120" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="stepStatusType(row.status)" size="small">{{ stepStatusName(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="耗时" width="80">
              <template #default="{ row }">{{ row.durationSeconds }}s</template>
            </el-table-column>
            <el-table-column label="错误信息" show-overflow-tooltip>
              <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-dialog>

    <!-- Restart Confirm -->
    <EnvConfirmDialog
      ref="restartConfirmRef"
      :is-prod="serverEnvType === 3"
      operation-type="服务重启"
      :service-name="service?.instanceName"
      @confirm="doRestart"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import api from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusDot from '../components/StatusDot.vue'
import EnvTag from '../components/EnvTag.vue'
import EnvConfirmDialog from '../components/EnvConfirmDialog.vue'

const route = useRoute()
const instanceId = ref(parseInt(route.params.id))
const loading = ref(false)
const service = ref(null)
const moduleName = ref('')
const serverEnvType = ref(0)
const activeTab = ref('records')

// Deploys
const deploys = ref([])
const deployLoading = ref(false)
const deployPage = ref(1)
const deployTotal = ref(0)
const deployDialogVisible = ref(false)
const deployForm = ref({ gitBranch: 'main', gitCommit: '' })
const branches = ref([])
const deployDetailVisible = ref(false)
const deployDetail = ref(null)
const deploySteps = ref([])

// Rollback
const rollbackDialogVisible = ref(false)
const rollbackVersion = ref('')
const rollbackLoading = ref(false)
const versions = ref([])

// Log
const logLines = ref('')
const historyLog = ref('')
const historyLoading = ref(false)
const ws = ref(null)
const wsConnected = ref(false)
const logFilter = ref('all')
const logKeyword = ref('')
const autoScroll = ref(true)
const logRef = ref(null)

// Config
const configLoading = ref(false)
const configSaving = ref(false)
const configForm = reactive({
  jvmOptions: '',
  startCommand: '',
  healthCheckPath: '/actuator/health',
  envVars: [],
})

// Monitor
const monitorLoading = ref(false)
const monitorData = reactive({})
const autoRefresh = ref(false)
let monitorTimer = null

// Restart confirm
const restartConfirmRef = ref(null)

const processStatusKey = (s) => {
  const map = { 0: 'stopped', 1: 'running', 2: 'deploying', 3: 'error' }
  return map[s] || 'offline'
}

const deployStatusType = (s) => {
  const map = { 0: 'info', 5: 'success', 6: 'danger', 7: 'warning' }
  return map[s] || 'info'
}
const deployStatusName = (s) => {
  const map = { 0: '等待中', 1: '拉代码', 2: '构建中', 3: '部署中', 4: '健康检查', 5: '成功', 6: '失败', 7: '已回滚' }
  return map[s] || '未知'
}
const stepStatusType = (s) => {
  const map = { 0: 'info', 1: 'success', 2: 'danger' }
  return map[s] || 'info'
}
const stepStatusName = (s) => {
  const map = { 0: '执行中', 1: '成功', 2: '失败' }
  return map[s] || '未知'
}

const filteredLogLines = computed(() => {
  let lines = logLines.value.split('\n').filter(l => l)
  if (logFilter.value !== 'all') {
    lines = lines.filter(l => l.includes(logFilter.value))
  }
  if (logKeyword.value) {
    lines = lines.filter(l => l.toLowerCase().includes(logKeyword.value.toLowerCase()))
  }
  return lines
})

const logLevelClass = (line) => {
  if (!line) return ''
  if (line.includes('ERROR') || line.includes('FATAL')) return 'error'
  if (line.includes('WARN')) return 'warn'
  if (line.includes('INFO')) return 'info'
  return ''
}

const fetchService = async () => {
  try {
    const res = await api.get(`/services/${instanceId.value}`)
    service.value = res.data
  } catch {}
}

const fetchDeploys = async () => {
  deployLoading.value = true
  try {
    const res = await api.get('/deploy/history', {
      params: { pageNum: deployPage.value, pageSize: 10, instanceId: instanceId.value }
    })
    deploys.value = res.data.records || []
    deployTotal.value = res.data.total || 0
  } finally {
    deployLoading.value = false
  }
}

const fetchBranches = async () => {
  if (!service.value?.moduleId) return
  try {
    const res = await api.get(`/deploy/branches/${service.value.moduleId}`)
    branches.value = res.data || []
  } catch {}
}

const fetchVersions = async () => {
  try {
    const res = await api.get(`/services/${instanceId.value}/versions`)
    versions.value = res.data || []
  } catch {}
}

const fetchConfig = async () => {
  configLoading.value = true
  try {
    const res = await api.get(`/services/${instanceId.value}/config`)
    if (res.data) {
      configForm.jvmOptions = res.data.jvmOptions || ''
      configForm.startCommand = res.data.startCommand || ''
      configForm.healthCheckPath = res.data.healthCheckPath || '/actuator/health'
      configForm.envVars = res.data.environmentVariables || []
    }
  } finally {
    configLoading.value = false
  }
}

const saveConfig = async () => {
  configSaving.value = true
  try {
    await api.post(`/services/${instanceId.value}/config`, {
      jvmOptions: configForm.jvmOptions,
      startCommand: configForm.startCommand,
      healthCheckPath: configForm.healthCheckPath,
    })
    ElMessage.success('配置已保存，下次重启生效')
  } finally {
    configSaving.value = false
  }
}

const fetchMonitor = async () => {
  monitorLoading.value = true
  try {
    const res = await api.get(`/services/${instanceId.value}/monitor`)
    if (res.data) {
      try {
        Object.assign(monitorData, typeof res.data === 'string' ? JSON.parse(res.data) : res.data)
      } catch {
        // If the data is not JSON, just set it as-is
      }
    }
  } finally {
    monitorLoading.value = false
  }
}

const showDeployDialog = async () => {
  deployForm.value = { gitBranch: 'main', gitCommit: '' }
  deployDialogVisible.value = true
  await fetchBranches()
}

const doDeploy = async () => {
  try {
    await ElMessageBox.confirm(
      `确认发版到分支 ${deployForm.value.gitBranch}？`,
      '确认发版',
      { type: 'warning', confirmButtonText: '开始部署' }
    )
    await api.post('/deploy/execute', {
      moduleId: service.value.moduleId,
      instanceId: instanceId.value,
    })
    ElMessage.success('发版任务已提交')
    deployDialogVisible.value = false
    fetchDeploys()
  } catch {}
}

const handleRestart = () => {
  restartConfirmRef.value.open()
}

const doRestart = async () => {
  try {
    await api.post(`/services/${instanceId.value}/restart`)
    ElMessage.success('重启成功')
    fetchService()
  } catch {}
}

const showRollbackDialog = async () => {
  await fetchVersions()
  rollbackVersion.value = service.value?.currentVersion || ''
  rollbackDialogVisible.value = true
}

const doRollback = async () => {
  if (!rollbackVersion.value) {
    ElMessage.warning('请选择目标版本')
    return
  }
  rollbackLoading.value = true
  try {
    await api.post(`/services/${instanceId.value}/rollback`, { targetVersion: rollbackVersion.value })
    ElMessage.success('回退成功')
    rollbackDialogVisible.value = false
    fetchService()
    fetchDeploys()
  } finally {
    rollbackLoading.value = false
  }
}

const viewDeployDetail = async (row) => {
  try {
    const res = await api.get(`/deploy/progress/${row.id}`)
    deployDetail.value = res.data.record
    deploySteps.value = res.data.steps || []
    deployDetailVisible.value = true
  } catch {}
}

const onLogFilterChange = () => {}
const onLogKeywordChange = () => {}

// WebSocket log
const connectLog = () => {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws.value = new WebSocket(`${protocol}//${location.host}/api/ws/log/${instanceId.value}`)
  ws.value.onmessage = (e) => {
    logLines.value += e.data + '\n'
    if (autoScroll.value && logRef.value) {
      setTimeout(() => {
        if (logRef.value) logRef.value.scrollTop = logRef.value.scrollHeight
      }, 50)
    }
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
  historyLoading.value = true
  try {
    const res = await api.get(`/services/${instanceId.value}/logs`, { params: { lines: 500 } })
    historyLog.value = res.data || '无日志数据'
  } finally {
    historyLoading.value = false
  }
}

const formatBytes = (mb) => {
  if (!mb) return '-'
  if (mb >= 1024) return (mb / 1024).toFixed(1) + ' GB'
  return mb + ' MB'
}

watch(autoRefresh, (val) => {
  if (monitorTimer) clearInterval(monitorTimer)
  if (val) {
    monitorTimer = setInterval(fetchMonitor, 30000)
  }
})

onMounted(async () => {
  loading.value = true
  try {
    await Promise.all([fetchService(), fetchDeploys()])
  } finally {
    loading.value = false
  }
  connectLog()
  // Fetch config and monitor data when tab is active
  watch(activeTab, (tab) => {
    if (tab === 'config') fetchConfig()
    if (tab === 'monitor') fetchMonitor()
  })
})

onUnmounted(() => {
  disconnectLog()
  if (monitorTimer) clearInterval(monitorTimer)
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}
.page-header h2 {
  font-size: 20px;
  font-weight: 600;
  color: #111827;
  display: flex;
  align-items: center;
  gap: 8px;
}
.subtitle {
  font-size: 13px;
  color: #6B7280;
  margin-top: 4px;
}
.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #6B7280;
  font-size: 13px;
  cursor: pointer;
  margin-bottom: 8px;
}
.back-btn:hover { color: #6366F1; }
.action-buttons { display: flex; gap: 8px; }

.log-container {
  background: #1E1E2D;
  border-radius: 8px;
  overflow: hidden;
}
.log-toolbar {
  display: flex;
  gap: 12px;
  padding: 10px 12px;
  background: #2D2D3D;
  align-items: center;
  flex-wrap: wrap;
}
.log-content {
  background: #1E1E2D;
  color: #D4D4D4;
  padding: 12px;
  margin: 0;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  max-height: 500px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
}
.log-content .error { color: #F87171; }
.log-content .warn { color: #FBBF24; }
.log-content .info { color: #60A5FA; }
.log-footer {
  padding: 8px 12px;
  background: #2D2D3D;
  font-size: 12px;
  color: #A2A3B7;
}

.monitor-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.monitor-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.monitor-card {
  background: #F9FAFB;
  border-radius: 8px;
  padding: 16px;
  text-align: center;
}
.mc-label { font-size: 12px; color: #6B7280; margin-bottom: 8px; }
.mc-value { font-size: 24px; font-weight: 700; color: #1F2937; }
.mc-unit { font-size: 12px; color: #6B7280; font-weight: 400; }
.mc-detail { font-size: 12px; color: #9CA3AF; margin-top: 4px; }

@media (max-width: 768px) {
  .monitor-grid { grid-template-columns: 1fr 1fr; }
}
</style>
