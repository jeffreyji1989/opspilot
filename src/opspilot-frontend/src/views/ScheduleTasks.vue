<template>
  <div>
    <div class="page-header">
      <div>
        <h2>定时任务</h2>
        <p class="page-desc">管理定时发版任务，支持单次和 Cron 表达式触发</p>
      </div>
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon> 创建任务
      </el-button>
    </div>

    <div class="card table-card">
      <el-form inline>
        <el-form-item label="模块">
          <el-select v-model="moduleIdFilter" placeholder="全部" clearable @change="fetchData" style="width: 160px;">
            <el-option v-for="m in modules" :key="m.id" :label="m.moduleName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="scheduleName" label="任务名称" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="toggleStatus(row)"
              :loading="row._toggling"
            />
          </template>
        </el-table-column>
        <el-table-column prop="cronExpression" label="Cron 表达式" width="140" />
        <el-table-column prop="targetBranch" label="目标分支" width="100" />
        <el-table-column label="回退策略" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.rollbackStrategy === 0 ? 'info' : 'warning'">
              {{ rollbackStrategyLabel(row.rollbackStrategy) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="钉钉通知" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.dingtalkEnabled ? 'success' : 'info'">
              {{ row.dingtalkEnabled ? '开' : '关' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="editSchedule(row)">编辑</el-button>
            <el-button size="small" type="warning" @click="triggerNow(row)">立即执行</el-button>
            <el-button size="small" type="danger" @click="deleteSchedule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && tableData.length === 0" description="暂无定时任务">
        <el-button type="primary" @click="showCreateDialog">创建任务</el-button>
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

    <!-- Cron help -->
    <div class="card cron-help">
      <h4>💡 Cron 表达式示例</h4>
      <ul>
        <li><code>0 2 * * *</code> — 每天凌晨 2 点</li>
        <li><code>0 */6 * * *</code> — 每 6 小时执行一次</li>
        <li><code>0 10 * * 1</code> — 每周一上午 10 点</li>
        <li><code>0 0 1 * *</code> — 每月 1 日零点</li>
      </ul>
      <p class="help-note">格式: 分 时 日 月 星期</p>
    </div>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑定时任务' : '创建定时任务'" width="600px" align-center>
      <el-form :model="form" label-width="120px">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.scheduleName" placeholder="如：每晚自动发版" />
        </el-form-item>
        <el-form-item label="目标模块" required>
          <el-select v-model="form.moduleId" placeholder="选择模块" style="width: 100%;" filterable>
            <el-option v-for="m in modules" :key="m.id" :label="m.moduleName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标分支" required>
          <el-input v-model="form.targetBranch" placeholder="main" />
        </el-form-item>
        <el-form-item label="触发方式" required>
          <el-radio-group v-model="triggerType">
            <el-radio label="cron">Cron 表达式</el-radio>
            <el-radio label="once">单次执行</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="Cron 表达式" v-if="triggerType === 'cron'" required>
          <el-input v-model="form.cronExpression" placeholder="0 2 * * *" />
        </el-form-item>
        <el-form-item label="执行时间" v-if="triggerType === 'once'" required>
          <el-date-picker v-model="form.onceTime" type="datetime" placeholder="选择执行时间" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="失败回退策略">
          <el-select v-model="form.rollbackStrategy" style="width: 100%;">
            <el-option label="不自动回退" :value="0" />
            <el-option label="失败自动回退到上一版本" :value="1" />
            <el-option label="健康检查失败自动回退" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="钉钉通知">
          <el-switch v-model="form.dingtalkEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSchedule" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import api from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const saving = ref(false)
const tableData = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const moduleIdFilter = ref(null)
const modules = ref([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const triggerType = ref('cron')
const form = reactive({
  scheduleName: '', moduleId: null, targetBranch: 'main',
  cronExpression: '', onceTime: null,
  rollbackStrategy: 0, dingtalkEnabled: 1,
})

const rollbackStrategyLabel = (s) => {
  const map = { 0: '不回退', 1: '自动回退', 2: '健康检查回退' }
  return map[s] || '不回退'
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = { pageNum: page.value, pageSize: pageSize.value }
    if (moduleIdFilter.value) params.moduleId = moduleIdFilter.value
    const res = await api.get('/schedules', { params })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const fetchModules = async () => {
  try {
    const res = await api.get('/projects?pageNum=1&pageSize=100')
    for (const p of res.data.records) {
      const mr = await api.get(`/projects/${p.id}/modules`)
      modules.value.push(...(mr.data || []))
    }
  } catch {}
}

const showCreateDialog = () => {
  isEdit.value = false
  editId.value = null
  triggerType.value = 'cron'
  Object.assign(form, {
    scheduleName: '', moduleId: null, targetBranch: 'main',
    cronExpression: '', onceTime: null,
    rollbackStrategy: 0, dingtalkEnabled: 1,
  })
  dialogVisible.value = true
}

const editSchedule = (row) => {
  isEdit.value = true
  editId.value = row.id
  triggerType.value = 'cron'
  Object.assign(form, {
    scheduleName: row.scheduleName,
    moduleId: row.moduleId,
    targetBranch: row.targetBranch || 'main',
    cronExpression: row.cronExpression,
    rollbackStrategy: row.rollbackStrategy,
    dingtalkEnabled: row.dingtalkEnabled,
  })
  dialogVisible.value = true
}

const saveSchedule = async () => {
  if (!form.scheduleName || !form.moduleId) {
    ElMessage.warning('请填写必填字段')
    return
  }
  saving.value = true
  try {
    const data = { ...form }
    if (triggerType.value === 'once' && form.onceTime) {
      // Convert datetime to cron-like expression
      const d = new Date(form.onceTime)
      data.cronExpression = `${d.getMinutes()} ${d.getHours()} ${d.getDate()} ${d.getMonth() + 1} *`
    }
    if (isEdit.value) {
      await api.put(`/schedules/${editId.value}`, data)
      ElMessage.success('更新成功')
    } else {
      await api.post('/schedules', data)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (row) => {
  row._toggling = true
  try {
    if (row.status === 1) {
      await api.post(`/schedules/${row.id}/resume`)
    } else {
      await api.post(`/schedules/${row.id}/pause`)
    }
    ElMessage.success(row.status === 1 ? '已恢复' : '已暂停')
  } catch {
    row.status = row.status === 1 ? 0 : 1
  } finally {
    row._toggling = false
  }
}

const triggerNow = async (row) => {
  try {
    await ElMessageBox.confirm(`确认立即执行任务 "${row.scheduleName}"？`, '确认', { type: 'warning' })
    await api.post(`/schedules/${row.id}/trigger`)
    ElMessage.success('任务已加入执行队列')
  } catch {}
}

const deleteSchedule = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除任务 "${row.scheduleName}"？`, '确认删除', { type: 'warning' })
    await api.delete(`/schedules/${row.id}`)
    ElMessage.success('已删除')
    fetchData()
  } catch {}
}

onMounted(async () => {
  await fetchModules()
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
.table-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #E5E7EB;
  padding: 16px;
  margin-bottom: 16px;
}
.cron-help {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #E5E7EB;
  padding: 16px 24px;
}
.cron-help h4 {
  font-size: 14px;
  font-weight: 600;
  color: #1F2937;
  margin-bottom: 8px;
}
.cron-help ul {
  list-style: none;
  padding: 0;
  margin: 0;
}
.cron-help li {
  font-size: 13px;
  color: #374151;
  padding: 4px 0;
}
.cron-help code {
  background: #F3F4F6;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Fira Code', monospace;
  font-size: 12px;
}
.help-note {
  font-size: 12px;
  color: #9CA3AF;
  margin-top: 8px;
}
</style>
