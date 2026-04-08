<template>
  <div class="agents-hub fade-in">
    <div class="hub-header">
      <div class="header-text">
        <h1>智能体枢纽</h1>
        <p>管理并监控您的 AI 协作团队，每个智能体都拥有独特的专业技能。</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" plain @click="loadAgents">
          <el-icon class="el-icon--left"><Refresh /></el-icon>刷新状态
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="agents-grid">
      <el-row :gutter="24">
        <el-col :span="8" v-for="agent in agents" :key="row.id" style="margin-bottom: 24px">
          <el-card class="agent-card" :body-style="{ padding: '0px' }">
            <div class="agent-header">
              <div class="agent-avatar">
                <el-icon :size="28" color="#6366f1"><UserFilled /></el-icon>
              </div>
              <div class="agent-title">
                <h3>{{ agent.name }}</h3>
                <el-tag size="small" effect="light" round>{{ agent.role || '智能专家' }}</el-tag>
              </div>
              <div class="agent-status">
                <el-switch
                  v-model="agent.status"
                  active-value="active"
                  inactive-value="inactive"
                  @change="toggleStatus(agent)"
                />
              </div>
            </div>
            
            <div class="agent-body">
              <div class="agent-skills">
                <el-tag 
                  v-for="skill in parseCapabilities(agent.capabilities)" 
                  :key="skill" 
                  size="small" 
                  class="skill-tag"
                >
                  {{ skill }}
                </el-tag>
              </div>
              
              <div class="agent-metrics">
                <div class="metric-item">
                  <span class="metric-label">效率</span>
                  <el-progress :percentage="agent.efficiencyScore" :color="customColors" />
                </div>
                <div class="metric-item">
                  <span class="metric-label">成功率</span>
                  <el-progress :percentage="agent.successRate" :color="customColors" />
                </div>
              </div>
            </div>

            <div class="agent-footer">
              <el-button text type="primary" @click="showEditDialog(agent)">
                <el-icon class="el-icon--left"><Edit /></el-icon>配置参数
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
      
      <el-empty v-if="agents.length === 0" description="未发现活跃智能体" />
    </div>

    <!-- Edit Dialog -->
    <el-dialog 
      v-model="dialogVisible" 
      title="配置智能体" 
      width="460px"
      class="custom-dialog"
    >
      <el-form :model="editForm" :rules="rules" ref="formRef" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="editForm.name" placeholder="输入智能体名称" />
        </el-form-item>
        <el-form-item label="能力架构 (JSON 格式)" prop="capabilities">
          <el-input v-model="editForm.capabilities" type="textarea" :rows="4" placeholder='{"skills": ["Java", "Spring Boot"]}' />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="效率评分" prop="efficiencyScore" style="flex: 1">
            <el-input-number v-model="editForm.efficiencyScore" :min="0" :max="100" style="width: 100%" />
          </el-form-item>
          <el-form-item label="成功率" prop="successRate" style="flex: 1">
            <el-input-number v-model="editForm.successRate" :min="0" :max="100" style="width: 100%" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave" :loading="saving">保存更改</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, UserFilled, Edit } from '@element-plus/icons-vue'
import { agentApi } from '../api'

const loading = ref(false)
const saving = ref(false)
const agents = ref([])
const dialogVisible = ref(false)
const formRef = ref(null)

const customColors = [
  { color: '#f56c6c', percentage: 20 },
  { color: '#e6a23c', percentage: 40 },
  { color: '#5cb87a', percentage: 60 },
  { color: '#1989fa', percentage: 80 },
  { color: '#6366f1', percentage: 100 },
]

const editForm = ref({
  id: '',
  name: '',
  capabilities: '',
  efficiencyScore: 0,
  successRate: 0
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  capabilities: [{ required: true, message: '请输入能力描述', trigger: 'blur' }]
}

const loadAgents = async () => {
  loading.value = true
  try {
    const res = await agentApi.getAll()
    agents.value = res.data
  } catch (error) {
    console.error(error)
    ElMessage.error('加载智能体失败')
  } finally {
    loading.value = false
  }
}

const parseCapabilities = (capabilities) => {
  try {
    const obj = JSON.parse(capabilities)
    return obj.skills || []
  } catch {
    return capabilities ? [capabilities] : []
  }
}

const showEditDialog = (agent) => {
  editForm.value = {
    id: agent.id,
    name: agent.name,
    capabilities: agent.capabilities,
    efficiencyScore: agent.efficiencyScore,
    successRate: agent.successRate
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  await formRef.value.validate()
  saving.value = true

  try {
    await agentApi.update(editForm.value.id, editForm.value)
    ElMessage.success('智能体配置已更新')
    dialogVisible.value = false
    loadAgents()
  } catch (error) {
    console.error(error)
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (agent) => {
  try {
    await agentApi.updateStatus(agent.id, agent.status)
    ElMessage.success(`智能体已${agent.status === 'active' ? '上线' : '下线'}`)
  } catch (error) {
    agent.status = agent.status === 'active' ? 'inactive' : 'active'
    console.error(error)
  }
}

onMounted(() => {
  loadAgents()
})
</script>

<style scoped>
.agents-hub {
  max-width: 1200px;
  margin: 0 auto;
}

.hub-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.header-text h1 {
  font-size: 1.875rem;
  font-weight: 800;
  margin: 0 0 8px 0;
  color: var(--text-primary);
}

.header-text p {
  color: var(--text-secondary);
  margin: 0;
}

.agent-card {
  transition: transform 0.2s, box-shadow 0.2s;
  height: 100%;
}

.agent-card:hover {
  transform: translateY(-4px);
}

.agent-header {
  padding: 20px;
  display: flex;
  align-items: flex-start;
  gap: 16px;
  border-bottom: 1px solid var(--border-color);
}

.agent-avatar {
  width: 48px;
  height: 48px;
  background-color: #eef2ff;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.agent-title {
  flex: 1;
}

.agent-title h3 {
  margin: 0 0 4px 0;
  font-size: 1.1rem;
  color: var(--text-primary);
}

.agent-body {
  padding: 20px;
}

.agent-skills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 20px;
  min-height: 60px;
}

.skill-tag {
  background-color: #f8fafc;
  border-color: #e2e8f0;
  color: #475569;
}

.agent-metrics {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.metric-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.metric-label {
  font-size: 0.8125rem;
  color: var(--text-secondary);
  width: 45px;
}

.metric-item :deep(.el-progress) {
  flex: 1;
  margin-bottom: 0;
}

.agent-footer {
  padding: 12px 20px;
  background-color: #f8fafc;
  border-top: 1px solid var(--border-color);
  text-align: right;
}

.form-row {
  display: flex;
  gap: 16px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
