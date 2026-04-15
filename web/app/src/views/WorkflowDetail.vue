<template>
  <div class="workflow-studio fade-in">
    <div class="studio-top">
      <div class="back-action">
        <el-button @click="$router.back()" circle>
          <el-icon><Back /></el-icon>
        </el-button>
        <span class="studio-title"
          >构建报告 / {{ workflow?.workflowName || "加载中..." }}</span
        >
      </div>
      <div class="top-actions" v-if="workflow">
        <el-tag :type="getStatusType(workflow.status)" effect="dark" round>
          {{ getStatusText(workflow.status) }}
        </el-tag>
        <el-tag
          v-if="workflow.currentStep && workflow.status === 'running'"
          type="warning"
          plain
          round
          style="margin-left: 8px"
        >
          {{ workflow.currentStep }}
        </el-tag>
        <el-button
          v-if="workflow.taskId && workflow.status === 'completed'"
          type="primary"
          size="small"
          style="margin-left: 12px"
          @click="router.push(`/preview/${workflow.taskId}`)"
        >
          预览产物
        </el-button>
        <el-button
          v-if="['completed', 'failed', 'cancelled'].includes(workflow.status)"
          type="danger"
          size="small"
          plain
          style="margin-left: 12px"
          @click="handleDelete"
        >
          删除记录
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="studio-content">
      <el-row :gutter="24">
        <!-- Sidebar: Steps & Timeline -->
        <el-col :span="8">
          <el-card class="timeline-card">
            <template #header>
              <div class="card-header">
                <span class="header-title">执行轨迹</span>
                <span class="header-subtitle">{{ steps.length }} 个节点</span>
              </div>
            </template>

            <el-timeline class="custom-timeline">
              <el-timeline-item
                v-for="step in steps"
                :key="step.id"
                :timestamp="step.startTime"
                :type="getStepType(step.status)"
                :hollow="step.status === 'running'"
              >
                <div
                  class="step-node"
                  :class="{ 'is-active': activeStepId === step.id }"
                  @click="activeStepId = step.id"
                >
                  <div class="step-info">
                    <h4>{{ step.stepName }}</h4>
                    <el-tag
                      :type="getStatusType(step.status)"
                      size="small"
                      plain
                      >{{ getStatusText(step.status) }}</el-tag
                    >
                  </div>
                </div>
              </el-timeline-item>
            </el-timeline>
          </el-card>

          <el-card class="artifacts-card">
            <template #header>
              <div class="card-header">
                <span class="header-title">交付产物</span>
              </div>
            </template>
            <div class="artifact-list">
              <div
                v-for="item in artifacts"
                :key="item.id"
                class="artifact-item"
              >
                <div class="artifact-icon">
                  <el-icon :size="20"><Document /></el-icon>
                </div>
                <div class="artifact-info">
                  <span class="name">{{ item.name }}</span>
                  <span class="meta"
                    >{{ formatFileSize(item.fileSize) }} ·
                    {{ item.artifactType }}</span
                  >
                </div>
                <el-button
                  link
                  type="primary"
                  @click="downloadArtifact(item.id, item.name)"
                >
                  下载
                </el-button>
              </div>
              <el-empty
                v-if="artifacts.length === 0"
                description="暂无产物"
                :image-size="40"
              />
            </div>
          </el-card>
        </el-col>

        <!-- Main Area: Content Rendering -->
        <el-col :span="16">
          <el-card class="detail-display-card">
            <template #header>
              <div class="display-header">
                <span class="display-title">{{
                  activeStep?.stepName || "选择步骤查看详情"
                }}</span>
                <div class="display-meta" v-if="activeStep">
                  <el-icon><Timer /></el-icon>
                  <span>开始: {{ activeStep.startTime }}</span>
                </div>
              </div>
            </template>

            <div class="display-body" v-if="activeStep">
              <div v-if="activeStep.outputData">
                <div
                  v-if="isMarkdown(activeStep.outputData)"
                  class="markdown-content"
                >
                  <vue-markdown :source="activeStep.outputData" />
                </div>
                <div v-else class="raw-content">
                  <pre>{{ activeStep.outputData }}</pre>
                </div>
              </div>
              <el-empty v-else description="该步骤暂无输出数据" />
            </div>
            <el-empty v-else description="点击左侧轨迹查看具体输出" />
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { Back, Document, Timer } from "@element-plus/icons-vue";
import { workflowApi, artifactApi } from "../api";
import VueMarkdown from "vue3-markdown-it";

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const workflow = ref(null);
const steps = ref([]);
const artifacts = ref([]);
const activeStepId = ref(null);

let pollTimer = null;

const activeStep = computed(() => {
  return steps.value.find((s) => s.id === activeStepId.value) || steps.value[0];
});

const loadWorkflowDetail = async () => {
  loading.value = true;
  try {
    const id = route.params.id;
    const [workflowRes, stepsRes, artifactsRes] = await Promise.all([
      workflowApi.getById(id),
      workflowApi.getSteps(id),
      workflowApi.getArtifacts(id),
    ]);

    workflow.value = workflowRes.data;
    steps.value = stepsRes.data;
    artifacts.value = artifactsRes.data;

    if (steps.value.length > 0 && !activeStepId.value) {
      activeStepId.value = steps.value[steps.value.length - 1].id;
    }
  } catch (error) {
    console.error(error);
    ElMessage.error("详情加载失败");
  } finally {
    loading.value = false;
  }
};

/** 轮询：运行中每 5 秒刷新一次步骤列表和工作流状态 */
const startPolling = () => {
  pollTimer = setInterval(async () => {
    try {
      const id = route.params.id;
      const [wfRes, stepsRes] = await Promise.all([
        workflowApi.getById(id),
        workflowApi.getSteps(id),
      ]);
      workflow.value = wfRes.data;
      steps.value = stepsRes.data;
      // 自动定位到最新步骤
      if (steps.value.length > 0) {
        activeStepId.value = steps.value[steps.value.length - 1].id;
      }
      if (wfRes.data.status !== "running") {
        stopPolling();
      }
    } catch (e) {
      console.error("轮询失败", e);
    }
  }, 5000);
};

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
};

const isMarkdown = (content) => {
  return (
    content &&
    (content.includes("# ") ||
      content.includes("##") ||
      content.includes("```"))
  );
};

const downloadArtifact = async (id, filename) => {
  try {
    const res = await artifactApi.download(id);
    const blob = new Blob([res]);
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    link.click();
    window.URL.revokeObjectURL(url);
    ElMessage.success("下载已开始");
  } catch (error) {
    console.error(error);
  }
};

const formatFileSize = (bytes) => {
  if (!bytes) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
};

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm(
      "确定要删除该记录吗？删除后关联的所有产物和步骤都将不可恢复。",
      "删除确认",
      {
        confirmButtonText: "删除",
        cancelButtonText: "取消",
        confirmButtonClass: "el-button--danger",
        type: "error",
      },
    );

    await workflowApi.delete(workflow.value.id);
    ElMessage.success("删除成功");
    router.push("/workflows");
  } catch (error) {
    if (error !== "cancel") {
      console.error(error);
      ElMessage.error("删除失败");
    }
  }
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

const getStatusText = (status) => {
  const map = {
    pending: "等待中",
    running: "正在执行",
    completed: "执行成功",
    failed: "执行失败",
    cancelled: "已取消",
  };
  return map[status] || status;
};

const getStepType = (status) => {
  const map = { completed: "success", failed: "danger", running: "primary" };
  return map[status] || "info";
};

onMounted(async () => {
  await loadWorkflowDetail();
  if (workflow.value?.status === "running") {
    startPolling();
  }
});

onUnmounted(() => {
  stopPolling();
});
</script>

<style scoped>
.workflow-studio {
  max-width: 1400px;
  margin: 0 auto;
}

.studio-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.back-action {
  display: flex;
  align-items: center;
  gap: 16px;
}

.studio-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--text-primary);
}

.card-header {
  display: flex;
  flex-direction: column;
}

.header-title {
  font-weight: 700;
  font-size: 1rem;
  color: var(--text-primary);
}

.header-subtitle {
  font-size: 0.75rem;
  color: var(--text-secondary);
}

.timeline-card,
.artifacts-card {
  margin-bottom: 24px;
}

.custom-timeline {
  padding: 10px 5px;
}

.step-node {
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.step-node:hover {
  background-color: #f8fafc;
}

.step-node.is-active {
  background-color: #eef2ff;
  border-color: #e0e7ff;
}

.step-info h4 {
  margin: 0 0 8px 0;
  font-size: 0.9375rem;
  color: var(--text-primary);
}

.artifact-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.artifact-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px;
  border-radius: 6px;
  border: 1px solid var(--border-color);
}

.artifact-icon {
  width: 36px;
  height: 36px;
  background-color: #f1f5f9;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
}

.artifact-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.artifact-info .name {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-primary);
}

.artifact-info .meta {
  font-size: 0.75rem;
  color: var(--text-secondary);
}

.detail-display-card {
  height: calc(100vh - 180px);
  display: flex;
  flex-direction: column;
}

.detail-display-card :deep(.el-card__body) {
  flex: 1;
  overflow-y: auto;
  padding: 32px;
}

.display-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.display-title {
  font-weight: 700;
  font-size: 1.125rem;
}

.display-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8125rem;
  color: var(--text-secondary);
}

.markdown-content {
  line-height: 1.8;
  color: #334155;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3) {
  color: var(--text-primary);
  margin-top: 24px;
}

.raw-content pre {
  background-color: #f8fafc;
  padding: 20px;
  border-radius: 12px;
  font-family: "Fira Code", monospace;
  font-size: 0.875rem;
  border: 1px solid var(--border-color);
  overflow-x: auto;
}
</style>
