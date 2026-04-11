<template>
  <div>
    <div class="page-header">
      <h2>Git 认证管理</h2>
      <el-button type="primary" @click="showDialog()">新增认证</el-button>
    </div>

    <div class="card">
      <el-table :data="tableData" v-loading="loading">
        <el-table-column prop="credentialName" label="认证名称" />
        <el-table-column label="认证类型" width="120">
          <template #default="{ row }">
            {{ ['SSH Key', 'PAT Token', '用户名密码'][row.credentialType] || '未知' }}
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" @click="showDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除？" @confirm="deleteItem(row.id)">
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑认证' : '新增认证'" width="500px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="认证名称" required>
          <el-input v-model="form.credentialName" />
        </el-form-item>
        <el-form-item label="认证类型" required>
          <el-select v-model="form.credentialType">
            <el-option label="SSH 私钥" :value="0" />
            <el-option label="Personal Access Token" :value="1" />
            <el-option label="用户名密码" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item :label="form.credentialType === 0 ? '私钥内容' : 'Token/密码'" required>
          <el-input v-model="plainText" type="textarea" :rows="6" :placeholder="form.credentialType === 0 ? '粘贴私钥内容' : '粘贴 Token 值'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import api from '../api'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const plainText = ref('')
const form = reactive({ id: null, credentialName: '', credentialType: 0, username: '', status: 1 })

const fetchData = async () => {
  loading.value = true
  try {
    const res = await api.get('/git-credentials', { params: { pageNum: page.value, pageSize: pageSize.value } })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const showDialog = (row) => {
  isEdit.value = !!row
  if (row) {
    Object.assign(form, { id: row.id, credentialName: row.credentialName, credentialType: row.credentialType, username: row.username, status: row.status })
    plainText.value = '' // Don't show encrypted data
  } else {
    Object.assign(form, { id: null, credentialName: '', credentialType: 0, username: '', status: 1 })
    plainText.value = ''
  }
  dialogVisible.value = true
}

const save = async () => {
  try {
    if (isEdit.value) {
      const data = { ...form }
      if (plainText.value) data.plainText = plainText.value
      await api.put(`/git-credentials/${form.id}`, data)
    } else {
      await api.post('/git-credentials', { ...form, plainText: plainText.value })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    fetchData()
  } catch (e) {}
}

const deleteItem = async (id) => {
  await api.delete(`/git-credentials/${id}`)
  ElMessage.success('已删除')
  fetchData()
}

onMounted(fetchData)
</script>
