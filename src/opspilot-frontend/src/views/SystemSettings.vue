<template>
  <div>
    <div class="page-header">
      <h2>系统设置</h2>
      <el-button @click="resetSettings" type="danger" plain>重置为默认</el-button>
    </div>

    <div class="card" v-loading="loading">
      <el-tabs v-model="activeTab">
        <!-- 基本设置 -->
        <el-tab-pane label="基本设置" name="basic">
          <el-form :model="settings" label-width="180px" style="max-width: 600px;">
            <el-form-item label="应用名称">
              <el-input v-model="settings.appName" />
            </el-form-item>
            <el-form-item label="默认 Git 分支">
              <el-input v-model="settings.defaultGitBranch" />
            </el-form-item>
            <el-form-item label="默认健康检查路径">
              <el-input v-model="settings.defaultHealthCheckPath" />
            </el-form-item>
            <el-form-item label="健康检查超时（秒）">
              <el-input-number v-model="settings.defaultHealthCheckTimeout" :min="5" :max="120" />
            </el-form-item>
            <el-form-item label="健康检查重试次数">
              <el-input-number v-model="settings.defaultHealthCheckRetries" :min="1" :max="10" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveSettings">保存设置</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 钉钉通知设置 -->
        <el-tab-pane label="钉钉通知" name="dingtalk">
          <el-form :model="settings" label-width="180px" style="max-width: 600px;">
            <el-form-item label="启用钉钉通知">
              <el-switch v-model="settings.dingtalkEnabled" :active-value="true" :inactive-value="false" />
            </el-form-item>
            <el-form-item label="Webhook 地址" v-if="settings.dingtalkEnabled">
              <el-input v-model="settings.dingtalkWebhook" placeholder="https://oapi.dingtalk.com/robot/send?access_token=..." type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="@ 用户 IDs" v-if="settings.dingtalkEnabled">
              <el-input v-model="settings.dingtalkAtUserIds" placeholder="多个用户用逗号分隔" />
            </el-form-item>
            <el-form-item label="部署成功通知">
              <el-switch v-model="settings.deployNotifyOnSuccess" :active-value="true" :inactive-value="false" />
            </el-form-item>
            <el-form-item label="部署失败通知">
              <el-switch v-model="settings.deployNotifyOnFailure" :active-value="true" :inactive-value="false" />
            </el-form-item>
            <el-form-item v-if="settings.dingtalkEnabled">
              <el-button type="primary" @click="saveSettings">保存设置</el-button>
              <el-button @click="testDingtalk" style="margin-left: 12px;">测试发送</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import api from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const activeTab = ref('basic')
const settings = reactive({
  appName: 'OpsPilot',
  defaultGitBranch: 'main',
  defaultHealthCheckPath: '/actuator/health',
  defaultHealthCheckTimeout: 30,
  defaultHealthCheckRetries: 3,
  dingtalkEnabled: false,
  dingtalkWebhook: '',
  dingtalkAtUserIds: '',
  deployNotifyOnSuccess: true,
  deployNotifyOnFailure: true
})

const fetchSettings = async () => {
  loading.value = true
  try {
    const res = await api.get('/settings')
    Object.assign(settings, res.data)
  } finally {
    loading.value = false
  }
}

const saveSettings = async () => {
  try {
    await api.put('/settings', { settings: { ...settings } })
    ElMessage.success('设置已保存')
  } catch (e) {}
}

const resetSettings = async () => {
  try {
    await ElMessageBox.confirm('确认重置所有设置为默认值？', '确认', { type: 'warning' })
    await api.post('/settings/reset')
    ElMessage.success('已重置为默认设置')
    fetchSettings()
  } catch {}
}

const testDingtalk = async () => {
  try {
    await api.post('/settings/test-dingtalk')
    ElMessage.success('测试消息已发送')
  } catch (e) {}
}

onMounted(fetchSettings)
</script>
