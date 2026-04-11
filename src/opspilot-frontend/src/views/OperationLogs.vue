<template>
  <div>
    <div class="page-header">
      <h2>操作日志</h2>
    </div>

    <div class="card">
      <el-form inline>
        <el-form-item label="操作类型">
          <el-select v-model="operationFilter" placeholder="全部" clearable @change="fetchData">
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
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="operation" label="操作" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="opTypeColor(row.operation)">{{ row.operation }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetType" label="类型" width="100" />
        <el-table-column prop="targetName" label="操作对象" show-overflow-tooltip />
        <el-table-column prop="requestParams" label="详情" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP" width="140" />
        <el-table-column prop="createTime" label="操作时间" width="170" />
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

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const operationFilter = ref('')

const opTypeColor = (op) => {
  if (['CREATE', 'DEPLOY', 'START', 'RESUME'].includes(op)) return 'success'
  if (['DELETE', 'STOP', 'PAUSE'].includes(op)) return 'danger'
  if (['UPDATE', 'RESTART', 'ROLLBACK'].includes(op)) return 'warning'
  return 'info'
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = { pageNum: page.value, pageSize: pageSize.value }
    if (operationFilter.value) params.operation = operationFilter.value
    const res = await api.get('/operation-logs', { params })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>
