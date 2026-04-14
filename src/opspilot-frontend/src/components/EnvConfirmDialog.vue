<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="width"
    align-center
    class="env-confirm-dialog"
  >
    <div class="confirm-content">
      <el-alert
        v-if="isProd"
        title="⚠️ 生产环境操作"
        type="error"
        :closable="false"
        show-icon
        style="margin-bottom: 16px;"
      />

      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="操作类型">{{ operationType }}</el-descriptions-item>
        <el-descriptions-item label="服务名称">{{ serviceName }}</el-descriptions-item>
        <el-descriptions-item v-if="targetVersion" label="目标版本">{{ targetVersion }}</el-descriptions-item>
        <el-descriptions-item v-if="targetBranch" label="目标分支">{{ targetBranch }}</el-descriptions-item>
      </el-descriptions>

      <p class="confirm-hint" :class="{ 'hint-danger': isProd }">
        {{ confirmHint }}
      </p>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        :type="isProd ? 'danger' : 'primary'"
        @click="handleConfirm"
      >
        <span v-if="isProd">⚠️ </span>{{ confirmButtonText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  /** 是否生产环境 */
  isProd: { type: Boolean, default: false },
  /** 操作类型 */
  operationType: { type: String, default: '' },
  /** 服务名称 */
  serviceName: { type: String, default: '' },
  /** 目标版本 */
  targetVersion: { type: String, default: '' },
  /** 目标分支 */
  targetBranch: { type: String, default: '' },
  /** 对话框宽度 */
  width: { type: String, default: '480px' },
})

const emit = defineEmits(['confirm'])

const visible = ref(false)

const title = computed(() => props.isProd ? '⚠️ 生产环境操作确认' : '确认操作')
const confirmHint = computed(() => {
  if (props.isProd) {
    return '此操作将影响生产环境服务，请确认已做好充分测试并选择了正确的目标。'
  }
  return '确认执行此操作？'
})
const confirmButtonText = computed(() => props.isProd ? '我确认，执行操作' : '确认')

const open = () => {
  visible.value = true
}

const handleConfirm = () => {
  visible.value = false
  emit('confirm')
}

defineExpose({ open })
</script>

<style scoped>
.confirm-content {
  padding: 8px 0;
}
.confirm-hint {
  margin-top: 16px;
  font-size: 13px;
  color: #6B7280;
}
.confirm-hint.hint-danger {
  color: #DC2626;
  font-weight: 500;
}
</style>
