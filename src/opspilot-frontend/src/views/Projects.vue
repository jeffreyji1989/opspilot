<template>
  <div class="page-container" v-loading="pageLoading">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h1>项目管理</h1>
        <p>管理所有项目及其模块，模块是发版部署的基本单元。</p>
      </div>
      <el-button type="primary" @click="showAddDialog">
        <el-icon><Plus /></el-icon> 新建项目
      </el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="searchName" placeholder="搜索项目名称" clearable @clear="handleSearch" @keyup.enter="handleSearch" style="width: 240px">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-input v-model="searchOwner" placeholder="负责人" clearable style="width: 160px" @clear="handleSearch" @keyup.enter="handleSearch" />
      <el-select v-model="searchTag" placeholder="标签筛选" clearable @change="handleSearch" style="width: 160px">
        <el-option label="Java" value="Java" />
        <el-option label="Vue" value="Vue" />
        <el-option label="微服务" value="微服务" />
        <el-option label="电商" value="电商" />
      </el-select>
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>

    <!-- 正常状态 -->
    <template v-if="!error">
      <!-- 数据表格 -->
      <div class="card">
        <el-table :data="tableData" stripe border style="width: 100%">
          <el-table-column prop="projectName" label="项目名称" min-width="180">
            <template #default="{ row }">
              <span style="font-weight: 500; color: var(--el-text-color-primary)">{{ row.projectName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="projectCode" label="项目编码" width="140" />
          <el-table-column prop="businessLine" label="所属业务" width="140" />
          <el-table-column label="标签" min-width="200">
            <template #default="{ row }">
              <el-tag v-for="tag in splitTags(row.tags)" :key="tag" size="small" style="margin-right: 4px; margin-bottom: 2px">{{ tag }}</el-tag>
              <span v-if="!row.tags" style="color: var(--el-text-color-placeholder)">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="moduleCount" label="模块数" width="80" align="center">
            <template #default="{ row }">{{ row.moduleCount || 0 }}</template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="170" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="editProject(row)">
                <el-icon><Edit /></el-icon> 编辑
              </el-button>
              <el-button text type="primary" size="small" @click="manageModules(row)">
                <el-icon><Box /></el-icon> 模块
              </el-button>
              <el-popconfirm title="确认删除项目？此操作不可恢复。" @confirm="deleteProject(row.id)">
                <template #reference>
                  <el-button text type="danger" size="small" :disabled="(row.moduleCount || 0) > 0">
                    <el-icon><Delete /></el-icon> 删除
                  </el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="pagination-wrapper">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :current-page="page"
            :page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50]"
            @current-change="page = $event; fetchData()"
            @size-change="pageSize = $event; page = 1; fetchData()"
          />
        </div>
      </div>

      <!-- 空数据状态 -->
      <div v-if="tableData.length === 0 && !loading" class="card" style="margin-top: 16px">
        <el-empty description="暂无项目">
          <el-button type="primary" @click="showAddDialog">新建项目</el-button>
        </el-empty>
      </div>
    </template>

    <!-- 错误状态 -->
    <div v-if="error" class="card" style="margin-top: 16px">
      <el-alert title="加载项目列表失败" type="error" :closable="false" show-icon style="margin-bottom: 16px">
        <template #default>网络连接超时，请检查网络后重试。</template>
      </el-alert>
      <el-button type="primary" @click="error = false; fetchData()">
        <el-icon><Refresh /></el-icon> 重新加载
      </el-button>
    </div>

    <!-- 新建/编辑项目对话框 -->
    <el-dialog v-model="projectDialogVisible" :title="isEdit ? '编辑项目' : '新建项目'" width="560px" destroy-on-close>
      <el-form :model="projectForm" label-width="100px" label-position="right">
        <el-form-item label="项目名称" required>
          <el-input v-model="projectForm.projectName" placeholder="如：order-system" />
        </el-form-item>
        <el-form-item label="项目编码" required>
          <el-input v-model="projectForm.projectCode" placeholder="如：OPS-DEPLOY（唯一标识）" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="projectForm.owner" placeholder="负责人姓名" />
        </el-form-item>
        <el-form-item label="所属业务线">
          <el-input v-model="projectForm.businessLine" placeholder="如：交易中台" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="projectForm.tags" placeholder="逗号分隔，如：Java,SpringBoot,微服务" />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input v-model="projectForm.description" type="textarea" :rows="3" placeholder="项目描述（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="projectDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitProject">确定</el-button>
      </template>
    </el-dialog>

    <!-- 模块管理对话框 -->
    <el-dialog v-model="moduleDialogVisible" :title="`模块管理 — ${currentProject?.projectName || ''}`" width="900px" destroy-on-close>
      <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center">
        <span style="font-size: 13px; color: var(--el-text-color-secondary)">
          模块是发版部署的基本单元，支持 JAR/WAR/Vue/React/Node.js 等类型。
        </span>
        <el-button type="primary" size="small" @click="showAddModule">
          <el-icon><Plus /></el-icon> 添加模块
        </el-button>
      </div>

      <el-table :data="modules" stripe border style="width: 100%">
        <el-table-column prop="moduleName" label="模块名称" min-width="150" />
        <el-table-column prop="moduleType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="getModuleTypeTag(row.moduleType)">{{ row.moduleType || 'JAR' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="repoUrl" label="Git 仓库" min-width="200" show-overflow-tooltip />
        <el-table-column prop="repoBranch" label="默认分支" width="100" />
        <el-table-column prop="buildCommand" label="构建命令" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="editModule(row)">
              <el-icon><Edit /></el-icon>
            </el-button>
            <el-popconfirm title="确认删除模块？" @confirm="deleteModule(row.id)">
              <template #reference>
                <el-button text type="danger" size="small">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 模块表单对话框 -->
      <el-dialog v-model="moduleFormVisible" :title="moduleForm.id ? '编辑模块' : '添加模块'" width="600px" append-to-body destroy-on-close>
        <el-form :model="moduleForm" label-width="120px" label-position="right">
          <el-form-item label="模块名称" required>
            <el-input v-model="moduleForm.moduleName" placeholder="如：order-service" />
          </el-form-item>
          <el-form-item label="模块类型" required>
            <el-select v-model="moduleForm.moduleType" placeholder="选择模块类型" @change="onModuleTypeChange" style="width: 100%">
              <el-option label="Spring Boot JAR" value="JAR" />
              <el-option label="WAR" value="WAR" />
              <el-option label="Vue" value="Vue" />
              <el-option label="React" value="React" />
              <el-option label="Node.js" value="Node.js" />
              <el-option label="Android" value="Android" />
              <el-option label="Flutter" value="Flutter" />
            </el-select>
          </el-form-item>
          <el-form-item label="Git 仓库地址" required>
            <el-input v-model="moduleForm.repoUrl" placeholder="如：git@github.com:xxx/order.git" />
          </el-form-item>
          <el-form-item label="默认分支">
            <el-input v-model="moduleForm.repoBranch" placeholder="main" />
          </el-form-item>
          <el-form-item label="仓库子路径">
            <el-input v-model="moduleForm.repoPath" placeholder="Monorepo 场景使用，如：backend/order-service" />
          </el-form-item>
          <el-form-item label="Maven 模块名" v-if="['JAR', 'WAR'].includes(moduleForm.moduleType)">
            <el-input v-model="moduleForm.mavenModuleName" placeholder="如：order-service" />
          </el-form-item>
          <el-form-item label="JDK 版本" v-if="['JAR', 'WAR'].includes(moduleForm.moduleType)">
            <el-select v-model="moduleForm.jdkVersion" placeholder="选择 JDK 版本" style="width: 100%">
              <el-option label="JDK 8" value="8" />
              <el-option label="JDK 11" value="11" />
              <el-option label="JDK 17" value="17" />
              <el-option label="JDK 21" value="21" />
            </el-select>
          </el-form-item>
          <el-form-item label="Node 版本" v-if="['Vue', 'React', 'Node.js'].includes(moduleForm.moduleType)">
            <el-select v-model="moduleForm.nodeVersion" placeholder="选择 Node 版本" style="width: 100%">
              <el-option label="Node 14" value="14" />
              <el-option label="Node 16" value="16" />
              <el-option label="Node 18" value="18" />
              <el-option label="Node 20" value="20" />
            </el-select>
          </el-form-item>
          <el-form-item label="构建命令">
            <el-input v-model="moduleForm.buildCommand" type="textarea" :rows="2" placeholder="选择模块类型后自动填充" />
          </el-form-item>
          <el-form-item label="构建产物路径">
            <el-input v-model="moduleForm.artifactPath" placeholder="如：target/xxx.jar 或 dist/" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="moduleFormVisible = false">取消</el-button>
          <el-button type="primary" @click="saveModule">保存</el-button>
        </template>
      </el-dialog>

      <template #footer>
        <el-button @click="moduleDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus, Search, Edit, Delete, Refresh, Box } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const pageLoading = ref(false)
const loading = ref(false)
const error = ref(false)
const tableData = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchName = ref('')
const searchOwner = ref('')
const searchTag = ref('')

const projectDialogVisible = ref(false)
const isEdit = ref(false)
const projectForm = reactive({ id: null, projectName: '', projectCode: '', owner: '', businessLine: '', tags: '', description: '' })

const moduleDialogVisible = ref(false)
const currentProject = ref(null)
const modules = ref([])
const moduleFormVisible = ref(false)
const moduleForm = reactive({
  id: null, moduleName: '', moduleType: '', repoUrl: '', repoBranch: 'main',
  repoPath: '', mavenModuleName: '', jdkVersion: '', nodeVersion: '',
  buildCommand: '', artifactPath: '', gitCredId: null
})

const splitTags = (tags) => {
  if (!tags) return []
  return tags.split(',').filter(t => t.trim())
}

const getModuleTypeTag = (type) => {
  const map = { JAR: '', Vue: 'success', React: 'success', 'Node.js': 'warning', Android: 'info', Flutter: 'info' }
  return map[type] || 'info'
}

const fetchData = async () => {
  loading.value = true
  error.value = false
  try {
    const res = await api.get('/projects', {
      params: { pageNum: page.value, pageSize: pageSize.value, keyword: searchName.value || searchOwner.value }
    })
    tableData.value = res.data.records
    total.value = res.data.total
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const resetSearch = () => {
  searchName.value = ''
  searchOwner.value = ''
  searchTag.value = ''
  handleSearch()
}

const showAddDialog = () => {
  isEdit.value = false
  Object.assign(projectForm, { id: null, projectName: '', projectCode: '', owner: '', businessLine: '', tags: '', description: '' })
  projectDialogVisible.value = true
}

const editProject = (row) => {
  isEdit.value = true
  Object.assign(projectForm, { ...row })
  projectDialogVisible.value = true
}

const submitProject = async () => {
  try {
    if (isEdit.value) {
      await api.put(`/projects/${projectForm.id}`, projectForm)
    } else {
      await api.post('/projects', projectForm)
    }
    ElMessage.success(isEdit.value ? '保存成功' : '创建成功')
    projectDialogVisible.value = false
    fetchData()
  } catch (e) {
    // Error handled by interceptor
  }
}

const deleteProject = async (id) => {
  try {
    await api.delete(`/projects/${id}`)
    ElMessage.success('删除成功')
    fetchData()
  } catch (e) {
    // Error handled by interceptor
  }
}

const manageModules = async (row) => {
  currentProject.value = row
  try {
    const res = await api.get(`/projects/${row.id}/modules`)
    modules.value = res.data
  } catch (e) {
    modules.value = []
  }
  moduleDialogVisible.value = true
}

const showAddModule = () => {
  Object.assign(moduleForm, {
    id: null, moduleName: '', moduleType: '', repoUrl: '', repoBranch: 'main',
    repoPath: '', mavenModuleName: '', jdkVersion: '', nodeVersion: '',
    buildCommand: '', artifactPath: '', gitCredId: null
  })
  moduleFormVisible.value = true
}

const editModule = (row) => {
  Object.assign(moduleForm, { ...row })
  moduleFormVisible.value = true
}

const onModuleTypeChange = async () => {
  try {
    const res = await api.get(`/modules/build-template/${moduleForm.moduleType}`)
    const t = res.data
    if (t.buildCommand) moduleForm.buildCommand = t.buildCommand
    if (t.artifactPath) moduleForm.artifactPath = t.artifactPath
  } catch (e) {
    // Use defaults
  }
}

const saveModule = async () => {
  try {
    if (moduleForm.id) {
      await api.put(`/modules/${moduleForm.id}`, moduleForm)
    } else {
      await api.post('/modules', { ...moduleForm, projectId: currentProject.value.id })
    }
    ElMessage.success('保存成功')
    moduleFormVisible.value = false
    manageModules(currentProject.value)
  } catch (e) {
    // Error handled by interceptor
  }
}

const deleteModule = async (id) => {
  try {
    await api.delete(`/modules/${id}`)
    ElMessage.success('删除成功')
    manageModules(currentProject.value)
  } catch (e) {
    // Error handled by interceptor
  }
}

onMounted(fetchData)
</script>

<style scoped>
.page-container {
  padding: 24px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.page-header h1 {
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 0 0 4px;
}
.page-header p {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0;
}
.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  align-items: center;
}
.card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  padding: 24px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
