<template>
  <span class="status-dot" :class="statusClass">
    <span v-if="status === 'deploying'" class="pulse"></span>
  </span>
  <span v-if="showLabel" class="status-label">{{ label }}</span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String, default: 'running' },
  showLabel: { type: Boolean, default: false },
})

const statusMap = {
  running: { class: 'running', label: '运行中' },
  stopped: { class: 'stopped', label: '已停止' },
  deploying: { class: 'deploying', label: '部署中' },
  error: { class: 'error', label: '异常' },
  offline: { class: 'offline', label: '离线' },
}

const statusClass = computed(() => statusMap[props.status]?.class || 'offline')
const label = computed(() => statusMap[props.status]?.label || '未知')
</script>

<style scoped>
.status-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  position: relative;
}
.status-dot.running { background: #10B981; }
.status-dot.stopped { background: #9CA3AF; }
.status-dot.deploying { background: #3B82F6; }
.status-dot.error { background: #EF4444; }
.status-dot.offline { background: #9CA3AF; }
.status-dot .pulse {
  position: absolute;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: inherit;
  animation: pulse 1.5s ease-in-out infinite;
}
.status-label {
  margin-left: 6px;
  font-size: 13px;
  color: #374151;
}
@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.5); }
}
</style>
