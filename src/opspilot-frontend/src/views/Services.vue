<template>
  <div>
    <div class="page-header">
      <h2>服务管理</h2>
      <el-button type="primary" @click="showCreateDialog()">
        <el-icon><Plus /></el-icon> 创建服务
      </el-button>
    </div>

    <div class="card">
      <!-- 筛选 -->
      <el-form inline>
        <el-form-item label="模块">
          <el-select v-model="moduleId" placeholder="全部" clearable @change="fetchData" style="width: 180px;">
            <el-option v-for="m in modules" :key="m.id" :label="m.moduleName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务器">
          <el-select v-model="serverId" placeholder="全部" clearable @change="fetchData" style="width: 180px;">
            <el-option v-for="s in servers" :key="s.id" :label="s.serverName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable @change="fetchData" style="width: 120px;">
            <el-option label="运行中" :value="1" />
            <el-option label="已停止" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <!-- 服务列表 -->
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="instanceName" label="服务名称" min-width="150" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusDot :status="row.processStatus" :pulse="row.processStatus === 1" show-text />
          </template>
        </el-table-column>
        <el-table-column prop="listenPort" label="端口" width="80" align="center" />
        <el-table-column prop="deployPath" label="部署路径" min-width="200" show-overflow-tooltip />
        <el-table-column prop="currentVersion" label="当前版本" width="180" />
        <el-table-column label="运行时" width="120">
          <template #default="{ row }">
            {{ runtimeTypeLabel(row.runtimeType, row.runtimeVersion) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="$router.push(`/services/${row.id}`)">详情</el-button>
            <el-button size="small" @click="showEditDialog(row)">编辑</el-button>
            <el-button size="small" type="success" @click="startService(row.id)" :disabled="row.processStatus === 1">启动</el-button>
            <el-button size="small" type="warning" @click="stopService(row.id)" :disabled="row.processStatus !== 1">停止</el-button>
            <el-popconfirm
              title="确认重启服务？重启期间服务将短暂不可用。"
              confirm-button-text="确认重启"
              cancel-button-text="取消"
              confirm-button-type="warning"
              @confirm="restartService(row.id)"
            >
              <template #reference>
                <el-button size="small" type="warning" :disabled="row.processStatus !== 1">重启</el-button>
              </template>
            </el-popconfirm>
            <el-popconfirm
              :title="row.processStatus === 1 ? '服务正在运行，请先停止后再删除' : '确认删除该服务？此操作不可恢复。'"
              confirm-button-text="确认删除"
              cancel-button-text="取消"
              confirm-button-type="danger"
              :disabled="row.processStatus === 1"
              @confirm="deleteService(row.id)"
            >
              <template #reference>
                <el-button size="small" type="danger" :disabled="row.processStatus === 1">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top: 16px; justify-content: flex-end;"
        :current-page="page"
        :page-size="pageSize"
        :total="total"
        @current-change="page = $event; fetchData()"
        layout="total, prev, pager, next"
      />
    </div>

    <!-- 创建服务分步向导 -->
    <el-dialog v-model="createDialogVisible" title="创建服务" width="640px" :close-on-click-modal="false">
      <el-steps :active="createStep" finish-status="success" style="margin-bottom: 24px;">
        <el-step title="选择模块" />
        <el-step title="选择服务器" />
        <el-step title="配置参数" />
      </el-steps>

      <!-- Step 1: 选择模块 -->
      <div v-show="createStep === 0">
        <el-form label-width="100px">
          <el-form-item label="所属项目">
            <el-select v-model="selectedProjectId" placeholder="选择项目" @change="onProjectChange" style="width: 100%;">
              <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="服务模块">
            <el-select v-model="createForm.moduleId" placeholder="选择模块" style="width: 100%;" @change="onModuleChange">
              <el-option v-for="m in filteredModules" :key="m.id" :label="m.moduleName" :value="m.id" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 2: 选择服务器 -->
      <div v-show="createStep === 1">
        <el-form label-width="100px">
          <el-form-item label="目标服务器">
            <el-select v-model="createForm.serverId" placeholder="选择服务器" style="width: 100%;">
              <el-option v-for="s in servers" :key="s.id" :label="`${s.serverName} (${s.hostname}:${s.port})`" :value="s.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="服务端口">
            <el-input-number v-model="createForm.listenPort" :min="1" :max="65535" style="width: 100%;" />
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 3: 配置参数 -->
      <div v-show="createStep === 2">
        <el-form label-width="120px">
          <el-form-item label="服务名称" required>
            <el-input v-model="createForm.instanceName" placeholder="例如: user-service-prod" />
          </el-form-item>
          <el-form-item label="部署路径" required>
            <el-input v-model="createForm.deployPath" placeholder="/opt/apps/service-name" />
          </el-form-item>
          <el-form-item label="运行时" v-if="createForm.runtimeType">
            <el-tag>{{ runtimeLabel }}</el-tag>
            <span style="margin-left: 8px; color: #909399; font-size: 12px;">（继承自项目配置）</span>
          </el-form-item>
          <el-form-item label="JVM参数" v-if="createForm.runtimeType === 'java'">
            <el-input v-model="createForm.jvmOptions" type="textarea" :rows="2" placeholder="-Xms512m -Xmx1024m -Dspring.profiles.active=prod" />
          </el-form-item>
          <el-form-item label="启动命令" v-if="createForm.runtimeType">
            <el-input v-model="createForm.startCommand" placeholder="留空则默认 java -jar *.jar" />
          </el-form-item>
          <el-form-item label="健康检查路径">
            <el-input v-model="createForm.healthCheckPath" placeholder="/actuator/health" />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button v-if="createStep > 0" @click="createStep--">上一步</el-button>
        <el-button v-if="createStep < 2" type="primary" @click="createStep++" :disabled="!canNext">下一步</el-button>
        <el-button v-if="createStep === 2" type="primary" @click="saveService" :loading="saving">创建服务</el-button>
      </template>
    </el-dialog>

    <!-- 编辑服务 -->
    <el-dialog v-model="editDialogVisible" title="编辑服务" width="600px">
      <el-form :model="editForm" label-width="130px">
        <el-form-item label="服务名称">
          <el-input v-model="editForm.instanceName" />
        </el-form-item>
        <el-form-item label="服务端口">
          <el-input-number v-model="editForm.listenPort" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="部署路径">
          <el-input v-model="editForm.deployPath" />
        </el-form-item>
        <el-form-item label="JVM参数">
          <el-input v-model="editForm.jvmOptions" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="启动命令">
          <el-input v-model="editForm.startCommand" />
        </el-form-item>
        <el-form-item label="健康检查路径">
          <el-input v-model="editForm.healthCheckPath" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重启二次确认 -->
    <EnvConfirmDialog
      v-model="restartDialogVisible"
      title="确认重启"
      message="确认重启服务？"
      warning="重启期间服务将短暂不可用，请确认当前无活跃请求。"
      confirm-text="确认重启"
      confirm-button-type="warning"
      :loading="restarting"
      @confirm="doRestart"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import api from '../api'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import StatusDot from '../components/StatusDot.vue'
import EnvConfirmDialog from '../components/EnvConfirmDialog.vue'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const moduleId = ref(null)
const serverId = ref(null)
const statusFilter = ref(null)
const modules = ref([])
const servers = ref([])
const projects = ref([])

// Create dialog
const createDialogVisible = ref(false)
const createStep = ref(0)
const saving = ref(false)
const selectedProjectId = ref(null)
const createForm = reactive({
  instanceName: '', moduleId: null, serverId: null,
  listenPort: 8080, deployPath: '', runtimeType: 'java',
  runtimeVersion: '17', jvmOptions: '', startCommand: '',
  healthCheckPath: '/actuator/health'
})

// Edit dialog
const editDialogVisible = ref(false)
const editForm = reactive({})

// Restart confirm
const restartDialogVisible = ref(false)
const restartId = ref(null)
const restarting = ref(false)

const filteredModules = computed(() => {
  if (!selectedProjectId.value) return modules.value
  return modules.value.filter(m => m.projectId === selectedProjectId.value)
})

const canNext = computed(() => {
  if (createStep.value === 0) return createForm.moduleId
  if (createStep.value === 1) return createForm.serverId
  return true
})

const statusType = (s) => s === 1 ? 'success' : 'info'
const statusName = (s) => s === 1 ? '运行中' : '已停止'
const runtimeTypeLabel = (type, version) => {
  const typeMap = { java: 'Java', node: 'Node.js', python: 'Python' }
  return version ? `${typeMap[type] || type} ${version}` : (typeMap[type] || type)
}
const runtimeLabel = computed(() => {
  const typeMap = { java: 'Java', node: 'Node.js', python: 'Python' }
  const type = typeMap[createForm.runtimeType] || createForm.runtimeType
  return createForm.runtimeVersion ? `${type} ${createForm.runtimeVersion}` : type
})

const fetchData = async () => {
  loading.value = true
  try {
    const params = { pageNum: page.value, pageSize: pageSize.value }
    if (moduleId.value !== null) params.moduleId = moduleId.value
    if (serverId.value !== null) params.serverId = serverId.value
    if (statusFilter.value !== null) params.status = statusFilter.value
    const res = await api.get('/services', { params })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const fetchModules = async () => {
  const res = await api.get('/projects?pageNum=1&pageSize=100')
  projects.value = res.data.records
  for (const p of projects.value) {
    const mr = await api.get(`/projects/${p.id}/modules`)
    modules.value.push(...mr.data)
  }
}

const fetchServers = async () => {
  const res = await api.get('/servers?pageNum=1&pageSize=100')
  servers.value = res.data.records
}

const showCreateDialog = () => {
  createStep.value = 0
  selectedProjectId.value = null
  Object.assign(createForm, {
    instanceName: '', moduleId: null, serverId: null,
    listenPort: 8080, deployPath: '', runtimeType: '', runtimeVersion: '',
    jvmOptions: '', startCommand: '', healthCheckPath: '/actuator/health'
  })
  createDialogVisible.value = true
}

const showEditDialog = (row) => {
  Object.assign(editForm, { ...row })
  editDialogVisible.value = true
}

const saveEdit = async () => {
  try {
    await api.put(`/services/${editForm.id}`, editForm)
    ElMessage.success('保存成功')
    editDialogVisible.value = false
    fetchData()
  } catch (e) {}
}

const onProjectChange = () => {
  createForm.moduleId = null
}

const onModuleChange = () => {
  const m = modules.value.find(m => m.id === createForm.moduleId)
  if (m) {
    createForm.deployPath = `/opt/apps/${m.moduleName}`
    const project = projects.value.find(p => p.id === m.projectId)
    if (project) {
      createForm.runtimeType = project.runtimeType || 'java'
      createForm.runtimeVersion = project.runtimeVersion || '17'
    }
    // Auto-generate instance name
    if (!createForm.instanceName) {
      createForm.instanceName = `${m.moduleName}-${m.environment || 'prod'}`
    }
  }
}

const saveService = async () => {
  saving.value = true
  try {
    await api.post('/services', createForm)
    ElMessage.success('创建成功，部署目录已初始化')
    createDialogVisible.value = false
    fetchData()
  } catch (e) {}
  finally {
    saving.value = false
  }
}

const startService = async (id) => {
  try {
    await api.post(`/services/${id}/start`)
    ElMessage.success('启动成功')
    fetchData()
  } catch (e) {}
}

const stopService = async (id) => {
  try {
    await api.post(`/services/${id}/stop`)
    ElMessage.success('已停止')
    fetchData()
  } catch (e) {}
}

const restartService = (id) => {
  restartId.value = id
  restartDialogVisible.value = true
}

const doRestart = async () => {
  restarting.value = true
  try {
    await api.post(`/services/${restartId.value}/restart`)
    ElMessage.success('重启成功')
    restartDialogVisible.value = false
    fetchData()
  } catch (e) {}
  finally {
    restarting.value = false
  }
}

const deleteService = async (id) => {
  try {
    await api.delete(`/services/${id}`)
    ElMessage.success('已删除')
    fetchData()
  } catch (e) {}
}

onMounted(() => {
  fetchData()
  fetchModules()
  fetchServers()
})
</script>
