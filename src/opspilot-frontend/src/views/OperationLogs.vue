<template>
  <div>
    <div class="page-header">
      <h2>操作日志</h2>
      <el-button type="primary" @click="exportCsv">
        <el-icon><Download /></el-icon> 导出 CSV
      </el-button>
    </div>

    <div class="card">
      <!-- 多条件筛选 -->
      <el-form inline>
        <el-form-item label="操作类型">
          <el-select v-model="operationFilter" placeholder="全部" clearable style="width: 140px;">
            <el-option label="登录" value="LOGIN" />
            <el-option label="创建" value="CREATE" />
            <el-option label="更新" value="UPDATE" />
            <el-option label="删除" value="DELETE" />
            <el-option label="发版" value="DEPLOY" />
            <el-option label="重启" value="RESTART" />
            <el-option label="回退" value="ROLLBACK" />
            <el-option label="启动" value="START" />
            <el-option label="停止" value="STOP" />
            <el-option label="暂停" value="PAUSE" />
            <el-option label="恢复" value="RESUME" />
            <el-option label="手动触发" value="TRIGGER" />
            <el-option label="系统设置" value="UPDATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标类型">
          <el-select v-model="targetTypeFilter" placeholder="全部" clearable style="width: 140px;">
            <el-option label="服务" value="SERVICE" />
            <el-option label="模块" value="MODULE" />
            <el-option label="项目" value="PROJECT" />
            <el-option label="服务器" value="SERVER" />
            <el-option label="定时任务" value="SCHEDULE" />
            <el-option label="系统设置" value="SETTINGS" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标名称">
          <el-input v-model="targetNameFilter" placeholder="搜索" clearable style="width: 160px;" />
        </el-form-item>
        <el-form-item label="操作结果">
          <el-select v-model="resultFilter" placeholder="全部" clearable style="width: 120px;">
            <el-option label="成功" value="success" />
            <el-option label="失败" value="failed" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 240px;"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="opTypeColor(row.operation)">{{ row.operation }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetType" label="类型" width="100" />
        <el-table-column prop="targetName" label="操作对象" show-overflow-tooltip />
        <el-table-column prop="requestParams" label="详情" show-overflow-tooltip />
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.result === 'failed' ? 'danger' : 'success'" size="small">
              {{ row.result === 'success' ? '成功' : row.result === 'failed' ? '失败' : '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP" width="140" />
        <el-table-column prop="createTime" label="操作时间" width="180" />
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'
import { Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const operationFilter = ref('')
const targetTypeFilter = ref('')
const targetNameFilter = ref('')
const resultFilter = ref('')
const dateRange = ref(null)

const opTypeColor = (op) => {
  if (['CREATE', 'DEPLOY', 'START', 'RESUME', 'TRIGGER'].includes(op)) return 'success'
  if (['DELETE', 'STOP', 'PAUSE'].includes(op)) return 'danger'
  if (['UPDATE', 'RESTART', 'ROLLBACK'].includes(op)) return 'warning'
  return 'info'
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = { pageNum: page.value, pageSize: pageSize.value }
    if (operationFilter.value) params.operation = operationFilter.value
    if (targetTypeFilter.value) params.targetType = targetTypeFilter.value
    if (targetNameFilter.value) params.targetName = targetNameFilter.value
    if (resultFilter.value) params.result = resultFilter.value
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await api.get('/operation-logs', { params })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  operationFilter.value = ''
  targetTypeFilter.value = ''
  targetNameFilter.value = ''
  resultFilter.value = ''
  dateRange.value = null
  fetchData()
}

const exportCsv = () => {
  const params = {}
  if (operationFilter.value) params.operation = operationFilter.value
  if (targetTypeFilter.value) params.targetType = targetTypeFilter.value
  if (targetNameFilter.value) params.targetName = targetNameFilter.value
  if (resultFilter.value) params.result = resultFilter.value
  if (dateRange.value && dateRange.value.length === 2) {
    params.startDate = dateRange.value[0]
    params.endDate = dateRange.value[1]
  }
  const qs = new URLSearchParams(params).toString()
  const token = localStorage.getItem('token')
  window.open(`/api/operation-logs/export${qs ? '?' + qs : ''}`, '_blank')
  ElMessage.success('导出中...')
}

onMounted(fetchData)
</script>
