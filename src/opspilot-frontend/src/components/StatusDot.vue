<template>
  <span class="status-dot" :class="[`status-${status}`, { 'pulse': pulse }]">
    <span class="dot"></span>
    <span v-if="showText" class="text">{{ label }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: {
    type: [String, Number],
    default: 0
  },
  pulse: {
    type: Boolean,
    default: false
  },
  showText: {
    type: Boolean,
    default: false
  }
})

const label = computed(() => {
  const map = {
    0: '已停止',
    1: '运行中',
    2: '部署中',
    3: '异常'
  }
  return map[props.status] || '未知'
})
</script>

<style scoped>
.status-dot {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #909399;
}
.status-0 .dot { background-color: #909399; }
.status-1 .dot { background-color: #67c23a; }
.status-2 .dot { background-color: #e6a23c; }
.status-3 .dot { background-color: #f56c6c; }
.text {
  font-size: 12px;
  color: #606266;
}
.pulse .dot {
  animation: pulse 1.5s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.2); }
}
</style>
