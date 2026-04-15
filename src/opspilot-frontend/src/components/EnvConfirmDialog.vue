<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="480px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="confirm-content">
      <el-alert
        :title="message"
        :type="alertType"
        :closable="false"
        show-icon
        style="margin-bottom: 16px;"
      />
      <slot name="extra"></slot>
      <p v-if="warning" class="warning-text">
        <el-icon><WarningFilled /></el-icon>
        {{ warning }}
      </p>
    </div>
    <template #footer>
      <el-button @click="handleCancel">{{ cancelText }}</el-button>
      <el-button :type="confirmButtonType" :loading="loading" @click="handleConfirm">
        {{ confirmText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: '确认操作'
  },
  message: {
    type: String,
    default: '确认执行此操作？'
  },
  warning: {
    type: String,
    default: ''
  },
  alertType: {
    type: String,
    default: 'warning'
  },
  confirmButtonType: {
    type: String,
    default: 'danger'
  },
  confirmText: {
    type: String,
    default: '确认'
  },
  cancelText: {
    type: String,
    default: '取消'
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'confirm', 'cancel'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const handleConfirm = () => {
  emit('confirm')
}

const handleCancel = () => {
  emit('cancel')
  visible.value = false
}

const handleClose = () => {
  emit('cancel')
}
</script>

<style scoped>
.confirm-content {
  padding: 8px 0;
}
.warning-text {
  color: #e6a23c;
  font-size: 13px;
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
