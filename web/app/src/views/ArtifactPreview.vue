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
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="loading-wrap">
      <el-skeleton :rows="10" animated />
    </div>

    <!-- Error -->
    <el-alert v-else-if="error" :title="error" type="error" show-icon :closable="false" />

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
import { ElMessage } from 'element-plus'
import {
  Back, Download, FolderOpened, Folder,
  Document, CopyDocument
} from '@element-plus/icons-vue'
import { taskApi } from '../api'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const saving = ref(false)
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
const fileCount = computed(() => Object.keys(projectFiles.value).length)
const canSave = computed(() => task.value?.status === 'success' && fileCount.value > 0)

const activeContent = computed(() => {
  if (!activeFile.value) return ''
  return projectFiles.value[activeFile.value] || ''
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

  const sortedPaths = Object.keys(projectFiles.value).sort()
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

onMounted(async () => {
  try {
    const res = await taskApi.getResult(route.params.taskId)
    task.value = res.data
    if (task.value?.result?.savedProjectPath) {
      savedPath.value = task.value.result.savedProjectPath
    }
    // Auto-select first file
    const files = Object.keys(projectFiles.value)
    if (files.length > 0) {
      // Prefer README or first md
      const readme = files.find(f => f.toLowerCase().includes('readme'))
      activeFile.value = readme || files[0]
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
</style>
