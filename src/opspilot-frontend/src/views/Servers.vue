<template>
  <div>
    <div class="page-header">
      <h2>服务器管理</h2>
      <el-button type="primary" @click="showDialog()">添加服务器</el-button>
    </div>

    <div class="card">
      <el-form inline>
        <el-form-item label="环境">
          <el-select v-model="envFilter" placeholder="全部" clearable @change="fetchData">
            <el-option label="开发" :value="0" />
            <el-option label="测试" :value="1" />
            <el-option label="预发" :value="2" />
            <el-option label="生产" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="搜索">
          <el-input v-model="keyword" placeholder="名称/IP" clearable @clear="fetchData" @keyup.enter="fetchData" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading">
        <el-table-column prop="serverName" label="主机名" />
        <el-table-column prop="hostname" label="IP地址" />
        <el-table-column prop="port" label="SSH端口" width="80" />
        <el-table-column label="环境" width="80">
          <template #default="{ row }">
            <el-tag :type="envTypeColor(row.envType)">{{ envTypeName(row.envType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '在线' : '离线' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="osType" label="操作系统" />
        <el-table-column label="SSH互信" width="90">
          <template #default="{ row }">
            <el-tag :type="row.sshKeyStatus === 1 ? 'success' : row.sshKeyStatus === 2 ? 'danger' : 'info'" size="small">
              {{ row.sshKeyStatus === 1 ? '已配置' : row.sshKeyStatus === 2 ? '失败' : '未配置' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="detectEnv(row)">探测</el-button>
            <el-button size="small" @click="showDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除？" @confirm="deleteServer(row.id)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑服务器' : '添加服务器'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="主机名" required>
          <el-input v-model="form.serverName" />
        </el-form-item>
        <el-form-item label="IP地址" required>
          <el-input v-model="form.hostname" />
        </el-form-item>
        <el-form-item label="SSH端口">
          <el-input-number v-model="form.port" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="SSH用户名" required>
          <el-input v-model="form.sshUsername" placeholder="root" />
        </el-form-item>
        <el-form-item label="SSH密码" v-if="!isEdit" required>
          <el-input v-model="sshPassword" type="password" show-password placeholder="用于建立SSH互信" />
        </el-form-item>
        <el-form-item label="环境类型" required>
          <el-select v-model="form.envType">
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
import api from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const envFilter = ref(null)
const keyword = ref('')

const dialogVisible = ref(false)
const isEdit = ref(false)
const form = reactive({ id: null, serverName: '', hostname: '', port: 22, envType: 0 })
const sshPassword = ref('')

const envTypeName = (type) => ['开发', '测试', '预发', '生产'][type] || '未知'
const envTypeColor = (type) => ['', 'success', 'warning', 'danger'][type] || 'info'

const fetchData = async () => {
  loading.value = true
  try {
    const params = { pageNum: page.value, pageSize: pageSize.value }
    if (envFilter.value !== null) params.envType = envFilter.value
    if (keyword.value) params.keyword = keyword.value
    const res = await api.get('/servers', { params })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const showDialog = (row) => {
  isEdit.value = !!row
  if (row) {
    Object.assign(form, row)
  } else {
    Object.assign(form, { id: null, serverName: '', hostname: '', port: 22, envType: 0 })
    sshPassword.value = ''
  }
  dialogVisible.value = true
}

const saveServer = async () => {
  if (form.envType === 3) {
    try {
      await ElMessageBox.confirm(
        `您正在添加生产环境服务器\n\n主机名: ${form.serverName}\nIP: ${form.hostname}\n\n生产环境操作具有高风险，请确认信息无误。`,
        '⚠️ 生产环境操作确认',
        { type: 'warning', confirmButtonText: '我确认，继续添加', cancelButtonText: '取消' }
      )
    } catch { return }
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
  } catch (e) {}
}

const deleteServer = async (id) => {
  await api.delete(`/servers/${id}`)
  ElMessage.success('已删除')
  fetchData()
}

const detectEnv = async (row) => {
  try {
    await api.post(`/servers/${row.id}/detect`)
    ElMessage.success('探测完成')
    fetchData()
  } catch (e) {}
}

onMounted(fetchData)
</script>
