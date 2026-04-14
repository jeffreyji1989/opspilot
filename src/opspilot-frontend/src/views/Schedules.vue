<template>
  <div>
    <div class="page-header">
      <h2>定时发版</h2>
      <el-button type="primary" @click="showDialog()">
        <el-icon><Plus /></el-icon> 创建定时任务
      </el-button>
    </div>

    <div class="card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="scheduleName" label="任务名称" min-width="150" />
        <el-table-column prop="targetBranch" label="目标分支" width="120" />
        <el-table-column label="Cron 表达式" width="160">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.cronExpression }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '运行中' : '已暂停' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="钉钉通知" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.dingtalkEnabled" size="small">已开启</el-tag>
            <span v-else style="color: #909399; font-size: 12px;">未开启</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="editDialog(row)">编辑</el-button>
            <el-button size="small" type="warning" @click="triggerNow(row)" :disabled="row.status !== 1">立即执行</el-button>
            <el-button size="small" :type="row.status === 1 ? 'info' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 1 ? '暂停' : '恢复' }}
            </el-button>
            <el-popconfirm title="确认删除该定时任务？" @confirm="deleteSchedule(row.id)">
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

    <!-- 创建/编辑定时任务 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑定时任务' : '创建定时任务'" width="640px">
      <el-form :model="form" label-width="140px">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.scheduleName" placeholder="例如: 每日凌晨发版" />
        </el-form-item>
        <el-form-item label="目标模块" required>
          <el-select v-model="form.moduleId" placeholder="选择模块" style="width: 100%;">
            <el-option v-for="m in modules" :key="m.id" :label="m.moduleName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标服务实例">
          <el-select v-model="form.instanceIds" placeholder="选择服务实例" multiple style="width: 100%;">
            <el-option v-for="s in services" :key="s.id" :label="s.instanceName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标分支">
          <el-input v-model="form.targetBranch" placeholder="main" />
        </el-form-item>
        <el-form-item label="Cron 表达式" required>
          <el-input v-model="form.cronExpression" placeholder="0 0 2 * * ?">
            <template #append>
              <el-button @click="showCronHelp = true">帮助</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="回退策略">
          <el-select v-model="form.rollbackStrategy" style="width: 100%;">
            <el-option label="失败自动回退" :value="1" />
            <el-option label="失败不回退" :value="0" />
          </el-select>
        </el-form-item>
        <el-divider>钉钉通知</el-divider>
        <el-form-item label="启用通知">
          <el-switch v-model="form.dingtalkEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="Webhook" v-if="form.dingtalkEnabled">
          <el-input v-model="form.dingtalkWebhook" placeholder="https://oapi.dingtalk.com/robot/send?access_token=..." />
        </el-form-item>
        <el-form-item label="成功通知" v-if="form.dingtalkEnabled">
          <el-switch v-model="form.notifyOnSuccess" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="失败通知" v-if="form.dingtalkEnabled">
          <el-switch v-model="form.notifyOnFailure" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>

      <!-- Cron 帮助 -->
      <el-dialog v-model="showCronHelp" title="Cron 表达式帮助" width="500px" append-to-body>
        <pre class="cron-help">
格式: 秒 分 时 日 月 周

常用示例:
0 0 2 * * ?       每天凌晨 2 点
0 0 2 * * MON     每周一凌晨 2 点
0 0 */6 * * ?     每 6 小时
0 */30 * * * ?    每 30 分钟
0 0 0 1 * ?       每月 1 号 0 点

字段说明:
秒: 0-59
分: 0-59
时: 0-23
日: 1-31
月: 1-12 或 JAN-DEC
周: 1-7 或 SUN-SAT

通配符:
*   表示所有值
?   表示不指定值（仅日/周字段）
/   表示增量
,   表示列举
-   表示范围
        </pre>
      </el-dialog>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSchedule" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import api from '../api'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const loading = ref(false)
const saving = ref(false)
const tableData = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const showCronHelp = ref(false)
const modules = ref([])
const services = ref([])

const form = reactive({
  id: null, scheduleName: '', moduleId: null, instanceIds: [],
  targetBranch: 'main', cronExpression: '', timezone: 'Asia/Shanghai',
  rollbackStrategy: 1, healthCheckTimeout: 30, healthCheckInterval: 5,
  healthCheckRetries: 3, dingtalkEnabled: 0, dingtalkWebhook: '',
  dingtalkSecret: '', dingtalkAtUserIds: '',
  notifyOnSuccess: 1, notifyOnFailure: 1, status: 1
})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await api.get('/schedules', { params: { pageNum: page.value, pageSize: pageSize.value } })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const fetchModules = async () => {
  const res = await api.get('/projects?pageNum=1&pageSize=100')
  for (const p of res.data.records) {
    const mr = await api.get(`/projects/${p.id}/modules`)
    modules.value.push(...mr.data)
  }
}

const fetchServices = async () => {
  const res = await api.get('/services?pageNum=1&pageSize=100')
  services.value = res.data.records
}

const showDialog = () => {
  isEdit.value = false
  Object.assign(form, {
    id: null, scheduleName: '', moduleId: null, instanceIds: [],
    targetBranch: 'main', cronExpression: '', timezone: 'Asia/Shanghai',
    rollbackStrategy: 1, healthCheckTimeout: 30, healthCheckInterval: 5,
    healthCheckRetries: 3, dingtalkEnabled: 0, dingtalkWebhook: '',
    dingtalkSecret: '', dingtalkAtUserIds: '',
    notifyOnSuccess: 1, notifyOnFailure: 1, status: 1
  })
  dialogVisible.value = true
}

const editDialog = (row) => {
  isEdit.value = true
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

const saveSchedule = async () => {
  saving.value = true
  try {
    if (isEdit.value) {
      await api.put(`/schedules/${form.id}`, form)
      ElMessage.success('更新成功')
    } else {
      await api.post('/schedules', form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (e) {}
  finally {
    saving.value = false
  }
}

const triggerNow = async (row) => {
  try {
    await api.post(`/schedules/${row.id}/trigger`)
    ElMessage.success('任务已触发')
  } catch (e) {}
}

const toggleStatus = async (row) => {
  try {
    if (row.status === 1) {
      await api.post(`/schedules/${row.id}/pause`)
      ElMessage.success('已暂停')
    } else {
      await api.post(`/schedules/${row.id}/resume`)
      ElMessage.success('已恢复')
    }
    fetchData()
  } catch (e) {}
}

const deleteSchedule = async (id) => {
  try {
    await api.delete(`/schedules/${id}`)
    ElMessage.success('已删除')
    fetchData()
  } catch (e) {}
}

onMounted(() => {
  fetchData()
  fetchModules()
  fetchServices()
})
</script>

<style scoped>
.cron-help {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  white-space: pre-wrap;
  line-height: 1.6;
}
</style>
