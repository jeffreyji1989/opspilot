<template>
  <div>
    <div class="page-header">
      <h2>服务管理</h2>
      <el-button type="primary" @click="showDialog()">创建服务</el-button>
    </div>

    <div class="card">
      <el-form inline>
        <el-form-item label="模块">
          <el-select v-model="moduleId" placeholder="全部" clearable @change="fetchData">
            <el-option v-for="m in modules" :key="m.id" :label="m.moduleName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable @change="fetchData">
            <el-option label="运行中" :value="1" />
            <el-option label="已停止" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading">
        <el-table-column prop="instanceName" label="服务名称" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.processStatus)">{{ statusName(row.processStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="listenPort" label="端口" width="80" />
        <el-table-column prop="deployPath" label="部署路径" show-overflow-tooltip />
        <el-table-column prop="currentVersion" label="当前版本" width="180" />
        <el-table-column prop="runtimeType" label="类型" width="80" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="$router.push(`/services/${row.id}`)">详情</el-button>
            <el-button size="small" type="success" @click="startService(row.id)" :disabled="row.processStatus === 1">启动</el-button>
            <el-button size="small" type="warning" @click="stopService(row.id)" :disabled="row.processStatus !== 1">停止</el-button>
            <el-button size="small" type="danger" @click="deleteService(row.id)">删除</el-button>
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

    <!-- Create Service Dialog -->
    <el-dialog v-model="dialogVisible" title="创建服务" width="600px">
      <el-form :model="form" label-width="130px">
        <el-form-item label="服务名称" required>
          <el-input v-model="form.instanceName" />
        </el-form-item>
        <el-form-item label="所属模块" required>
          <el-select v-model="form.moduleId" @change="onModuleChange">
            <el-option v-for="m in modules" :key="m.id" :label="m.moduleName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标服务器" required>
          <el-select v-model="form.serverId">
            <el-option v-for="s in servers" :key="s.id" :label="`${s.serverName} (${s.hostname})`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务端口" required>
          <el-input-number v-model="form.listenPort" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="部署路径" required>
          <el-input v-model="form.deployPath" placeholder="/opt/apps/service-name" />
        </el-form-item>
        <el-form-item label="运行时类型">
          <el-select v-model="form.runtimeType">
            <el-option label="Java" value="java" />
            <el-option label="Node.js" value="node" />
            <el-option label="Python" value="python" />
          </el-select>
        </el-form-item>
        <el-form-item label="JVM参数">
          <el-input v-model="form.jvmOptions" placeholder="-Xms512m -Xmx1024m" />
        </el-form-item>
        <el-form-item label="健康检查路径">
          <el-input v-model="form.healthCheckPath" placeholder="/actuator/health" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveService">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import api from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const moduleId = ref(null)
const statusFilter = ref(null)
const modules = ref([])
const servers = ref([])

const dialogVisible = ref(false)
const form = reactive({
  instanceName: '', moduleId: null, serverId: null,
  listenPort: 8080, deployPath: '', runtimeType: 'java',
  jvmOptions: '', healthCheckPath: '/actuator/health'
})

const statusType = (s) => s === 1 ? 'success' : 'info'
const statusName = (s) => s === 1 ? '运行中' : '已停止'

const fetchData = async () => {
  loading.value = true
  try {
    const params = { pageNum: page.value, pageSize: pageSize.value }
    if (moduleId.value !== null) params.moduleId = moduleId.value
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
  const projects = res.data.records
  for (const p of projects) {
    const mr = await api.get(`/projects/${p.id}/modules`)
    modules.value.push(...mr.data)
  }
}

const fetchServers = async () => {
  const res = await api.get('/servers?pageNum=1&pageSize=100')
  servers.value = res.data.records
}

const showDialog = () => {
  Object.assign(form, {
    instanceName: '', moduleId: null, serverId: null,
    listenPort: 8080, deployPath: '', runtimeType: 'java',
    jvmOptions: '', healthCheckPath: '/actuator/health'
  })
  dialogVisible.value = true
}

const onModuleChange = () => {
  const m = modules.value.find(m => m.id === form.moduleId)
  if (m) {
    form.deployPath = `/opt/apps/${m.moduleName}`
  }
}

const saveService = async () => {
  try {
    await api.post('/services', form)
    ElMessage.success('创建成功，部署目录已初始化')
    dialogVisible.value = false
    fetchData()
  } catch (e) {}
}

const startService = async (id) => {
  await api.post(`/services/${id}/start`)
  ElMessage.success('启动成功')
  fetchData()
}

const stopService = async (id) => {
  await api.post(`/services/${id}/stop`)
  ElMessage.success('已停止')
  fetchData()
}

const deleteService = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除该服务？', '确认', { type: 'warning' })
    await api.delete(`/services/${id}`)
    ElMessage.success('已删除')
    fetchData()
  } catch {}
}

onMounted(() => {
  fetchData()
  fetchModules()
  fetchServers()
})
</script>
