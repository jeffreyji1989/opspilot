<template>
  <el-tag :style="tagStyle" size="small" effect="light">
    {{ label }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  envType: { type: Number, default: 0 },
})

const envMap = {
  0: { label: 'dev', color: '#3B82F6', bg: '#EFF6FF', border: '#BFDBFE' },
  1: { label: 'test', color: '#D97706', bg: '#FFFBEB', border: '#FDE68A' },
  2: { label: 'staging', color: '#8B5CF6', bg: '#F5F3FF', border: '#DDD6FE' },
  3: { label: 'prod', color: '#DC2626', bg: '#FEF2F2', border: '#FECACA' },
}

const env = computed(() => envMap[props.envType] || envMap[0])
const label = computed(() => env.value.label)
const tagStyle = computed(() => ({
  backgroundColor: env.value.bg,
  color: env.value.color,
  borderColor: env.value.border,
}))
</script>
