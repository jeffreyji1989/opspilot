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
          <StatusDot :status="service?.processStatus" :pulse="service?.processStatus === 1" show-text />
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

    <el-tabs v-model="activeTab" @tab-click="onTabChange">
      <!-- Tab 1: 基本信息 -->
      <el-tab-pane label="基本信息" name="basic">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="服务 ID">{{ service?.id }}</el-descriptions-item>
          <el-descriptions-item label="服务名称">{{ service?.instanceName }}</el-descriptions-item>
          <el-descriptions-item label="部署路径">{{ service?.deployPath }}</el-descriptions-item>
          <el-descriptions-item label="监听端口">{{ service?.listenPort }}</el-descriptions-item>
          <el-descriptions-item label="运行时类型">{{ service?.runtimeType }}</el-descriptions-item>
          <el-descriptions-item label="运行时版本">{{ service?.runtimeVersion }}</el-descriptions-item>
          <el-descriptions-item label="JVM 参数">{{ service?.jvmOptions || '-' }}</el-descriptions-item>
          <el-descriptions-item label="启动命令">{{ service?.startCommand || '-' }}</el-descriptions-item>
          <el-descriptions-item label="健康检查路径">{{ service?.healthCheckPath || '-' }}</el-descriptions-item>
          <el-descriptions-item label="健康检查端口">{{ service?.healthCheckPort || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ service?.createTime }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ service?.updateTime }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <!-- Tab 2: 发版记录 -->
      <el-tab-pane label="发版记录" name="records">
        <el-table :data="deploys" size="small" v-loading="deployLoading">
          <el-table-column prop="version" label="版本号" width="200" />
          <el-table-column prop="gitBranch" label="分支" width="120" />
          <el-table-column prop="gitCommit" label="Commit" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="deployStatusType(row.status)" size="small">{{ deployStatusName(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="80">
            <template #default="{ row }">{{ row.durationSeconds != null ? row.durationSeconds + 's' : '-' }}</template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间" width="180" />
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

      <!-- Tab 3: 实时日志 -->
      <el-tab-pane label="实时日志" name="log">
        <div class="log-container">
          <div class="log-toolbar">
            <el-select v-model="logLevel" placeholder="级别" size="small" style="width: 100px;" @change="reconnectLog">
              <el-option label="全部" value="" />
              <el-option label="ERROR" value="ERROR" />
              <el-option label="WARN" value="WARN" />
              <el-option label="INFO" value="INFO" />
              <el-option label="DEBUG" value="DEBUG" />
            </el-select>
            <el-input
              v-model="logKeyword"
              placeholder="关键字搜索"
              size="small"
              style="width: 180px;"
              clearable
              @keyup.enter="reconnectLog"
            />
            <el-button size="small" :type="wsConnected ? 'danger' : 'success'" @click="toggleLog">
              {{ wsConnected ? '⏹ 断开' : '▶ 连接' }}
            </el-button>
            <el-button size="small" @click="logLines = ''">清空</el-button>
            <el-switch v-model="autoScroll" active-text="自动滚动" size="small" style="margin-left: auto;" />
            <span style="color: #909399; font-size: 12px; margin-left: 12px;">
              共 {{ logLines.split('\n').filter(l => l).length }} 行
            </span>
          </div>
          <pre ref="logContentRef" class="log-content">{{ logLines }}</pre>
        </div>
      </el-tab-pane>

      <!-- Tab 4: 配置管理 -->
      <el-tab-pane label="配置管理" name="config">
        <div v-loading="configLoading">
          <el-card v-if="config" style="margin-bottom: 16px;">
            <template #header>
              <span>服务配置</span>
            </template>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="实例名称">{{ config.instanceName }}</el-descriptions-item>
              <el-descriptions-item label="部署路径">{{ config.deployPath }}</el-descriptions-item>
              <el-descriptions-item label="监听端口">{{ config.listenPort }}</el-descriptions-item>
              <el-descriptions-item label="健康检查">{{ config.healthCheckPath }}</el-descriptions-item>
              <el-descriptions-item label="运行时">{{ config.runtimeType }} {{ config.runtimeVersion }}</el-descriptions-item>
              <el-descriptions-item label="当前版本">{{ config.currentVersion || '-' }}</el-descriptions-item>
            </el-descriptions>
          </el-card>

          <el-card>
            <template #header>
              <span>JVM 参数</span>
              <el-button size="small" style="float: right;" @click="showJvmEdit = !showJvmEdit">
                {{ showJvmEdit ? '取消' : '编辑' }}
              </el-button>
            </template>
            <div v-if="!showJvmEdit">
              <pre class="config-value">{{ config?.jvmOptions || '（未配置）' }}</pre>
            </div>
            <div v-else>
              <el-input v-model="jvmEditValue" type="textarea" :rows="3" />
              <div style="margin-top: 8px;">
                <el-button size="small" @click="showJvmEdit = false">取消</el-button>
                <el-button size="small" type="primary" @click="saveJvmOptions">保存</el-button>
              </div>
            </div>
          </el-card>

          <el-card style="margin-top: 16px;">
            <template #header>
              <span>启动命令</span>
              <el-button size="small" style="float: right;" @click="showCmdEdit = !showCmdEdit">
                {{ showCmdEdit ? '取消' : '编辑' }}
              </el-button>
            </template>
            <div v-if="!showCmdEdit">
              <pre class="config-value">{{ config?.startCommand || '（未配置，使用默认启动）' }}</pre>
            </div>
            <div v-else>
              <el-input v-model="cmdEditValue" />
              <div style="margin-top: 8px;">
                <el-button size="small" @click="showCmdEdit = false">取消</el-button>
                <el-button size="small" type="primary" @click="saveStartCommand">保存</el-button>
              </div>
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <!-- Tab 5: 监控 -->
      <el-tab-pane label="监控" name="monitor">
        <el-row :gutter="16" v-loading="monitorLoading">
          <el-col :span="8">
            <el-card>
              <template #header>CPU 使用率</template>
              <div class="monitor-value">{{ monitor?.cpu || 'N/A' }}</div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card>
              <template #header>内存使用</template>
              <div class="monitor-value">{{ monitor?.memory || 'N/A' }}</div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card>
              <template #header>磁盘使用</template>
              <div class="monitor-value">{{ monitor?.disk || 'N/A' }}</div>
            </el-card>
          </el-col>
        </el-row>
        <el-card style="margin-top: 16px;">
          <template #header>进程信息</template>
          <pre class="config-value">{{ monitor?.process || '（服务未启动）' }}</pre>
        </el-card>
        <el-card style="margin-top: 16px;">
          <template #header>系统运行时间</template>
          <pre class="config-value">{{ monitor?.systemUptime || 'N/A' }}</pre>
        </el-card>
        <el-button style="margin-top: 12px;" @click="fetchMonitor" :loading="monitorLoading">刷新</el-button>
      </el-tab-pane>
    </el-tabs>

    <!-- Deploy Dialog -->
    <el-dialog v-model="deployDialogVisible" title="发版部署" width="500px">
      <el-form :model="deployForm" label-width="100px">
        <el-form-item label="目标分支" required>
          <el-select v-model="deployForm.gitBranch" placeholder="选择分支" filterable style="width: 100%;" @focus="fetchBranches">
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

    <!-- Deploy Progress Dialog -->
    <DeployProgressDialog
      v-model="deployProgressVisible"
      :record="deployProgressRecord"
      :steps="deployProgressSteps"
      @close="stopDeployPolling"
    />

    <!-- Rollback Dialog -->
    <el-dialog v-model="rollbackDialogVisible" title="版本回退" width="500px">
      <el-form>
        <el-form-item label="目标版本" required>
          <el-select v-model="rollbackVersion" placeholder="选择版本" filterable style="width: 100%;" @focus="fetchVersions">
            <el-option v-for="v in versions" :key="v.version" :label="v.version" :value="v.version" />
          </el-select>
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

    <!-- Restart Confirm -->
    <EnvConfirmDialog
      v-model="restartConfirmVisible"
      title="确认重启"
      message="确认重启服务？"
      warning="重启期间服务将短暂不可用。"
      confirm-text="确认重启"
      confirm-button-type="warning"
      :loading="restarting"
      @confirm="doRestart"
    />
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '../api'
import { ElMessage } from 'element-plus'
import StatusDot from '../components/StatusDot.vue'
import DeployProgressDialog from '../components/DeployProgressDialog.vue'
import EnvConfirmDialog from '../components/EnvConfirmDialog.vue'

const route = useRoute()
const instanceId = ref(parseInt(route.params.id))
const loading = ref(false)
const service = ref(null)
const activeTab = ref('basic')

// Deploys
const deploys = ref([])
const deployPage = ref(1)
const deployTotal = ref(0)
const deployLoading = ref(false)

// Deploy dialog
const deployDialogVisible = ref(false)
const deployForm = ref({ gitBranch: 'main', gitCommit: '' })
const branches = ref([])
const deployProgressVisible = ref(false)
const deployProgressRecord = ref(null)
const deployProgressSteps = ref([])
let deployPollTimer = null

// Rollback
const rollbackDialogVisible = ref(false)
const rollbackVersion = ref('')
const versions = ref([])

// Deploy log
const deployLogVisible = ref(false)
const deployLogContent = ref('')

// Log
const logLines = ref('')
const logLevel = ref('')
const logKeyword = ref('')
const autoScroll = ref(true)
const logContentRef = ref(null)
const ws = ref(null)
const wsConnected = ref(false)

// Config
const config = ref(null)
const configLoading = ref(false)
const showJvmEdit = ref(false)
const showCmdEdit = ref(false)
const jvmEditValue = ref('')
const cmdEditValue = ref('')

// Monitor
const monitor = ref(null)
const monitorLoading = ref(false)

// Restart confirm
const restartConfirmVisible = ref(false)
const restarting = ref(false)

const deployStatusType = (s) => {
  const map = { 0: 'info', 1: 'warning', 2: 'warning', 3: 'warning', 4: 'warning', 5: 'success', 6: 'danger', 7: '' }
  return map[s] || 'info'
}
const deployStatusName = (s) => {
  const map = { 0: '等待中', 1: '拉取代码', 2: '编译构建', 3: '部署中', 4: '健康检查', 5: '成功', 6: '失败', 7: '已回滚' }
  return map[s] || '未知'
}

const fetchService = async () => {
  const res = await api.get(`/services/${instanceId.value}`)
  service.value = res.data
}

const fetchDeploys = async () => {
  deployLoading.value = true
  try {
    const res = await api.get('/deploy/history', { params: { pageNum: deployPage.value, pageSize: 10, instanceId: instanceId.value } })
    deploys.value = res.data.records
    deployTotal.value = res.data.total
  } finally {
    deployLoading.value = false
  }
}

const fetchVersions = async () => {
  const res = await api.get(`/services/${instanceId.value}/versions`)
  versions.value = res.data
}

const fetchBranches = async () => {
  if (branches.value.length > 0) return
  if (service.value?.moduleId) {
    const res = await api.get(`/deploy/branches/${service.value.moduleId}`)
    branches.value = res.data || []
  }
}

const showDeployDialog = () => {
  deployForm.value = { gitBranch: 'main', gitCommit: '' }
  deployDialogVisible.value = true
}

const doDeploy = async () => {
  try {
    await api.post('/deploy/execute', {
      moduleId: service.value.moduleId,
      instanceId: instanceId.value,
      gitBranch: deployForm.value.gitBranch,
      gitCommit: deployForm.value.gitCommit,
      operator: 'user'
    })
    ElMessage.success('发版任务已提交')
    deployDialogVisible.value = false
    deployProgressVisible.value = true
    startDeployPolling()
    fetchDeploys()
  } catch (e) {}
}

const startDeployPolling = () => {
  deployPollTimer = setInterval(async () => {
    try {
      const res = await api.get(`/deploy/progress/${deployProgressRecord.value?.id || 'latest'}`)
      // We need to track the latest record ID; for now poll deploys
      const deploysRes = await api.get('/deploy/history', { params: { pageNum: 1, pageSize: 1, instanceId: instanceId.value } })
      if (deploysRes.data.records.length > 0) {
        const latest = deploysRes.data.records[0]
        deployProgressRecord.value = latest
        const progressRes = await api.get(`/deploy/progress/${latest.id}`)
        deployProgressSteps.value = progressRes.data.steps || []
        deployProgressRecord.value = progressRes.data.record

        // Stop polling if complete
        if (latest.status === 5 || latest.status === 6 || latest.status === 7) {
          stopDeployPolling()
        }
      }
    } catch (e) {}
  }, 2000)
}

const stopDeployPolling = () => {
  if (deployPollTimer) {
    clearInterval(deployPollTimer)
    deployPollTimer = null
  }
}

const restartService = () => {
  restartConfirmVisible.value = true
}

const doRestart = async () => {
  restarting.value = true
  try {
    await api.post(`/services/${instanceId.value}/restart`)
    ElMessage.success('重启成功')
    restartConfirmVisible.value = false
    fetchService()
  } catch (e) {}
  finally {
    restarting.value = false
  }
}

const showRollbackDialog = () => {
  rollbackDialogVisible.value = true
}

const doRollback = async () => {
  try {
    await api.post(`/services/${instanceId.value}/rollback`, { targetVersion: rollbackVersion.value })
    ElMessage.success('回退成功')
    rollbackDialogVisible.value = false
    fetchService()
    fetchDeploys()
  } catch (e) {}
}

const viewDeployLog = async (row) => {
  const res = await api.get(`/deploys/${row.id}/log`)
  deployLogContent.value = res.data
  deployLogVisible.value = true
}

// WebSocket log
const connectLog = () => {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  let url = `${protocol}//${location.host}/api/ws/log/${instanceId.value}`
  const params = []
  if (logLevel.value) params.push(`level=${logLevel.value}`)
  if (logKeyword.value) params.push(`keyword=${logKeyword.value}`)
  if (params.length > 0) url += '?' + params.join('&')

  ws.value = new WebSocket(url)
  ws.value.onmessage = (e) => {
    logLines.value += e.data + '\n'
    if (autoScroll.value && logContentRef.value) {
      nextTick(() => {
        logContentRef.value.scrollTop = logContentRef.value.scrollHeight
      })
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

const reconnectLog = () => {
  disconnectLog()
  connectLog()
}

// Config
const fetchConfig = async () => {
  configLoading.value = true
  try {
    const res = await api.get(`/services/${instanceId.value}/config`)
    config.value = res.data
    jvmEditValue.value = res.data.jvmOptions || ''
    cmdEditValue.value = res.data.startCommand || ''
  } finally {
    configLoading.value = false
  }
}

const saveJvmOptions = async () => {
  try {
    await api.put(`/services/${instanceId.value}/config/jvm`, { value: jvmEditValue.value })
    ElMessage.success('JVM 参数已更新')
    showJvmEdit.value = false
    fetchConfig()
  } catch (e) {}
}

const saveStartCommand = async () => {
  try {
    await api.put(`/services/${instanceId.value}/config/start-command`, { value: cmdEditValue.value })
    ElMessage.success('启动命令已更新')
    showCmdEdit.value = false
    fetchConfig()
  } catch (e) {}
}

// Monitor
const fetchMonitor = async () => {
  monitorLoading.value = true
  try {
    const res = await api.get(`/services/${instanceId.value}/monitor`)
    monitor.value = res.data
  } finally {
    monitorLoading.value = false
  }
}

const onTabChange = (tab) => {
  if (tab.props.name === 'config') fetchConfig()
  if (tab.props.name === 'monitor') fetchMonitor()
}

onMounted(() => {
  loading.value = true
  Promise.all([fetchService(), fetchDeploys()]).finally(() => { loading.value = false })
})

onUnmounted(() => {
  disconnectLog()
  stopDeployPolling()
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
  flex-wrap: wrap;
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
.config-value {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}
.monitor-value {
  font-size: 24px;
  font-weight: bold;
  color: #409eff;
  text-align: center;
  padding: 16px 0;
}
</style>
