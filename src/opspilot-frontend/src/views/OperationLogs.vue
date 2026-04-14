<template>
  <div>
    <div class="page-header">
      <div>
        <h2>操作日志</h2>
        <p class="page-desc">查看所有用户的操作行为记录</p>
      </div>
      <el-button @click="exportCsv" :loading="exportLoading">
        <el-icon><Download /></el-icon> 导出 CSV
      </el-button>
    </div>

    <!-- Multi-condition search -->
    <div class="card search-bar">
      <el-form inline>
        <el-form-item label="操作类型">
          <el-select v-model="operationFilter" placeholder="全部" clearable @change="fetchData" style="width: 140px;">
            <el-option label="登录" value="LOGIN" />
            <el-option label="创建" value="CREATE" />
            <el-option label="更新" value="UPDATE" />
            <el-option label="删除" value="DELETE" />
            <el-option label="发版" value="DEPLOY" />
            <el-option label="重启" value="RESTART" />
            <el-option label="回退" value="ROLLBACK" />
            <el-option label="启动" value="START" />
            <el-option label="停止" value="STOP" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作模块">
          <el-select v-model="moduleFilter" placeholder="全部" clearable @change="fetchData" style="width: 140px;">
            <el-option label="用户" value="user" />
            <el-option label="项目" value="project" />
            <el-option label="服务器" value="server" />
            <el-option label="服务" value="service" />
            <el-option label="发版" value="deploy" />
            <el-option label="定时任务" value="schedule" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            @change="fetchData"
            style="width: 240px;"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- Log table -->
    <div class="card table-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="opTypeColor(row.operation)">{{ row.operation }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="80" />
        <el-table-column prop="targetType" label="目标类型" width="100" />
        <el-table-column prop="targetName" label="操作对象" min-width="160" show-overflow-tooltip />
        <el-table-column prop="requestParams" label="详情" min-width="160" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP" width="140" />
        <el-table-column prop="createTime" label="操作时间" width="170" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && tableData.length === 0" description="暂无操作日志" />

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

    <!-- Log Detail Dialog -->
    <el-dialog v-model="detailVisible" title="操作日志详情" width="600px" align-center>
      <el-descriptions :column="1" border size="small" v-if="detailLog">
        <el-descriptions-item label="日志 ID">{{ detailLog.id }}</el-descriptions-item>
        <el-descriptions-item label="操作人 ID">{{ detailLog.userId }}</el-descriptions-item>
        <el-descriptions-item label="操作模块">{{ detailLog.module }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">
          <el-tag size="small" :type="opTypeColor(detailLog.operation)">{{ detailLog.operation }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="目标类型">{{ detailLog.targetType }}</el-descriptions-item>
        <el-descriptions-item label="操作对象">{{ detailLog.targetName }}</el-descriptions-item>
        <el-descriptions-item label="详情">{{ detailLog.requestParams || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP 地址">{{ detailLog.ipAddress }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ detailLog.createTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Download } from '@element-plus/icons-vue'
import api from '../api'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const exportLoading = ref(false)
const tableData = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const operationFilter = ref('')
const moduleFilter = ref('')
const dateRange = ref(null)
const detailVisible = ref(false)
const detailLog = ref(null)

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
    if (moduleFilter.value) params.module = moduleFilter.value
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

const resetSearch = () => {
  operationFilter.value = ''
  moduleFilter.value = ''
  dateRange.value = null
  page.value = 1
  fetchData()
}

const viewDetail = (row) => {
  detailLog.value = row
  detailVisible.value = true
}

const exportCsv = async () => {
  exportLoading.value = true
  try {
    const params = {}
    if (operationFilter.value) params.operation = operationFilter.value
    if (moduleFilter.value) params.module = moduleFilter.value
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await api.get('/operation-logs/export', { params })
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    const date = new Date().toISOString().split('T')[0]
    link.href = url
    link.download = `opspilot-operation-log-${date}.csv`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  } finally {
    exportLoading.value = false
  }
}

onMounted(fetchData)
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
