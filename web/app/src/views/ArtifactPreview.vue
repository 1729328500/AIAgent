<template>
  <div class="preview-studio fade-in">
    <!-- Header -->
    <div class="studio-top">
      <div class="back-action">
        <el-button @click="$router.back()" circle>
          <el-icon><Back /></el-icon>
        </el-button>
        <div class="title-block">
          <span class="studio-title">产物预览 / {{ systemName || '加载中...' }}</span>
          <span class="studio-sub">确认内容后点击「保存到本地」</span>
        </div>
      </div>
      <div class="top-actions">
        <el-tag v-if="savedPath" type="success" effect="dark" round>已保存</el-tag>
        <el-button
          v-else
          type="primary"
          :loading="saving"
          @click="handleSave"
          :disabled="!canSave"
        >
          <el-icon class="el-icon--left"><Download /></el-icon>
          保存到本地
        </el-button>

        <!-- E2B 沙箱部署按钮 -->
        <el-button
          v-if="canSave && task?.sandboxStatus !== 'running'"
          type="warning"
          :loading="deploying"
          @click="handleDeploy"
          :disabled="deploying"
        >
          <el-icon class="el-icon--left"><Monitor /></el-icon>
          {{ deploying ? '部署中...' : (task?.sandboxStatus === 'failed' ? '重新部署' : '部署到沙箱预览') }}
        </el-button>
        <template v-if="task?.sandboxStatus === 'running' && task?.sandboxUrl">
          <el-button type="success" @click="openSandbox">
            <el-icon class="el-icon--left"><Link /></el-icon>
            打开沙箱预览
          </el-button>
          <el-button type="danger" plain :loading="killing" @click="handleKill">
            <el-icon class="el-icon--left"><SwitchButton /></el-icon>
            关闭沙箱
          </el-button>
          <el-button type="warning" plain :loading="deploying" @click="handleRedeploy" :disabled="killing">
            <el-icon class="el-icon--left"><Monitor /></el-icon>
            重新部署
          </el-button>
        </template>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="loading-wrap">
      <el-skeleton :rows="10" animated />
    </div>

    <!-- Error -->
    <el-alert v-else-if="error" :title="error" type="error" show-icon :closable="false" />

    <!-- Not Found -->
    <el-empty v-else-if="!task" description="任务不存在或已过期">
      <el-button type="primary" @click="$router.push('/dashboard')">返回工作台</el-button>
    </el-empty>

    <!-- Main Content -->
    <div v-else-if="task" class="preview-body">
      <!-- Saved path banner -->
      <el-alert
        v-if="savedPath"
        :title="`项目已保存至: ${savedPath}`"
        type="success"
        show-icon
        :closable="false"
        class="saved-banner"
      />

      <!-- Sandbox deploy banner -->
      <div v-if="task?.sandboxStatus && task.sandboxStatus !== 'none'" class="sandbox-banner">
        <!-- Deploying state -->
        <el-alert
          v-if="task.sandboxStatus === 'deploying'"
          title="正在部署到 E2B 沙箱..."
          description="正在上传文件并启动 Vite 开发服务器，请稍候"
          type="info"
          show-icon
          :closable="false"
        />
        <!-- Failed state -->
        <el-alert
          v-else-if="task.sandboxStatus === 'failed'"
          title="沙箱部署失败"
          :description="task.sandboxError || '未知错误'"
          type="error"
          show-icon
          :closable="false"
        />
        <!-- Running state -->
        <el-card v-else-if="task.sandboxStatus === 'running'" class="sandbox-card" shadow="never">
          <div class="sandbox-info">
            <div class="sandbox-left">
              <el-icon class="sandbox-icon" color="#67c23a"><Monitor /></el-icon>
              <div class="sandbox-text">
                <div class="sandbox-title">沙箱部署成功</div>
                <div class="sandbox-url">
                  <a :href="task.sandboxUrl" target="_blank" rel="noopener">{{ task.sandboxUrl }}</a>
                </div>
                <div class="sandbox-tip">
                  npm install 正在后台运行，请等待约 <strong>1-2 分钟</strong>后再访问。沙箱将在 <strong>30 分钟</strong>后自动销毁。
                </div>
              </div>
            </div>
            <div class="sandbox-actions">
              <el-button type="primary" size="small" @click="openSandbox">
                <el-icon><Link /></el-icon> 在新标签页打开
              </el-button>
              <el-button type="default" size="small" @click="copyUrl">
                <el-icon><CopyDocument /></el-icon> 复制链接
              </el-button>
            </div>
          </div>
        </el-card>
      </div>

      <!-- Code review warnings -->
      <el-collapse v-if="reviewWarnings" class="review-warnings-collapse">
        <el-collapse-item name="warnings">
          <template #title>
            <el-tag type="warning" effect="plain" size="small">代码审查警告</el-tag>
            <span style="margin-left: 8px; font-size: 0.8125rem; color: var(--text-secondary)">
              以下问题不影响项目运行，仅供参考（点击展开）
            </span>
          </template>
          <el-alert
            :title="reviewWarnings"
            type="warning"
            :closable="false"
            show-icon
            class="review-warning-alert"
          />
        </el-collapse-item>
      </el-collapse>

      <el-row :gutter="24">
        <!-- Left: File Tree -->
        <el-col :span="6">
          <el-card class="tree-card">
            <template #header>
              <div class="card-header">
                <el-icon><FolderOpened /></el-icon>
                <span>文件目录</span>
                <el-tag size="small" type="info" round>{{ fileCount }} 个文件</el-tag>
              </div>
            </template>
            <el-tree
              :data="fileTree"
              :props="{ label: 'name', children: 'children' }"
              node-key="path"
              default-expand-all
              highlight-current
              @node-click="onNodeClick"
              class="file-tree"
            >
              <template #default="{ node, data }">
                <span class="tree-node">
                  <el-icon v-if="data.isDir" class="dir-icon"><Folder /></el-icon>
                  <el-icon v-else class="file-icon" :class="getFileIconClass(data.name)">
                    <component :is="getFileIcon(data.name)" />
                  </el-icon>
                  <span class="node-label" :title="data.name">{{ data.name }}</span>
                </span>
              </template>
            </el-tree>
          </el-card>
        </el-col>

        <!-- Right: Content Viewer -->
        <el-col :span="18">
          <el-card class="viewer-card">
            <template #header>
              <div class="viewer-header">
                <div class="file-path-bar" v-if="activeFile">
                  <el-breadcrumb separator="/">
                    <el-breadcrumb-item v-for="seg in activePathSegments" :key="seg">{{ seg }}</el-breadcrumb-item>
                  </el-breadcrumb>
                </div>
                <span v-else class="placeholder-hint">← 点击左侧文件查看内容</span>
                <div class="viewer-actions" v-if="activeFile">
                  <el-tag size="small" type="info">{{ getLanguage(activeFile) }}</el-tag>
                </div>
              </div>
            </template>

            <div class="viewer-body" v-if="activeFile && activeContent">
              <!-- Markdown rendering -->
              <div v-if="isMarkdownFile(activeFile)" class="md-render">
                <div class="md-body" v-html="renderedMarkdown"></div>
              </div>
              <!-- Code rendering -->
              <div v-else class="code-render">
                <div class="code-toolbar">
                  <span class="line-count">{{ lineCount }} 行</span>
                  <el-button size="small" text @click="copyCode">
                    <el-icon><CopyDocument /></el-icon> 复制
                  </el-button>
                </div>
                <pre class="code-block"><code :class="`language-${getLanguage(activeFile)}`" v-html="highlightedCode"></code></pre>
              </div>
            </div>
            <el-empty v-else description="选择左侧文件查看内容" :image-size="80" />
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Back, Download, FolderOpened, Folder,
  Document, CopyDocument, Monitor, Link, SwitchButton
} from '@element-plus/icons-vue'
import { taskApi } from '../api'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const saving = ref(false)
const deploying = ref(false)
const killing = ref(false)
const error = ref('')
const task = ref(null)
const activeFile = ref('')
const savedPath = ref('')

// Markdown renderer with highlight.js
const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs-pre"><code class="hljs language-${lang}">${hljs.highlight(str, { language: lang }).value}</code></pre>`
      } catch {}
    }
    return `<pre class="hljs-pre"><code class="hljs">${md.utils.escapeHtml(str)}</code></pre>`
  }
})

const systemName = computed(() => task.value?.result?.systemName || '')
const projectFiles = computed(() => task.value?.result?.projectFiles || {})

// 代码审查警告（从 errorMsg 中提取 WARNING 块）
const reviewWarnings = computed(() => {
  const msg = task.value?.result?.errorMsg || task.value?.message || ''
  if (msg.includes('【代码审查警告')) {
    const start = msg.indexOf('【代码审查警告')
    return msg.substring(start).trim()
  }
  return ''
})

// 合并文档（PRD / 架构）到虚拟文件树，方便统一预览
const allFiles = computed(() => {
  const files = { ...projectFiles.value }
  const prdContent = task.value?.result?.prdContent
  const archContent = task.value?.result?.archContent
  if (prdContent) files['docs/需求分析文档.md'] = prdContent
  if (archContent) files['docs/系统架构文档.md'] = archContent
  return files
})

const fileCount = computed(() => Object.keys(allFiles.value).length)
const canSave = computed(() => task.value?.status === 'success' && fileCount.value > 0)

const activeContent = computed(() => {
  if (!activeFile.value) return ''
  return allFiles.value[activeFile.value] || ''
})

const activePathSegments = computed(() => {
  if (!activeFile.value) return []
  return activeFile.value.split('/')
})

const lineCount = computed(() => {
  if (!activeContent.value) return 0
  return activeContent.value.split('\n').length
})

const renderedMarkdown = computed(() => {
  if (!activeContent.value) return ''
  return md.render(activeContent.value)
})

const highlightedCode = computed(() => {
  if (!activeContent.value) return ''
  const lang = getLanguage(activeFile.value)
  try {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(activeContent.value, { language: lang }).value
    }
  } catch {}
  return hljs.highlightAuto(activeContent.value).value
})

// Build file tree from flat Map keys
const fileTree = computed(() => {
  const root = []
  const dirMap = {}

  const sortedPaths = Object.keys(allFiles.value).sort()
  for (const filePath of sortedPaths) {
    const parts = filePath.split('/')
    let current = root
    let accumulated = ''

    for (let i = 0; i < parts.length; i++) {
      const part = parts[i]
      accumulated = accumulated ? `${accumulated}/${part}` : part
      const isLast = i === parts.length - 1

      if (isLast) {
        current.push({ name: part, path: filePath, isDir: false })
      } else {
        if (!dirMap[accumulated]) {
          const node = { name: part, path: accumulated, isDir: true, children: [] }
          dirMap[accumulated] = node
          current.push(node)
        }
        current = dirMap[accumulated].children
      }
    }
  }
  return root
})

const isMarkdownFile = (path) => path?.endsWith('.md')

const getLanguage = (path) => {
  if (!path) return 'text'
  const ext = path.split('.').pop()?.toLowerCase()
  const map = {
    java: 'java', js: 'javascript', ts: 'typescript', vue: 'xml',
    xml: 'xml', yml: 'yaml', yaml: 'yaml', json: 'json',
    html: 'html', css: 'css', sql: 'sql', md: 'markdown',
    sh: 'bash', properties: 'properties', txt: 'text'
  }
  return map[ext] || 'text'
}

const getFileIcon = (name) => {
  if (!name) return Document
  const ext = name.split('.').pop()?.toLowerCase()
  return Document
}

const getFileIconClass = (name) => {
  if (!name) return ''
  const ext = name.split('.').pop()?.toLowerCase()
  const map = {
    java: 'icon-java', js: 'icon-js', ts: 'icon-ts', vue: 'icon-vue',
    xml: 'icon-xml', yml: 'icon-yaml', yaml: 'icon-yaml', json: 'icon-json',
    md: 'icon-md', css: 'icon-css', html: 'icon-html', sql: 'icon-sql'
  }
  return map[ext] || ''
}

const onNodeClick = (data) => {
  if (!data.isDir) {
    activeFile.value = data.path
  }
}

const copyCode = async () => {
  try {
    await navigator.clipboard.writeText(activeContent.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    const res = await taskApi.saveProject(route.params.taskId)
    savedPath.value = res.data.savedPath
    ElMessage.success(`项目已保存至: ${res.data.savedPath}`)
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.response?.data?.error || e.message))
  } finally {
    saving.value = false
  }
}

const handleRedeploy = async () => {
  // 先关闭旧沙箱，再重新部署
  killing.value = true
  try {
    await taskApi.killSandbox(route.params.taskId)
    if (task.value) { task.value.sandboxStatus = 'none'; task.value.sandboxId = null; task.value.sandboxUrl = null }
  } catch (e) {
    console.warn('关闭旧沙箱失败，继续重新部署:', e.message)
  } finally {
    killing.value = false
  }
  await handleDeploy()
}

const handleKill = async () => {
  try {
    await ElMessageBox.confirm('确定要关闭当前沙箱吗？关闭后预览链接将失效。', '关闭沙箱', {
      confirmButtonText: '关闭', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }

  killing.value = true
  try {
    await taskApi.killSandbox(route.params.taskId)
    if (task.value) {
      task.value.sandboxStatus = 'none'
      task.value.sandboxId = null
      task.value.sandboxUrl = null
    }
    ElMessage.success('沙箱已关闭')
  } catch (e) {
    ElMessage.error('关闭失败: ' + (e.response?.data?.message || e.message))
  } finally {
    killing.value = false
  }
}

const handleDeploy = async () => {
  deploying.value = true
  // Optimistically update UI status
  if (task.value) task.value.sandboxStatus = 'deploying'
  try {
    ElMessage.info('正在部署到 E2B 沙箱，请稍候（预计 30-60 秒）...')
    const res = await taskApi.deployToSandbox(route.params.taskId)
    // Refresh task to get updated sandbox info
    const updated = await taskApi.getResult(route.params.taskId)
    task.value = updated.data
    ElMessage.success(res.data.message || '部署成功！请等待约 1-2 分钟后访问预览链接')
  } catch (e) {
    if (task.value) task.value.sandboxStatus = 'failed'
    ElMessage.error('部署失败: ' + (e.response?.data?.message || e.message))
  } finally {
    deploying.value = false
  }
}

const openSandbox = () => {
  const url = task.value?.sandboxUrl
  if (url) window.open(url, '_blank', 'noopener,noreferrer')
}

const copyUrl = async () => {
  const url = task.value?.sandboxUrl
  if (!url) return
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

onMounted(async () => {
  try {
    const res = await taskApi.getResult(route.params.taskId)
    task.value = res.data
    if (task.value?.result?.savedProjectPath) {
      savedPath.value = task.value.result.savedProjectPath
    }
    // Auto-select first file — prefer docs, then README, then first file
    const files = Object.keys(allFiles.value)
    if (files.length > 0) {
      const prdDoc = files.find(f => f.includes('需求分析文档'))
      const readme = files.find(f => f.toLowerCase().includes('readme'))
      activeFile.value = prdDoc || readme || files[0]
    }
  } catch (e) {
    error.value = '加载失败: ' + e.message
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.preview-studio {
  max-width: 1600px;
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

.title-block {
  display: flex;
  flex-direction: column;
}

.studio-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--text-primary);
}

.studio-sub {
  font-size: 0.8125rem;
  color: var(--text-secondary);
  margin-top: 2px;
}

.loading-wrap {
  padding: 40px;
}

.saved-banner {
  margin-bottom: 20px;
}

.tree-card {
  height: calc(100vh - 180px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.tree-card :deep(.el-card__body) {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.file-tree {
  font-size: 0.8125rem;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
}

.node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dir-icon {
  color: #f59e0b;
}

.file-icon {
  color: #94a3b8;
}

.icon-java { color: #b07219; }
.icon-js { color: #f1e05a; }
.icon-ts { color: #3178c6; }
.icon-vue { color: #41b883; }
.icon-json { color: #f59e0b; }
.icon-md { color: #6366f1; }
.icon-yaml { color: #e34c26; }
.icon-css { color: #563d7c; }
.icon-html { color: #e34c26; }
.icon-sql { color: #336791; }

.viewer-card {
  height: calc(100vh - 180px);
  display: flex;
  flex-direction: column;
}

.viewer-card :deep(.el-card__body) {
  flex: 1;
  overflow: hidden;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.viewer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.file-path-bar {
  font-size: 0.875rem;
}

.placeholder-hint {
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.viewer-body {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}

/* Markdown */
.md-render {
  padding: 32px 40px;
}

.md-body {
  line-height: 1.8;
  color: #334155;
  max-width: 860px;
}

.md-body :deep(h1) { font-size: 1.75rem; font-weight: 800; margin: 0 0 16px; color: #1e293b; border-bottom: 2px solid #e2e8f0; padding-bottom: 8px; }
.md-body :deep(h2) { font-size: 1.375rem; font-weight: 700; margin: 28px 0 12px; color: #1e293b; }
.md-body :deep(h3) { font-size: 1.125rem; font-weight: 600; margin: 20px 0 8px; color: #334155; }
.md-body :deep(p) { margin: 0 0 12px; }
.md-body :deep(ul), .md-body :deep(ol) { padding-left: 24px; margin: 0 0 12px; }
.md-body :deep(li) { margin-bottom: 4px; }
.md-body :deep(code) { background: #f1f5f9; padding: 2px 6px; border-radius: 4px; font-family: 'Fira Code', monospace; font-size: 0.875em; color: #6366f1; }
.md-body :deep(blockquote) { border-left: 4px solid #6366f1; margin: 16px 0; padding: 8px 16px; background: #f8fafc; color: #64748b; }
.md-body :deep(table) { width: 100%; border-collapse: collapse; margin: 16px 0; }
.md-body :deep(th) { background: #f1f5f9; padding: 10px 14px; text-align: left; font-weight: 600; border: 1px solid #e2e8f0; }
.md-body :deep(td) { padding: 8px 14px; border: 1px solid #e2e8f0; }
.md-body :deep(tr:nth-child(even)) { background: #f8fafc; }
.md-body :deep(.hljs-pre) { margin: 16px 0; border-radius: 10px; overflow: hidden; }
.md-body :deep(.hljs) { padding: 20px; font-size: 0.875rem; line-height: 1.6; }

/* Code */
.code-render {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.code-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  font-size: 0.8125rem;
  color: var(--text-secondary);
}

.code-block {
  flex: 1;
  margin: 0;
  padding: 20px 24px;
  overflow: auto;
  background: #1e293b;
  font-family: 'Fira Code', 'Cascadia Code', monospace;
  font-size: 0.875rem;
  line-height: 1.7;
  color: #e2e8f0;
}

.code-block :deep(.hljs) {
  background: transparent;
  padding: 0;
}

/* ── Sandbox banner ─────────────────────────────────── */
.sandbox-banner {
  margin-bottom: 16px;
}

.sandbox-card {
  border: 1px solid #a3d977;
  background: #f0f9eb;
  border-radius: 8px;
}

.sandbox-info {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.sandbox-left {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  flex: 1;
}

.sandbox-icon {
  font-size: 2rem;
  flex-shrink: 0;
  margin-top: 2px;
}

.sandbox-text {
  flex: 1;
}

.sandbox-title {
  font-weight: 700;
  font-size: 0.9375rem;
  color: #3d8e24;
  margin-bottom: 4px;
}

.sandbox-url a {
  font-family: 'Fira Code', monospace;
  font-size: 0.875rem;
  color: #409eff;
  word-break: break-all;
}

.sandbox-tip {
  margin-top: 6px;
  font-size: 0.8125rem;
  color: #5a7a45;
}

.sandbox-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-shrink: 0;
}

/* ── Review warnings ────────────────────────────────── */
.review-warnings-collapse {
  margin-bottom: 16px;
  border: 1px solid #e6a23c44;
  border-radius: 8px;
  overflow: hidden;
}

.review-warning-alert :deep(.el-alert__title) {
  white-space: pre-wrap;
  font-size: 0.8125rem;
  line-height: 1.7;
}
</style>
