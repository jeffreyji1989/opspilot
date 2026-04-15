<template>
  <el-tag :type="tagType" :effect="effect" size="small" :round="round">
    {{ label }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  env: {
    type: String,
    default: ''
  },
  effect: {
    type: String,
    default: 'light'
  },
  round: {
    type: Boolean,
    default: false
  }
})

const tagType = computed(() => {
  const map = {
    dev: 'info',
    test: 'warning',
    staging: '',
    prod: 'danger',
    production: 'danger'
  }
  return map[props.env?.toLowerCase()] || 'info'
})

const label = computed(() => {
  const map = {
    dev: 'DEV',
    test: 'TEST',
    staging: 'STAGING',
    prod: 'PROD',
    production: 'PROD'
  }
  return map[props.env?.toLowerCase()] || props.env?.toUpperCase() || '-'
})
</script>
