<template>
  <div>
    <div class="page-header">
      <h2>项目管理</h2>
      <el-button type="primary" @click="showProjectDialog()">新增项目</el-button>
    </div>

    <div class="card">
      <el-form inline>
        <el-form-item label="搜索">
          <el-input v-model="keyword" placeholder="项目名称/编码" clearable @clear="fetchData" @keyup.enter="fetchData" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" style="width: 100%">
        <el-table-column prop="projectCode" label="项目编码" width="140" />
        <el-table-column prop="projectName" label="项目名称" />
        <el-table-column label="运行时" width="140">
          <template #default="{ row }">
            {{ projectRuntimeLabel(row.runtimeType, row.runtimeVersion) }}
          </template>
        </el-table-column>
        <el-table-column prop="businessLine" label="业务线" width="120" />
        <el-table-column prop="tags" label="标签" width="160" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showModules(row)">模块管理</el-button>
            <el-button size="small" type="primary" @click="showProjectDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该项目？" @confirm="deleteProject(row.id)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top: 16px; justify-content: flex-end;"
        :current-page="page"
        :page-size="pageSize"
        :total="total"
        @current-change="page = $event; fetchData()"
        layout="total, prev, pager, next"
      />
    </div>

    <!-- Project Dialog -->
    <el-dialog v-model="projectVisible" :title="isEdit ? '编辑项目' : '新增项目'" width="500px">
      <el-form :model="projectForm" label-width="100px">
        <el-form-item label="项目编码" required>
          <el-input v-model="projectForm.projectCode" />
        </el-form-item>
        <el-form-item label="项目名称" required>
          <el-input v-model="projectForm.projectName" />
        </el-form-item>
        <el-form-item label="业务线">
          <el-input v-model="projectForm.businessLine" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="projectForm.tags" placeholder="逗号分隔" />
        </el-form-item>
        <el-form-item label="运行时类型">
          <el-select v-model="projectForm.runtimeType" @change="onRuntimeTypeChange">
            <el-option label="Java" value="java" />
            <el-option label="Node.js" value="node" />
            <el-option label="Python" value="python" />
          </el-select>
        </el-form-item>
        <el-form-item label="JDK版本" v-if="projectForm.runtimeType === 'java'">
          <el-select v-model="projectForm.runtimeVersion" placeholder="选择 JDK 版本">
            <el-option label="JDK 8" value="8" />
            <el-option label="JDK 11" value="11" />
            <el-option label="JDK 17" value="17" />
            <el-option label="JDK 21" value="21" />
          </el-select>
        </el-form-item>
        <el-form-item label="Node.js版本" v-if="projectForm.runtimeType === 'node'">
          <el-select v-model="projectForm.runtimeVersion" placeholder="选择 Node.js 版本">
            <el-option label="Node 16" value="16" />
            <el-option label="Node 18" value="18" />
            <el-option label="Node 20" value="20" />
            <el-option label="Node 22" value="22" />
          </el-select>
        </el-form-item>
        <el-form-item label="Python版本" v-if="projectForm.runtimeType === 'python'">
          <el-select v-model="projectForm.runtimeVersion" placeholder="选择 Python 版本">
            <el-option label="Python 3.8" value="3.8" />
            <el-option label="Python 3.9" value="3.9" />
            <el-option label="Python 3.10" value="3.10" />
            <el-option label="Python 3.11" value="3.11" />
            <el-option label="Python 3.12" value="3.12" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="projectForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="projectVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProject">保存</el-button>
      </template>
    </el-dialog>

    <!-- Modules Dialog -->
    <el-dialog v-model="moduleVisible" :title="`模块管理 - ${currentProject?.projectName}`" width="800px">
      <el-button type="primary" size="small" @click="showModuleDialog()" style="margin-bottom: 12px;">新增模块</el-button>
      <el-table :data="modules" size="small">
        <el-table-column prop="moduleName" label="模块名称" />
        <el-table-column prop="repoUrl" label="Git仓库" show-overflow-tooltip />
        <el-table-column prop="repoBranch" label="默认分支" width="100" />
        <el-table-column prop="buildTool" label="构建工具" width="100" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" @click="showModuleDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除？" @confirm="deleteModule(row.id)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- Module Form Dialog -->
      <el-dialog v-model="moduleFormVisible" :title="moduleForm.id ? '编辑模块' : '新增模块'" width="600px" append-to-body>
        <el-form :model="moduleForm" label-width="120px">
          <el-form-item label="模块名称" required>
            <el-input v-model="moduleForm.moduleName" />
          </el-form-item>
          <el-form-item label="Git仓库地址" required>
            <el-input v-model="moduleForm.repoUrl" />
          </el-form-item>
          <el-form-item label="默认分支">
            <el-input v-model="moduleForm.repoBranch" placeholder="main" />
          </el-form-item>
          <el-form-item label="子路径">
            <el-input v-model="moduleForm.repoPath" placeholder="Monorepo 子路径" />
          </el-form-item>
          <el-form-item label="构建工具">
            <el-select v-model="moduleForm.buildTool">
              <el-option label="Maven" value="maven" />
              <el-option label="Gradle" value="gradle" />
              <el-option label="npm" value="npm" />
            </el-select>
          </el-form-item>
          <el-form-item label="构建命令">
            <el-input v-model="moduleForm.buildCommand" placeholder="留空使用默认" />
          </el-form-item>
          <el-form-item label="产物路径">
            <el-input v-model="moduleForm.artifactPath" placeholder="如 target/xxx.jar" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="moduleFormVisible = false">取消</el-button>
          <el-button type="primary" @click="saveModule">保存</el-button>
        </template>
      </el-dialog>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import api from '../api'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')

const projectVisible = ref(false)
const isEdit = ref(false)
const projectForm = reactive({ id: null, projectCode: '', projectName: '', businessLine: '', tags: '', description: '', runtimeType: 'java', runtimeVersion: '17' })

const moduleVisible = ref(false)
const currentProject = ref(null)
const modules = ref([])
const moduleFormVisible = ref(false)
const moduleForm = reactive({ id: null, moduleName: '', repoUrl: '', repoBranch: 'main', repoPath: '', buildTool: 'maven', buildCommand: '', artifactPath: '' })

const projectRuntimeLabel = (type, version) => {
  const typeMap = { java: 'Java', node: 'Node.js', python: 'Python' }
  return version ? `${typeMap[type] || type} ${version}` : (typeMap[type] || type || '-')
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await api.get('/projects', { params: { pageNum: page.value, pageSize: pageSize.value, keyword: keyword.value } })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const showProjectDialog = (row) => {
  isEdit.value = !!row
  if (row) {
    Object.assign(projectForm, { ...row, runtimeType: row.runtimeType || 'java', runtimeVersion: row.runtimeVersion || '17' })
  } else {
    Object.assign(projectForm, { id: null, projectCode: '', projectName: '', businessLine: '', tags: '', description: '', runtimeType: 'java', runtimeVersion: '17' })
  }
  projectVisible.value = true
}

const onRuntimeTypeChange = () => {
  projectForm.runtimeVersion = ''
}

const saveProject = async () => {
  try {
    if (isEdit.value) {
      await api.put(`/projects/${projectForm.id}`, projectForm)
    } else {
      await api.post('/projects', projectForm)
    }
    ElMessage.success('保存成功')
    projectVisible.value = false
    fetchData()
  } catch (e) {}
}

const deleteProject = async (id) => {
  await api.delete(`/projects/${id}`)
  ElMessage.success('已删除')
  fetchData()
}

const showModules = async (row) => {
  currentProject.value = row
  const res = await api.get(`/projects/${row.id}/modules`)
  modules.value = res.data
  moduleVisible.value = true
}

const showModuleDialog = (row) => {
  if (row) {
    Object.assign(moduleForm, row)
  } else {
    Object.assign(moduleForm, { id: null, moduleName: '', repoUrl: '', repoBranch: 'main', repoPath: '', buildTool: 'maven', buildCommand: '', artifactPath: '' })
  }
  moduleFormVisible.value = true
}

const saveModule = async () => {
  try {
    if (moduleForm.id) {
      await api.put(`/projects/modules/${moduleForm.id}`, moduleForm)
    } else {
      await api.post(`/projects/${currentProject.value.id}/modules`, moduleForm)
    }
    ElMessage.success('保存成功')
    moduleFormVisible.value = false
    showModules(currentProject.value)
  } catch (e) {}
}

const deleteModule = async (id) => {
  await api.delete(`/projects/modules/${id}`)
  ElMessage.success('已删除')
  showModules(currentProject.value)
}

onMounted(fetchData)
</script>
