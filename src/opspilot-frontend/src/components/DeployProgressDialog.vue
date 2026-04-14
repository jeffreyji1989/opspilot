<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="680px"
    :close-on-click-modal="false"
    :show-close="canClose"
    align-center
    class="deploy-progress-dialog"
  >
    <!-- 7 步进度条 -->
    <div class="steps-container">
      <div
        v-for="step in steps"
        :key="step.no"
        class="deploy-step"
        :class="stepClass(step.status)"
      >
        <div class="step-icon" :class="stepIconClass(step.status)">
          <el-icon v-if="step.status === 'success'"><Check /></el-icon>
          <el-icon v-else-if="step.status === 'failed'"><Close /></el-icon>
          <el-icon v-else-if="step.status === 'running'"><Loading /></el-icon>
          <span v-else>{{ step.no }}</span>
        </div>
        <div class="step-info">
          <div class="step-name">{{ step.name }}</div>
          <div class="step-status">{{ stepStatusText(step.status) }}</div>
        </div>
        <div class="step-time" v-if="step.duration">
          {{ step.duration }}s
        </div>
        <div class="step-error" v-if="step.status === 'failed' && step.error">
          {{ step.error }}
        </div>
      </div>
    </div>

    <!-- 实时日志区域 -->
    <div v-if="showLog" class="deploy-log">
      <div class="log-header">实时日志</div>
      <div class="log-content" ref="logContainer">
        <div
          v-for="(line, idx) in logLines"
          :key="idx"
          class="log-line"
          :class="logLevelClass(line)"
        >{{ line }}</div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button v-if="showCancel" @click="handleCancel" type="danger">取消部署</el-button>
        <el-button v-if="showRollback" @click="handleRollback" type="warning">一键回退</el-button>
        <el-button v-if="isComplete" type="primary" @click="visible = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { Check, Close, Loading } from '@element-plus/icons-vue'

const props = defineProps({
  serviceName: { type: String, default: '' },
  isProd: { type: Boolean, default: false },
})

const emit = defineEmits(['cancel', 'rollback', 'close'])

const visible = ref(false)
const logLines = ref([])
const logContainer = ref(null)

const stepStatusMap = {
  0: 'pending',
  1: 'running',
  2: 'success',
  3: 'failed',
}

const steps = ref([
  { no: 1, name: '拉取代码', status: 'pending', duration: '', error: '' },
  { no: 2, name: '编译构建', status: 'pending', duration: '', error: '' },
  { no: 3, name: '打包产物', status: 'pending', duration: '', error: '' },
  { no: 4, name: '上传至服务器', status: 'pending', duration: '', error: '' },
  { no: 5, name: '切换版本', status: 'pending', duration: '', error: '' },
  { no: 6, name: '重启服务', status: 'pending', duration: '', error: '' },
  { no: 7, name: '健康检查', status: 'pending', duration: '', error: '' },
])

const showLog = ref(true)

const dialogTitle = computed(() => {
  if (props.isProd) return `⚠️ 生产环境发版部署 - ${props.serviceName}`
  return `发版部署 - ${props.serviceName}`
})

const isComplete = computed(() => {
  return steps.value.every(s => s.status === 'success' || s.status === 'failed')
})

const hasFailed = computed(() => {
  return steps.value.some(s => s.status === 'failed')
})

const canClose = computed(() => isComplete.value)
const showCancel = computed(() => !isComplete.value && !hasFailed.value && !props.isProd)
const showRollback = computed(() => hasFailed.value)

const stepClass = (status) => {
  const map = { pending: '', running: 'active', success: 'success', failed: 'failed' }
  return map[status] || ''
}

const stepIconClass = (status) => {
  const map = { pending: 'pending', running: 'running', success: 'done', failed: 'fail' }
  return map[status] || 'pending'
}

const stepStatusText = (status) => {
  const map = { pending: '等待中', running: '执行中...', success: '完成', failed: '失败' }
  return map[status] || ''
}

const logLevelClass = (line) => {
  if (line.includes('ERROR') || line.includes('FATAL')) return 'error'
  if (line.includes('WARN')) return 'warn'
  if (line.includes('INFO')) return 'info'
  return ''
}

const open = () => {
  visible.value = true
  steps.value.forEach(s => { s.status = 'pending'; s.duration = ''; s.error = '' })
  logLines.value = []
}

const updateStep = (stepNo, status, duration, error) => {
  const step = steps.value.find(s => s.no === stepNo)
  if (step) {
    step.status = stepStatusMap[status] || status
    if (duration) step.duration = duration
    if (error) step.error = error
  }
}

const addLog = (line) => {
  logLines.value.push(line)
  nextTick(() => {
    if (logContainer.value) {
      logContainer.value.scrollTop = logContainer.value.scrollHeight
    }
  })
}

const handleCancel = () => { emit('cancel') }
const handleRollback = () => { emit('rollback') }

defineExpose({ open, updateStep, addLog })
</script>

<style scoped>
.steps-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
}
.deploy-step {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 8px;
  background: #F9FAFB;
  transition: all 0.3s;
}
.deploy-step.active {
  background: #EEF2FF;
  border: 1px solid #6366F1;
}
.deploy-step.success {
  background: #ECFDF5;
}
.deploy-step.failed {
  background: #FEF2F2;
}
.step-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
}
.step-icon.pending { background: #E5E7EB; color: #6B7280; }
.step-icon.running { background: #6366F1; color: #fff; animation: pulse 1.5s ease-in-out infinite; }
.step-icon.done { background: #10B981; color: #fff; }
.step-icon.fail { background: #EF4444; color: #fff; }
.step-info { flex: 1; }
.step-name { font-size: 14px; font-weight: 500; }
.step-status { font-size: 12px; color: #6B7280; }
.step-time { font-size: 12px; color: #6B7280; }
.step-error { font-size: 12px; color: #EF4444; margin-top: 4px; }
@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.2); }
}

.deploy-log {
  background: #1E1E2D;
  border-radius: 8px;
  overflow: hidden;
}
.log-header {
  padding: 8px 16px;
  background: #2D2D3D;
  font-size: 13px;
  color: #A2A3B7;
}
.log-content {
  padding: 12px;
  max-height: 200px;
  overflow-y: auto;
  font-family: 'Cascadia Code', 'Fira Code', monospace;
  font-size: 12px;
  line-height: 1.6;
}
.log-line { white-space: pre-wrap; word-break: break-all; }
.log-line.error { color: #F87171; }
.log-line.warn { color: #FBBF24; }
.log-line.info { color: #60A5FA; }
.log-line:not(.error):not(.warn):not(.info) { color: #D4D4D4; }

.dialog-footer {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
