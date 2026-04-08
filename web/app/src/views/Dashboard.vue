<template>
  <div class="dashboard-studio fade-in">
    <!-- Header Section -->
    <div class="studio-header">
      <div class="welcome-text">
        <h1>新项目构建</h1>
        <p>描述您的想法，让智能体团队为您构建一切。</p>
      </div>
      <div class="header-stats">
        <div class="stat-item">
          <span class="stat-value">{{ recentWorkflows.length }}</span>
          <span class="stat-label">近期构建</span>
        </div>
      </div>
    </div>

    <!-- Input Section -->
    <div class="input-section">
      <el-card class="command-card">
        <div class="prompt-container">
          <el-input
            v-model="form.userInput"
            type="textarea"
            :rows="4"
            placeholder="例如：构建一个带用户认证和图书管理的图书系统，后端使用 Spring Boot，前端使用 Vue3..."
            resize="none"
            class="prompt-input"
          />
          <div class="prompt-footer">
            <div class="quick-tips">
              <el-tag
                size="small"
                effect="plain"
                round
                @click="form.userInput = '构建一个简单的待办事项管理系统'"
                >待办事项</el-tag
              >
              <el-tag
                size="small"
                effect="plain"
                round
                @click="form.userInput = '构建一个带权限管理的博客系统'"
                >博客系统</el-tag
              >
            </div>
            <el-button
              type="primary"
              class="build-btn"
              @click="handleSubmit"
              :loading="loading"
            >
              <el-icon v-if="!loading" class="el-icon--left"
                ><MagicStick
              /></el-icon>
              {{ loading ? "智能体协作中..." : "立即构建" }}
            </el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- Progress & Result Section -->
    <transition name="el-fade-in-linear">
      <div v-if="taskId || result" class="execution-section">
        <el-card class="execution-card">
          <template #header>
            <div class="card-header">
              <div class="task-info">
                <el-icon class="loading-icon" v-if="loading"
                  ><Loading
                /></el-icon>
                <el-icon
                  class="success-icon"
                  v-else-if="result?.status === 'completed'"
                  ><CircleCheckFilled
                /></el-icon>
                <span class="task-title"
                  >构建任务: {{ result?.systemName || "正在解析..." }}</span
                >
              </div>
              <el-tag
                :type="getStatusType(result?.status || 'pending')"
                effect="dark"
                round
              >
                {{ result?.status?.toUpperCase() || "PENDING" }}
              </el-tag>
            </div>
          </template>

          <div class="execution-body">
            <!-- Steps Visualization -->
            <div class="progress-container">
              <el-steps
                :active="currentStep"
                finish-status="success"
                align-center
              >
                <el-step title="需求分析" description="Qwen 正在产出 PRD" />
                <el-step title="架构设计" description="系统架构与 API 定义" />
                <el-step title="代码生成" description="后端与前端代码构建" />
                <el-step title="审查修复" description="质量保障与完整性校验" />
              </el-steps>
            </div>

            <!-- Real-time Logs -->
            <div class="status-feed" ref="feedRef">
              <div v-if="result?.errorMsg" class="error-msg">
                <el-alert
                  :title="result.errorMsg"
                  type="error"
                  show-icon
                  :closable="false"
                />
              </div>
              <div
                v-else
                class="success-result"
                v-if="result?.status === 'success'"
              >
                <div class="result-details">
                  <div class="detail-item">
                    <span class="label">系统名称:</span>
                    <span class="value">{{ result.result?.systemName }}</span>
                  </div>
                  <div
                    class="detail-item"
                    v-if="result.result?.savedProjectPath"
                  >
                    <span class="label">已保存路径:</span>
                    <code class="path">{{
                      result.result.savedProjectPath
                    }}</code>
                  </div>
                  <div class="action-bar">
                    <el-button
                      type="primary"
                      @click="viewPreview(result.taskId)"
                    >
                      预览产物 &amp; 确认保存
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-card>
      </div>
    </transition>

    <!-- Recent Projects Grid -->
    <div class="recent-section">
      <div class="section-header">
        <h3>最近构建项目</h3>
        <el-button text @click="router.push('/workflows')">查看全部</el-button>
      </div>

      <el-row :gutter="20">
        <el-col :span="8" v-for="item in recentWorkflows" :key="item.id">
          <el-card
            class="project-item"
            shadow="hover"
            @click="viewWorkflow(item.id)"
          >
            <div class="project-icon">
              <el-icon :size="24" color="#6366f1"><Collection /></el-icon>
            </div>
            <div class="project-info">
              <h4>{{ item.workflowName }}</h4>
              <p>{{ item.createdTime }}</p>
            </div>
            <div class="project-status">
              <el-tag :type="getStatusType(item.status)" size="small" round>{{
                item.status
              }}</el-tag>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8" v-if="recentWorkflows.length === 0">
          <div class="empty-projects">
            <el-empty description="暂无构建记录" :image-size="60" />
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  MagicStick,
  Loading,
  CircleCheckFilled,
  Collection,
} from "@element-plus/icons-vue";
import { taskApi, workflowApi } from "../api";

const router = useRouter();
const loading = ref(false);
const taskId = ref("");
const result = ref(null);
const recentWorkflows = ref([]);
const feedRef = ref(null);

const form = ref({
  userInput: "",
});

const currentStep = computed(() => {
  if (!result.value) return 0;
  const status = result.value.status;
  if (status === "completed") return 4;
  if (status === "failed") return 0;
  // 这里可以根据更细粒度的进度来映射
  return 2;
});

const handleSubmit = async () => {
  if (!form.value.userInput.trim()) {
    ElMessage.warning("请输入您的创意");
    return;
  }

  loading.value = true;
  taskId.value = "";
  result.value = null;

  try {
    const res = await taskApi.submit(form.value.userInput);
    taskId.value = res.data.taskId;
    ElMessage.success("智能体团队已就绪，开始构建...");
    pollResult();
  } catch (error) {
    console.error(error);
    loading.value = false;
  }
};

const pollResult = () => {
  const timer = setInterval(async () => {
    try {
      const res = await taskApi.getResult(taskId.value);
      result.value = res.data;

      if (res.data.status === "completed" || res.data.status === "failed") {
        clearInterval(timer);
        loading.value = false;
        loadRecentWorkflows();
      }
    } catch (error) {
      clearInterval(timer);
      loading.value = false;
    }
  }, 3000);
};

const loadRecentWorkflows = async () => {
  try {
    const res = await workflowApi.getPage({ pageNum: 1, pageSize: 6 });
    recentWorkflows.value = res.data.records;
  } catch (error) {
    console.error(error);
  }
};

const viewPreview = (taskId) => {
  router.push(`/preview/${taskId}`);
};

const viewWorkflow = (id) => {
  router.push(`/workflow/${id}`);
};

const getStatusType = (status) => {
  const map = {
    pending: "info",
    running: "warning",
    completed: "success",
    failed: "danger",
  };
  return map[status] || "info";
};

onMounted(() => {
  loadRecentWorkflows();
});
</script>

<style scoped>
.dashboard-studio {
  max-width: 1100px;
  margin: 0 auto;
}

.studio-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 32px;
}

.welcome-text h1 {
  font-size: 2rem;
  font-weight: 800;
  margin: 0 0 8px 0;
  background: linear-gradient(to right, #1e293b, #6366f1);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.welcome-text p {
  color: var(--text-secondary);
  font-size: 1.1rem;
  margin: 0;
}

.header-stats {
  display: flex;
  gap: 24px;
}

.stat-item {
  text-align: right;
}

.stat-value {
  display: block;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--primary-color);
}

.stat-label {
  font-size: 0.875rem;
  color: var(--text-secondary);
}

.command-card {
  padding: 8px;
  border: 2px solid #eef2ff;
}

.prompt-container {
  display: flex;
  flex-direction: column;
}

.prompt-input :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  font-size: 1.1rem;
  padding: 16px;
  background: transparent;
}

.prompt-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-top: 1px solid #f1f5f9;
}

.quick-tips {
  display: flex;
  gap: 8px;
}

.quick-tips .el-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.quick-tips .el-tag:hover {
  background-color: #eef2ff;
  border-color: var(--primary-color);
}

.build-btn {
  height: 44px;
  padding: 0 24px;
  font-size: 1rem;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.2);
}

.execution-section {
  margin-top: 32px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-info {
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 600;
}

.loading-icon {
  animation: rotate 2s linear infinite;
  color: var(--primary-color);
}

.success-icon {
  color: #10b981;
}

.execution-body {
  padding: 20px 0;
}

.progress-container {
  margin-bottom: 40px;
}

.success-result {
  background-color: #f0fdf4;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #dcfce7;
}

.detail-item {
  margin-bottom: 16px;
}

.detail-item .label {
  font-weight: 600;
  color: #166534;
  margin-right: 8px;
}

.detail-item .path {
  background: #fff;
  padding: 4px 8px;
  border-radius: 4px;
  border: 1px solid #bbf7d0;
  font-family: monospace;
}

.recent-section {
  margin-top: 48px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.project-item {
  cursor: pointer;
  margin-bottom: 20px;
  position: relative;
}

.project-item :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 16px;
}

.project-icon {
  width: 48px;
  height: 48px;
  background-color: #f5f3ff;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.project-info h4 {
  margin: 0 0 4px 0;
  font-size: 1rem;
  color: var(--text-primary);
}

.project-info p {
  margin: 0;
  font-size: 0.8125rem;
  color: var(--text-secondary);
}

.project-status {
  position: absolute;
  top: 12px;
  right: 12px;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
