<template>
  <el-dialog
    v-model="visible"
    title="发版部署进度"
    width="640px"
    :close-on-click-modal="false"
    :show-close="isComplete"
    @close="handleClose"
  >
    <div class="deploy-progress">
      <!-- 7 步进度条 -->
      <div class="steps-container">
        <div v-for="(step, index) in steps" :key="index" class="step-item">
          <div class="step-icon" :class="stepClass(step.status)">
            <el-icon v-if="step.status === 0"><Loading /></el-icon>
            <el-icon v-else-if="step.status === 1"><CircleCheckFilled /></el-icon>
            <el-icon v-else-if="step.status === 2"><CircleCloseFilled /></el-icon>
            <span v-else>{{ index + 1 }}</span>
          </div>
          <div class="step-label">{{ step.name }}</div>
          <div v-if="index < steps.length - 1" class="step-line" :class="{ completed: isStepCompleted(index) }"></div>
        </div>
      </div>

      <!-- 当前状态信息 -->
      <div class="status-info">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="版本号">{{ record?.version || '-' }}</el-descriptions-item>
          <el-descriptions-item label="分支">{{ record?.gitBranch || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType" size="small">{{ statusLabel }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">
            {{ durationText }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 错误信息 -->
      <el-alert
        v-if="record?.errorMessage"
        :title="record.errorMessage"
        type="error"
        :closable="false"
        show-icon
        style="margin-top: 12px;"
      />

      <!-- 操作按钮 -->
      <div class="action-bar" v-if="isComplete">
        <el-button type="primary" @click="handleClose">完成</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Loading, CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  record: {
    type: Object,
    default: () => ({})
  },
  steps: {
    type: Array,
    default: () => []
  },
  polling: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'close'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const defaultSteps = [
  { name: '拉取代码', status: 0 },
  { name: '编译构建', status: 0 },
  { name: '打包产物', status: 0 },
  { name: '上传服务器', status: 0 },
  { name: '切换版本', status: 0 },
  { name: '重启服务', status: 0 },
  { name: '健康检查', status: 0 }
]

const displaySteps = computed(() => {
  if (props.steps.length > 0) return props.steps
  return defaultSteps
})

const isComplete = computed(() => {
  const status = props.record?.status
  return status === 5 || status === 6 || status === 7 // 成功/失败/已回滚
})

const statusTagType = computed(() => {
  const map = { 0: 'info', 1: 'warning', 2: 'warning', 3: 'warning', 4: 'warning', 5: 'success', 6: 'danger', 7: '' }
  return map[props.record?.status] || 'info'
})

const statusLabel = computed(() => {
  const map = { 0: '等待中', 1: '拉取代码', 2: '编译构建', 3: '打包产物', 4: '上传服务器', 5: '部署成功', 6: '部署失败', 7: '已回滚' }
  return map[props.record?.status] || '未知'
})

const durationText = computed(() => {
  if (props.record?.durationSeconds != null) {
    return `${props.record.durationSeconds}s`
  }
  return '-'
})

const stepClass = (status) => {
  if (status === 0) return 'step-running'
  if (status === 1) return 'step-success'
  if (status === 2) return 'step-failed'
  return ''
}

const isStepCompleted = (index) => {
  for (let i = 0; i <= index; i++) {
    if (displaySteps.value[i]?.status !== 1) return false
  }
  return true
}

const handleClose = () => {
  emit('close')
  visible.value = false
}
</script>

<style scoped>
.deploy-progress {
  padding: 8px 0;
}
.steps-container {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px 0 30px;
  position: relative;
}
.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  flex: 1;
  z-index: 1;
}
.step-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #e4e7ed;
  color: #909399;
  font-size: 14px;
  font-weight: bold;
  transition: all 0.3s;
}
.step-running {
  background: #e6a23c;
  color: #fff;
  animation: pulse 1s ease-in-out infinite;
}
.step-success {
  background: #67c23a;
  color: #fff;
}
.step-failed {
  background: #f56c6c;
  color: #fff;
}
.step-label {
  font-size: 12px;
  color: #606266;
  margin-top: 8px;
  white-space: nowrap;
}
.step-line {
  position: absolute;
  top: 18px;
  left: 50%;
  width: 100%;
  height: 2px;
  background: #e4e7ed;
  z-index: -1;
  transition: background 0.3s;
}
.step-line.completed {
  background: #67c23a;
}
.status-info {
  margin-top: 12px;
}
.action-bar {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}
</style>
