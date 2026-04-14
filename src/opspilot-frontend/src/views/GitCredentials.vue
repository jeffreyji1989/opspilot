<template>
  <div class="page-container" v-loading="pageLoading">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h1>Git 认证管理</h1>
        <p>统一管理所有 Git 仓库的认证信息（SSH Key / HTTPS Token），创建模块时直接关联。</p>
      </div>
      <el-button type="primary" @click="showDialog">
        <el-icon><Plus /></el-icon> 添加认证
      </el-button>
    </div>

    <!-- 安全提示 -->
    <div class="security-notice">
      <el-icon><Lock /></el-icon>
      <div><strong>安全提示：</strong>私钥内容和 Token 值在数据库中 AES-256-GCM 加密存储，列表页不展示敏感信息。</div>
    </div>

    <!-- 正常状态 -->
    <template v-if="!error">
      <div class="card">
        <el-table :data="tableData" stripe border style="width: 100%">
          <el-table-column prop="credentialName" label="认证名称" min-width="200">
            <template #default="{ row }">
              <span style="font-weight: 500; color: var(--el-text-color-primary)">
                <el-icon><Key /></el-icon> {{ row.credentialName }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="认证类型" width="140">
            <template #default="{ row }">
              <el-tag :type="row.credentialType === 0 ? '' : 'warning'" size="small">
                {{ ['SSH Key', 'PAT Token', '用户名密码'][row.credentialType] || '未知' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="domain" label="适用域名" width="160">
            <template #default="{ row }">
              {{ row.domain || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="moduleCount" label="关联模块数" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.moduleCount || 0 }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="170" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="showDialog(row)">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-popconfirm
                :title="`确认删除认证「${row.credentialName}」？`"
                @confirm="deleteItem(row.id)"
              >
                <template #reference>
                  <el-button text type="danger" size="small" :disabled="(row.moduleCount || 0) > 0" :title="(row.moduleCount || 0) > 0 ? '已被模块关联，无法删除' : ''">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="pagination-wrapper">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :current-page="page"
            :page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50]"
            @current-change="page = $event; fetchData()"
            @size-change="pageSize = $event; page = 1; fetchData()"
          />
        </div>
      </div>

      <!-- 空数据 -->
      <div v-if="tableData.length === 0 && !loading" class="card" style="margin-top: 16px">
        <el-empty description="暂无认证信息">
          <el-button type="primary" @click="showDialog">添加认证</el-button>
        </el-empty>
      </div>
    </template>

    <!-- 错误状态 -->
    <div v-if="error" class="card" style="margin-top: 16px">
      <el-alert title="加载认证列表失败" type="error" :closable="false" show-icon style="margin-bottom: 16px">
        <template #default>网络连接超时，请检查网络后重试。</template>
      </el-alert>
      <el-button type="primary" @click="error = false; fetchData()">
        <el-icon><Refresh /></el-icon> 重新加载
      </el-button>
    </div>

    <!-- 添加/编辑认证对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑认证' : '添加认证'" width="560px" destroy-on-close>
      <!-- 编辑时安全提示 -->
      <el-alert v-if="isEdit" title="编辑时留空则保留原值" type="info" :closable="false" style="margin-bottom: 16px" />

      <el-form :model="form" label-width="120px" label-position="right">
        <el-form-item label="认证名称" required>
          <el-input v-model="form.credentialName" placeholder="如：github-personal-key" />
        </el-form-item>
        <el-form-item label="认证类型" required>
          <el-select v-model="form.credentialType" style="width: 100%">
            <el-option label="SSH 私钥" :value="0" />
            <el-option label="Personal Access Token" :value="1" />
            <el-option label="用户名密码" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="可选" />
        </el-form-item>
        <el-form-item label="适用域名">
          <el-input v-model="form.domain" placeholder="如：github.com" />
        </el-form-item>
        <el-form-item :label="form.credentialType === 0 ? '私钥内容' : 'Token/密码'" :required="!isEdit">
          <el-input
            v-model="plainText"
            type="textarea"
            :rows="6"
            :placeholder="form.credentialType === 0 ? '粘贴私钥内容' : '粘贴 Token 值'"
          />
          <template #extra v-if="isEdit">
            <span style="color: var(--el-text-color-placeholder); font-size: 12px">留空则保留原值</span>
          </template>
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
import { Plus, Edit, Delete, Refresh, Lock, Key } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const pageLoading = ref(false)
const loading = ref(false)
const error = ref(false)
const tableData = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const isEdit = ref(false)
const plainText = ref('')
const form = reactive({ id: null, credentialName: '', credentialType: 0, username: '', domain: '', status: 1 })

const fetchData = async () => {
  loading.value = true
  error.value = false
  try {
    const res = await api.get('/git-credentials', { params: { pageNum: page.value, pageSize: pageSize.value } })
    tableData.value = res.data.records
    total.value = res.data.total
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
}

const showDialog = (row) => {
  isEdit.value = !!row
  if (row) {
    Object.assign(form, {
      id: row.id,
      credentialName: row.credentialName,
      credentialType: row.credentialType,
      username: row.username,
      domain: row.domain || '',
      status: row.status
    })
    plainText.value = '' // 不展示加密数据
  } else {
    Object.assign(form, { id: null, credentialName: '', credentialType: 0, username: '', domain: '', status: 1 })
    plainText.value = ''
  }
  dialogVisible.value = true
}

const save = async () => {
  try {
    if (isEdit.value) {
      const data = { credentialName: form.credentialName, username: form.username, domain: form.domain, status: form.status }
      if (plainText.value) data.plainText = plainText.value
      await api.put(`/git-credentials/${form.id}`, data)
    } else {
      await api.post('/git-credentials', { ...form, plainText: plainText.value })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    fetchData()
  } catch (e) {
    // Error handled by interceptor
  }
}

const deleteItem = async (id) => {
  try {
    await api.delete(`/git-credentials/${id}`)
    ElMessage.success('已删除')
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
.security-notice {
  background: #FFFBEB;
  border: 1px solid #F59E0B;
  border-radius: 8px;
  padding: 12px 16px;
  font-size: 13px;
  color: #92400E;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  padding: 24px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
