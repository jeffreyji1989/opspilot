<template>
  <div>
    <div class="page-header">
      <div>
        <h2>系统设置</h2>
        <p class="page-desc">配置钉钉通知、默认参数等系统级选项</p>
      </div>
    </div>

    <div class="card settings-card" v-loading="loading">
      <h3>🔔 钉钉通知配置</h3>
      <el-form :model="settings" label-width="160px">
        <el-form-item label="钉钉 Webhook">
          <el-input v-model="settings.dingtalkWebhook" placeholder="https://oapi.dingtalk.com/robot/send?access_token=..." />
        </el-form-item>
        <el-form-item label="加签密钥">
          <el-input v-model="settings.dingtalkSecret" placeholder="SECxxxx" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="testDingTalk" :loading="testingDingTalk">测试通知</el-button>
        </el-form-item>
      </el-form>

      <el-divider />

      <h3>⚙️ 默认参数配置</h3>
      <el-form :model="settings" label-width="160px">
        <el-form-item label="默认构建超时（秒）">
          <el-input-number v-model="settings.buildTimeout" :min="30" :max="1800" />
        </el-form-item>
        <el-form-item label="默认健康检查路径">
          <el-input v-model="settings.healthCheckPath" placeholder="/actuator/health" style="width: 300px;" />
        </el-form-item>
        <el-form-item label="默认健康检查超时（秒）">
          <el-input-number v-model="settings.healthCheckTimeout" :min="10" :max="300" />
        </el-form-item>
      </el-form>

      <el-divider />

      <el-form-item>
        <el-button type="primary" @click="saveSettings" :loading="saving">保存设置</el-button>
      </el-form-item>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import api from '../api'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const saving = ref(false)
const testingDingTalk = ref(false)
const settings = reactive({
  dingtalkWebhook: '',
  dingtalkSecret: '',
  buildTimeout: 300,
  healthCheckPath: '/actuator/health',
  healthCheckTimeout: 60,
})

const fetchSettings = async () => {
  loading.value = true
  try {
    const res = await api.get('/system-settings')
    if (res.data) {
      settings.dingtalkWebhook = res.data.dingtalkWebhook || ''
      settings.buildTimeout = res.data.buildTimeout || 300
      settings.healthCheckPath = res.data.healthCheckPath || '/actuator/health'
      settings.healthCheckTimeout = res.data.healthCheckTimeout || 60
    }
  } finally {
    loading.value = false
  }
}

const saveSettings = async () => {
  saving.value = true
  try {
    await api.post('/system-settings', settings)
    ElMessage.success('设置已保存')
  } finally {
    saving.value = false
  }
}

const testDingTalk = async () => {
  if (!settings.dingtalkWebhook) {
    ElMessage.warning('请先填写 Webhook 地址')
    return
  }
  testingDingTalk.value = true
  try {
    const res = await api.post('/system-settings/test-dingtalk', {
      webhook: settings.dingtalkWebhook,
      secret: settings.dingtalkSecret,
    })
    if (res.data) {
      ElMessage.success('测试通知发送成功')
    } else {
      ElMessage.error('发送失败，请检查 Webhook 配置')
    }
  } catch {
    ElMessage.error('发送失败')
  } finally {
    testingDingTalk.value = false
  }
}

onMounted(fetchSettings)
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
.settings-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #E5E7EB;
  padding: 24px;
  max-width: 700px;
}
.settings-card h3 {
  font-size: 16px;
  font-weight: 600;
  color: #1F2937;
  margin-bottom: 16px;
}
.el-divider {
  margin: 24px 0;
}
</style>
