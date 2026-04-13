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
                  v-else-if="result?.status === 'success'"
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
                {{ getStatusLabel(result?.status) || "PENDING" }}
              </el-tag>
              <!-- 取消按钮：仅在任务进行中时显示 -->
              <el-button
                v-if="taskId && (result?.status === 'pending' || result?.status === 'running')"
                type="danger"
                plain
                size="small"
                :loading="cancelling"
                @click="handleCancel"
                style="margin-left: 12px;"
              >
                取消任务
              </el-button>
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
              <!-- 进度消息（运行中时显示） -->
              <div v-if="result?.status === 'running' || result?.status === 'pending'" class="progress-msg">
                <el-icon class="spin-icon"><Loading /></el-icon>
                <span>{{ result?.message || '正在处理...' }}</span>
                <el-button
                  v-if="result?.workflowId"
                  text
                  type="primary"
                  size="small"
                  style="margin-left:auto; flex-shrink:0;"
                  @click="router.push(`/workflow/${result.workflowId}`)"
                >
                  实时日志
                </el-button>
              </div>
              <div v-if="result?.status === 'failed'" class="error-msg">
                <el-alert
                  :title="result.message || '生成失败'"
                  type="error"
                  show-icon
                  :closable="false"
                />
              </div>
              <div v-else-if="result?.status === 'cancelled'" class="error-msg">
                <el-alert
                  title="任务已取消"
                  description="工作流已在当前步骤完成后停止，您可以重新提交构建请求。"
                  type="info"
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
                  <!-- 审查警告：代码审查轮次用完后仍有未解决问题 -->
                  <div v-if="result.result?.errorMsg" class="review-warning">
                    <el-alert
                      title="代码审查提示（建议手动确认）"
                      :description="result.result.errorMsg"
                      type="warning"
                      show-icon
                      :closable="false"
                      style="margin-bottom: 16px;"
                    />
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
                    <el-button
                      v-if="result.result?.workflowId"
                      text
                      type="primary"
                      size="small"
                      @click="router.push(`/workflow/${result.result.workflowId}`)"
                    >
                      查看构建详情
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
import { ref, onMounted, onUnmounted, computed } from "vue";
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
const cancelling = ref(false);
const taskId = ref("");
const result = ref(null);
const recentWorkflows = ref([]);
const feedRef = ref(null);

// SSE 连接句柄，用于组件销毁时清理
let eventSource = null;

// localStorage key：记录进行中的任务，切页面后返回时恢复
const ACTIVE_TASK_KEY = "agentai_active_task";

const saveActiveTask = (id) => {
  localStorage.setItem(ACTIVE_TASK_KEY, id);
};

const clearActiveTask = () => {
  localStorage.removeItem(ACTIVE_TASK_KEY);
};

const getStoredTaskId = () => localStorage.getItem(ACTIVE_TASK_KEY) || null;

const form = ref({
  userInput: "",
});

const currentStep = computed(() => {
  if (!result.value) return 0;
  const status = result.value.status;
  const msg = result.value.message || '';
  if (status === 'success') return 4;
  if (status === 'failed') return 0;
  if (msg.includes('架构')) return 1;
  if (msg.includes('代码') || msg.includes('骨架') || msg.includes('Controller') || msg.includes('前端')) return 2;
  if (msg.includes('审查') || msg.includes('修复') || msg.includes('完整性')) return 3;
  return 1;
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
    saveActiveTask(res.data.taskId); // 持久化，切页面后可恢复
    ElMessage.success("智能体团队已就绪，开始构建...");
    connectSSE(res.data.taskId);
  } catch (error) {
    console.error(error);
    loading.value = false;
  }
};

/**
 * 通过 SSE 订阅任务进度。
 * 服务端每 3 秒推送一次 "update" 事件；任务终态后服务端关闭连接。
 * EventSource 在网络短暂中断时会自动重连，无需手动处理。
 */
const connectSSE = (id) => {
  closeSSE(); // 关闭旧连接（如有）

  const url = `http://localhost:8080/api/agent/task/${id}/stream`;
  const es = new EventSource(url);
  eventSource = es;

  es.addEventListener("update", (e) => {
    try {
      const data = JSON.parse(e.data);
      result.value = data;
      const status = data.status;
      if (status === "success" || status === "failed" || status === "cancelled") {
        loading.value = false;
        cancelling.value = false;
        clearActiveTask(); // 任务结束，清除持久化记录
        closeSSE();
        loadRecentWorkflows();
      }
    } catch (err) {
      console.error("SSE parse error:", err);
    }
  });

  es.addEventListener("error", () => {
    if (!eventSource || es.readyState === EventSource.CLOSED) {
      // 已主动关闭（导航离开）或服务端正常结束流，忽略
      return;
    }
    // readyState === CONNECTING：网络短暂中断，浏览器正在自动重连，无需干预
  });
};

const closeSSE = () => {
  if (eventSource) {
    eventSource.close();
    eventSource = null;
  }
};

/**
 * 挂载时恢复进行中的任务。
 * 若 localStorage 中有 taskId，先通过 REST 拉取当前状态，
 * 若任务仍在进行则重新建立 SSE 连接；已终止则直接展示结果。
 */
onMounted(async () => {
  loadRecentWorkflows();

  const storedId = getStoredTaskId();
  if (!storedId) return;

  taskId.value = storedId;
  loading.value = true;

  try {
    const res = await taskApi.getResult(storedId);
    const task = res.data;

    if (!task) {
      // Redis 中已过期
      clearActiveTask();
      loading.value = false;
      return;
    }

    result.value = task;
    const status = task.status;

    if (status === "success" || status === "failed" || status === "cancelled") {
      // 离开期间任务已完成
      loading.value = false;
      clearActiveTask();
    } else {
      // 仍在运行，重新连接 SSE
      connectSSE(storedId);
    }
  } catch {
    loading.value = false;
    clearActiveTask();
  }
});

onUnmounted(() => {
  closeSSE(); // 仅关闭连接，不清除 localStorage，返回时可恢复
});

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

const handleCancel = async () => {
  if (!taskId.value) return;
  cancelling.value = true;
  try {
    await taskApi.cancel(taskId.value);
    ElMessage.info("取消请求已发送，任务将在当前步骤完成后停止");
  } catch (error) {
    cancelling.value = false;
    console.error(error);
  }
};

const getStatusLabel = (status) => {
  const map = {
    pending: "等待中",
    running: "运行中",
    success: "已完成",
    failed: "已失败",
    cancelled: "已取消",
  };
  return map[status] || (status?.toUpperCase() ?? "PENDING");
};

const getStatusType = (status) => {
  const map = {
    pending: "info",
    running: "warning",
    success: "success",
    completed: "success",
    cancelled: "info",
    failed: "danger",
  };
  return map[status] || "info";
};
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

.progress-msg {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  background-color: #f0f4ff;
  border-radius: 10px;
  border: 1px solid #c7d2fe;
  color: #4338ca;
  font-size: 0.95rem;
}

.spin-icon {
  animation: rotate 1.5s linear infinite;
  color: #6366f1;
  flex-shrink: 0;
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
