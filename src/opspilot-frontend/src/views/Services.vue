<template>
  <div>
    <div class="page-header">
      <div>
        <h2>服务管理</h2>
        <p class="page-desc">管理服务实例的创建、启动、停止、重启和删除</p>
      </div>
      <el-button type="primary" @click="showCreateWizard">
        <el-icon><Plus /></el-icon> 创建服务
      </el-button>
    </div>

    <!-- Search bar -->
    <div class="card search-bar">
      <el-form inline>
        <el-form-item label="项目">
          <el-select v-model="searchProjectId" placeholder="全部" clearable @change="fetchData" style="width: 160px;">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="模块">
          <el-select v-model="searchModuleId" placeholder="全部" clearable @change="fetchData" style="width: 160px;">
            <el-option v-for="m in modules" :key="m.id" :label="m.moduleName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchStatus" placeholder="全部" clearable @change="fetchData" style="width: 120px;">
            <el-option label="运行中" :value="1" />
            <el-option label="已停止" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- Service table -->
    <div class="card table-card">
      <el-table :data="tableData" v-loading="loading" stripe size="default">
        <el-table-column prop="instanceName" label="服务名称" min-width="180">
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/services/${row.id}`)" :underline="false">
              {{ row.instanceName }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusDot :status="processStatusKey(row.processStatus)" show-label />
          </template>
        </el-table-column>
        <el-table-column prop="listenPort" label="端口" width="80" />
        <el-table-column prop="currentVersion" label="当前版本" width="180" />
        <el-table-column prop="deployPath" label="部署路径" min-width="200" show-overflow-tooltip />
        <el-table-column label="运行时" width="120">
          <template #default="{ row }">
            {{ runtimeLabel(row.runtimeType, row.runtimeVersion) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="$router.push(`/services/${row.id}`)">详情</el-button>
            <el-button size="small" type="success" @click="startService(row)" :disabled="row.processStatus === 1">启动</el-button>
            <el-button size="small" type="warning" @click="stopService(row)" :disabled="row.processStatus !== 1">停止</el-button>
            <el-button size="small" type="info" @click="restartService(row)" :disabled="row.processStatus !== 1">重启</el-button>
            <el-button size="small" type="danger" @click="deleteService(row)" :disabled="row.processStatus === 1">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Empty state -->
      <el-empty v-if="!loading && tableData.length === 0" description="暂无服务实例">
        <el-button type="primary" @click="showCreateWizard">创建服务</el-button>
      </el-empty>

      <el-pagination
        v-if="tableData.length > 0"
        style="margin-top: 16px; justify-content: flex-end;"
        :current-page="page"
        :page-size="pageSize"
        :total="total"
        @current-change="page = $event; fetchData()"
        layout="total, prev, pager, next"
      />
    </div>

    <!-- Create Service Wizard Dialog -->
    <el-dialog v-model="wizardVisible" title="创建服务" width="700px" align-center>
      <el-steps :active="wizardStep" finish-status="success" style="margin-bottom: 24px;">
        <el-step title="选择模块" />
        <el-step title="选择服务器" />
        <el-step title="配置参数" />
      </el-steps>

      <!-- Step 1: Select Module -->
      <div v-if="wizardStep === 0">
        <el-form :model="form" label-width="100px">
          <el-form-item label="所属项目" required>
            <el-select v-model="form.projectId" @change="onProjectChange" placeholder="请选择项目" style="width: 100%;">
              <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="所属模块" required>
            <el-select v-model="form.moduleId" @change="onModuleChange" placeholder="请选择模块" style="width: 100%;">
              <el-option v-for="m in filteredModules" :key="m.id" :label="m.moduleName" :value="m.id" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 2: Select Server -->
      <div v-if="wizardStep === 1">
        <el-form :model="form" label-width="100px">
          <el-form-item label="目标服务器" required>
            <el-select v-model="form.serverId" placeholder="请选择服务器" style="width: 100%;">
              <el-option v-for="s in servers" :key="s.id" :label="`${s.serverName} (${s.hostname})`" :value="s.id">
                <template #default>
                  <span>{{ s.serverName }}</span>
                  <span style="color: #909399; margin-left: 8px;">{{ s.hostname }}</span>
                  <EnvTag :env-type="s.envType" style="margin-left: 8px;" />
                </template>
              </el-option>
            </el-select>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 3: Configure -->
      <div v-if="wizardStep === 2">
        <el-form :model="form" label-width="120px">
          <el-form-item label="服务名称" required>
            <el-input v-model="form.instanceName" placeholder="如：user-service-prod-01" />
          </el-form-item>
          <el-form-item label="服务端口" required>
            <el-input-number v-model="form.listenPort" :min="1" :max="65535" style="width: 100%;" />
          </el-form-item>
          <el-form-item label="部署路径" required>
            <el-input v-model="form.deployPath" placeholder="/opt/apps/service-name" />
          </el-form-item>
          <el-form-item label="JVM参数" v-if="form.runtimeType === 'java'">
            <el-input v-model="form.jvmOptions" type="textarea" :rows="2" placeholder="-Xms512m -Xmx1024m -XX:+UseG1GC" />
          </el-form-item>
          <el-form-item label="健康检查路径">
            <el-input v-model="form.healthCheckPath" placeholder="/actuator/health" />
          </el-form-item>
          <el-form-item label="自定义启动命令">
            <el-input v-model="form.startCommand" placeholder="留空则使用默认 java -jar" />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="wizardVisible = false">取消</el-button>
        <el-button v-if="wizardStep > 0" @click="wizardStep--">上一步</el-button>
        <el-button v-if="wizardStep < 2" type="primary" @click="wizardStep++">下一步</el-button>
        <el-button v-if="wizardStep === 2" type="primary" @click="saveService" :loading="saving">创建服务</el-button>
      </template>
    </el-dialog>

    <!-- Restart Confirm Dialog -->
    <EnvConfirmDialog
      ref="restartConfirmRef"
      :is-prod="restartIsProd"
      operation-type="服务重启"
      :service-name="restartServiceName"
      @confirm="doRestart"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import api from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusDot from '../components/StatusDot.vue'
import EnvTag from '../components/EnvTag.vue'
import EnvConfirmDialog from '../components/EnvConfirmDialog.vue'

const loading = ref(false)
const saving = ref(false)
const tableData = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

// Search
const searchProjectId = ref(null)
const searchModuleId = ref(null)
const searchStatus = ref(null)

// Data
const modules = ref([])
const filteredModules = ref([])
const servers = ref([])
const projects = ref([])

// Wizard
const wizardVisible = ref(false)
const wizardStep = ref(0)
const form = reactive({
  projectId: null, moduleId: null, serverId: null,
  instanceName: '', listenPort: 8080, deployPath: '',
  runtimeType: 'java', runtimeVersion: '17',
  jvmOptions: '', healthCheckPath: '/actuator/health',
  startCommand: ''
})

// Restart confirm
const restartConfirmRef = ref(null)
const restartIsProd = ref(false)
const restartServiceName = ref('')
const restartTargetId = ref(null)

const processStatusKey = (s) => {
  const map = { 0: 'stopped', 1: 'running', 2: 'deploying', 3: 'error' }
  return map[s] || 'offline'
}
const runtimeLabel = (type, version) => {
  const typeMap = { java: 'Java', node: 'Node.js', python: 'Python' }
  return version ? `${typeMap[type] || type} ${version}` : (typeMap[type] || type)
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = { pageNum: page.value, pageSize: pageSize.value }
    if (searchModuleId.value !== null) params.moduleId = searchModuleId.value
    if (searchStatus.value !== null) params.processStatus = searchStatus.value
    const res = await api.get('/services', { params })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const fetchProjects = async () => {
  try {
    const res = await api.get('/projects?pageNum=1&pageSize=100')
    projects.value = res.data.records
  } catch {}
}

const fetchModules = async () => {
  try {
    for (const p of projects.value) {
      const mr = await api.get(`/projects/${p.id}/modules`)
      modules.value.push(...(mr.data || []))
    }
  } catch {}
}

const fetchServers = async () => {
  try {
    const res = await api.get('/servers?pageNum=1&pageSize=100')
    servers.value = res.data.records
  } catch {}
}

const onProjectChange = () => {
  filteredModules.value = modules.value.filter(m => m.projectId === form.projectId)
  form.moduleId = null
}

const onModuleChange = () => {
  const m = filteredModules.value.find(m => m.id === form.moduleId)
  if (m) {
    form.deployPath = `/opt/apps/${m.moduleName}`
    const project = projects.value.find(p => p.id === m.projectId)
    if (project) {
      form.runtimeType = project.runtimeType || 'java'
      form.runtimeVersion = project.runtimeVersion || '17'
    }
  }
}

const showCreateWizard = () => {
  wizardStep.value = 0
  Object.assign(form, {
    projectId: null, moduleId: null, serverId: null,
    instanceName: '', listenPort: 8080, deployPath: '',
    runtimeType: 'java', runtimeVersion: '17',
    jvmOptions: '', healthCheckPath: '/actuator/health',
    startCommand: ''
  })
  wizardVisible.value = true
}

const saveService = async () => {
  if (!form.moduleId || !form.serverId || !form.instanceName || !form.listenPort || !form.deployPath) {
    ElMessage.warning('请填写必填字段')
    return
  }
  saving.value = true
  try {
    await api.post('/services', {
      instanceName: form.instanceName,
      moduleId: form.moduleId,
      serverId: form.serverId,
      listenPort: form.listenPort,
      deployPath: form.deployPath,
      runtimeType: form.runtimeType,
      runtimeVersion: form.runtimeVersion,
      jvmOptions: form.jvmOptions,
      healthCheckPath: form.healthCheckPath,
      startCommand: form.startCommand,
    })
    ElMessage.success('创建成功，部署目录已初始化')
    wizardVisible.value = false
    fetchData()
  } catch (e) {
    // error already handled by interceptor
  } finally {
    saving.value = false
  }
}

const startService = async (row) => {
  try {
    await api.post(`/services/${row.id}/start`)
    ElMessage.success('启动成功')
    fetchData()
  } catch {}
}

const stopService = async (row) => {
  try {
    await ElMessageBox.confirm(`确认停止服务 ${row.instanceName}？`, '确认', { type: 'warning' })
    await api.post(`/services/${row.id}/stop`)
    ElMessage.success('已停止')
    fetchData()
  } catch {}
}

const restartService = (row) => {
  restartTargetId.value = row.id
  restartServiceName.value = row.instanceName
  restartIsProd.value = false // TODO: determine from server envType
  restartConfirmRef.value.open()
}

const doRestart = async () => {
  try {
    await api.post(`/services/${restartTargetId.value}/restart`)
    ElMessage.success('重启成功')
    fetchData()
  } catch {}
}

const deleteService = async (row) => {
  if (row.processStatus === 1) {
    ElMessage.warning('服务正在运行中，请先停止服务再删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除服务 ${row.instanceName}？此操作不可恢复。`, '确认删除', { type: 'warning' })
    await api.delete(`/services/${row.id}`)
    ElMessage.success('已删除')
    fetchData()
  } catch {}
}

const resetSearch = () => {
  searchProjectId.value = null
  searchModuleId.value = null
  searchStatus.value = null
  fetchData()
}

onMounted(async () => {
  await fetchProjects()
  await Promise.all([fetchModules(), fetchServers()])
  fetchData()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}
.page-header h2 {
  font-size: 20px;
  font-weight: 600;
  color: #111827;
}
.page-desc {
  font-size: 13px;
  color: #6B7280;
  margin-top: 4px;
}
.search-bar {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #E5E7EB;
  padding: 16px;
  margin-bottom: 16px;
}
.table-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #E5E7EB;
  padding: 16px;
}
</style>
