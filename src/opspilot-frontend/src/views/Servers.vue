<template>
  <div class="page-container" v-loading="pageLoading">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h1>服务器管理</h1>
        <p>管理所有服务器，按环境分组展示，自动探测服务器环境信息。</p>
      </div>
      <el-button type="primary" @click="showDialog">
        <el-icon><Plus /></el-icon> 添加服务器
      </el-button>
    </div>

    <!-- 正常状态 -->
    <template v-if="!error">
      <!-- 按环境分组 -->
      <div v-for="env in envList" :key="env.type" class="env-group">
        <div class="env-group-header" :class="{ 'prod-header': env.type === 3 }">
          <span class="env-dot" :style="{ background: env.color }"></span>
          <span class="env-name">{{ env.name }} ({{ env.label }})</span>
          <span class="count">— {{ (serversByEnv[env.type] || []).length }} 台</span>
        </div>

        <!-- 生产环境警告 -->
        <div v-if="env.type === 3 && (serversByEnv[env.type] || []).length > 0" class="prod-warning">
          <el-icon><WarningFilled /></el-icon>
          <span>生产环境服务器操作具有高风险，请谨慎操作。</span>
        </div>

        <!-- 服务器卡片 -->
        <div v-if="(serversByEnv[env.type] || []).length > 0" class="server-cards">
          <div v-for="server in serversByEnv[env.type]" :key="server.id" class="server-card" :class="{ 'prod-card': server.envType === 3 }">
            <div class="server-card-header">
              <div>
                <div class="server-name">{{ server.serverName }}</div>
                <div class="server-ip">{{ server.hostname }}:{{ server.port || 22 }}</div>
              </div>
              <div class="status-badge">
                <span class="status-dot" :class="{ online: server.status === 1, offline: server.status !== 1 }"></span>
                <span>{{ server.status === 1 ? '在线' : '离线' }}</span>
              </div>
            </div>
            <div class="server-card-info">
              <div class="info-item">操作系统: <span>{{ server.osType || '-' }}</span></div>
              <div class="info-item">CPU: <span>{{ server.cpuCores ? server.cpuCores + '核' : '-' }}</span></div>
              <div class="info-item">内存: <span>{{ server.memoryMb ? (server.memoryMb / 1024).toFixed(1) + 'GB' : '-' }}</span></div>
              <div class="info-item">磁盘: <span>{{ server.diskTotalGb ? server.diskTotalGb + 'GB' : '-' }}</span></div>
              <div class="info-item">SSH 用户名: <span>{{ server.sshUsername || '-' }}</span></div>
              <div class="info-item">SSH 互信: <span :style="{ color: server.sshKeyStatus === 1 ? '#10B981' : server.sshKeyStatus === 2 ? '#EF4444' : '#9CA3AF' }">
                {{ server.sshKeyStatus === 1 ? '已配置' : server.sshKeyStatus === 2 ? '失败' : '未配置' }}
              </span></div>
            </div>
            <div class="server-card-footer">
              <el-button size="small" @click="detectEnv(server)">
                <el-icon><Refresh /></el-icon> 探测
              </el-button>
              <el-button size="small" @click="showDialog(server)">
                <el-icon><Edit /></el-icon> 编辑
              </el-button>
              <el-popconfirm title="确认删除服务器？" @confirm="deleteServer(server.id)">
                <template #reference>
                  <el-button size="small" type="danger">
                    <el-icon><Delete /></el-icon> 删除
                  </el-button>
                </template>
              </el-popconfirm>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else class="empty-group">
          <span>暂无{{ env.name }}服务器</span>
        </div>
      </div>
    </template>

    <!-- 错误状态 -->
    <div v-if="error" class="card">
      <el-alert title="加载服务器列表失败" type="error" :closable="false" show-icon style="margin-bottom: 16px">
        <template #default>网络连接超时，请检查网络后重试。</template>
      </el-alert>
      <el-button type="primary" @click="error = false; fetchData()">
        <el-icon><Refresh /></el-icon> 重新加载
      </el-button>
    </div>

    <!-- 添加/编辑服务器对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑服务器' : '添加服务器'" width="500px" destroy-on-close>
      <el-form :model="form" label-width="100px" label-position="right">
        <el-form-item label="主机名" required>
          <el-input v-model="form.serverName" placeholder="如：prod-order-01" />
        </el-form-item>
        <el-form-item label="IP 地址" required>
          <el-input v-model="form.hostname" placeholder="如：192.168.1.100" />
        </el-form-item>
        <el-form-item label="SSH 端口">
          <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>
        <el-form-item label="SSH 用户名" required>
          <el-input v-model="form.sshUsername" placeholder="root" />
        </el-form-item>
        <el-form-item label="SSH 密码" v-if="!isEdit" required>
          <el-input v-model="sshPassword" type="password" show-password placeholder="用于建立 SSH 互信，密码不入库" />
        </el-form-item>
        <el-form-item label="环境类型" required>
          <el-select v-model="form.envType" style="width: 100%">
            <el-option label="开发 (dev)" :value="0" />
            <el-option label="测试 (test)" :value="1" />
            <el-option label="预发 (staging)" :value="2" />
            <el-option label="生产 (prod)" :value="3" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveServer">
          {{ isEdit ? '保存' : '添加并建立互信' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus, Edit, Delete, Refresh, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const pageLoading = ref(false)
const error = ref(false)
const serversByEnv = ref({ 0: [], 1: [], 2: [], 3: [] })

const dialogVisible = ref(false)
const isEdit = ref(false)
const form = reactive({ id: null, serverName: '', hostname: '', port: 22, sshUsername: '', envType: 0 })
const sshPassword = ref('')

const envList = [
  { type: 0, name: '开发', label: 'dev', color: '#3B82F6' },
  { type: 1, name: '测试', label: 'test', color: '#F59E0B' },
  { type: 2, name: '预发', label: 'staging', color: '#8B5CF6' },
  { type: 3, name: '生产', label: 'prod', color: '#EF4444' }
]

const fetchData = async () => {
  pageLoading.value = true
  error.value = false
  try {
    const res = await api.get('/servers/by-env')
    serversByEnv.value = res.data || { 0: [], 1: [], 2: [], 3: [] }
  } catch (e) {
    error.value = true
  } finally {
    pageLoading.value = false
  }
}

const showDialog = (row) => {
  isEdit.value = !!row
  if (row) {
    Object.assign(form, { ...row })
  } else {
    Object.assign(form, { id: null, serverName: '', hostname: '', port: 22, sshUsername: '', envType: 0 })
    sshPassword.value = ''
  }
  dialogVisible.value = true
}

const saveServer = async () => {
  // 生产环境二次确认
  if (!isEdit.value && form.envType === 3) {
    try {
      await ElMessageBox.confirm(
        `您正在添加生产环境服务器\n\n主机名: ${form.serverName}\nIP 地址: ${form.hostname}\n环境: 生产 (prod)\n\n生产环境操作具有高风险，请确认信息无误。`,
        '⚠️ 生产环境操作确认',
        { type: 'warning', confirmButtonText: '我确认，继续添加', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }

  try {
    if (isEdit.value) {
      await api.put(`/servers/${form.id}`, form)
    } else {
      await api.post('/servers', { server: form, sshPassword: sshPassword.value })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    fetchData()
  } catch (e) {
    // Error handled by interceptor
  }
}

const deleteServer = async (id) => {
  try {
    await api.delete(`/servers/${id}`)
    ElMessage.success('已删除')
    fetchData()
  } catch (e) {
    // Error handled by interceptor
  }
}

const detectEnv = async (row) => {
  try {
    await api.post(`/servers/${row.id}/detect`)
    ElMessage.success('探测完成')
    fetchData()
  } catch (e) {
    // Error handled by interceptor
  }
}

onMounted(fetchData)
</script>

<style scoped>
.page-container {
  padding: 24px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
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
.env-group {
  margin-bottom: 24px;
}
.env-group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 600;
}
.env-group-header.prod-header {
  color: #EF4444;
}
.env-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}
.env-name {
  font-weight: 600;
}
.count {
  font-size: 13px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}
.prod-warning {
  border: 1px solid #EF4444;
  border-radius: 8px;
  padding: 12px 16px;
  background: #FEF2F2;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #DC2626;
}
.server-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 16px;
}
.server-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  padding: 20px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  transition: box-shadow 0.15s;
}
.server-card:hover {
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}
.server-card.prod-card {
  border-left: 3px solid #EF4444;
}
.server-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}
.server-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.server-ip {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  font-family: monospace;
  margin-top: 2px;
}
.status-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.status-dot.online {
  background: #10B981;
}
.status-dot.offline {
  background: #9CA3AF;
}
.server-card-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 16px;
}
.info-item {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.info-item span {
  color: var(--el-text-color-primary);
  font-weight: 500;
}
.server-card-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 12px;
}
.empty-group {
  text-align: center;
  padding: 24px;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
}
.card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  padding: 24px;
}
</style>
