<script setup>
import { ref, computed, watch, onMounted, nextTick } from "vue";
import Vue3MarkdownIt from "vue3-markdown-it";
import markdownIt from "markdown-it";
import markdownItMermaid from "markdown-it-mermaid-plugin";
import mermaid from "mermaid";
import hljs from "highlight.js";
import "highlight.js/styles/github.css";

mermaid.initialize({
  theme: "default",
  startOnLoad: false,
});

const requirement = ref("");
const loading = ref(false);
const error = ref("");
const markdownContent = ref("");
const docId = ref("");
const storagePath = ref("");

const md = markdownIt({
  html: true,
  linkify: true,
  typographer: true,
  highlight: function (str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return (
          '<pre class="hljs"><code>' +
          hljs.highlight(str, { language: lang, ignoreIllegals: true }).value +
          "</code></pre>"
        );
      } catch (__) {}
    }
    return (
      '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + "</code></pre>"
    );
  },
}).use(markdownItMermaid, { mermaid });

const markdownOptions = ref({
  markdownIt: md,
});

function renderMermaid() {
  nextTick(() => {
    const nodes = document.querySelectorAll(".mermaid");
    nodes.forEach((el, idx) => {
      const code = el.textContent || "";
      const id = `mermaid-${Date.now()}-${idx}`;
      el.setAttribute("id", id);
      try {
        mermaid.render(id, code, (svg) => {
          el.innerHTML = svg;
        });
      } catch (e) {
        console.error(e);
      }
    });
  });
}

watch(markdownContent, () => {
  renderMermaid();
});

async function generate() {
  error.value = "";
  loading.value = true;
  markdownContent.value = "";
  docId.value = "";
  storagePath.value = "";
  try {
    const resp = await fetch("http://localhost:8080/api/requirement/generate", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ requirementDescription: requirement.value }),
    });
    if (!resp.ok) {
      const text = await resp.text();
      throw new Error(text || "请求失败");
    }
    const data = await resp.json();
    docId.value = data.documentId || "";
    storagePath.value = data.storagePath || "";
    markdownContent.value = markdownify(data.documentContent || "");
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  renderMermaid();
});

function markdownify(text) {
  if (!text) return "";
  const hasMd =
    /(^|\\s)(#|\\*|\\-|`|\\||>)/.test(text) || /```|mermaid|\\n\\n/.test(text);
  if (hasMd) return text;
  let t = text.trim();
  t = t.replace(
    /(需求背景与目标|目标用户与核心场景|核心功能需求|非功能需求|需求优先级|验收标准|数据字典)/g,
    "## $1\\n",
  );
  t = t.replace(/([。；？！])/g, "$1\\n");
  t = `# ${requirement.value || "需求分析文档"}\\n\\n` + t;
  t = t.replace(/\\n(?!\\n)/g, "\\n\\n");
  return t;
}
</script>

<template>
  <div class="wrap">
    <header class="head">
      <h1>多智能体协调开发系统原型（Vite + Vue3）</h1>
    </header>
    <main class="grid">
      <section class="panel">
        <h2>需求输入</h2>
        <textarea
          v-model="requirement"
          placeholder="输入系统需求..."
        ></textarea>
        <button :disabled="loading || !requirement" @click="generate">
          生成文档
        </button>
        <div v-if="error" class="error">{{ error }}</div>
        <div v-if="docId" class="meta">
          <span>文档ID: {{ docId }}</span>
          <span v-if="storagePath">存储路径: {{ storagePath }}</span>
        </div>
      </section>
      <section class="panel">
        <h2>AI文档预览（支持 Mermaid）</h2>
        <Vue3MarkdownIt :source="markdownContent" :options="markdownOptions" />
      </section>
    </main>
  </div>
</template>

<style scoped>
.wrap {
  background: #f6f7f9;
  min-height: 100vh;
}
.head {
  background: #0f62fe;
  color: #fff;
  padding: 16px 24px;
}
.head h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.grid {
  max-width: 1080px;
  margin: 24px auto;
  padding: 0 16px;
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}
.panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
}
.panel h2 {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
}
textarea {
  width: 100%;
  min-height: 120px;
  padding: 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  resize: vertical;
  font-size: 14px;
}
button {
  margin-top: 12px;
  padding: 10px 16px;
  background: #0f62fe;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
}
button[disabled] {
  background: #9aa1af;
  cursor: not-allowed;
}
.error {
  margin-top: 8px;
  color: #b00020;
  font-size: 13px;
}
.meta {
  display: flex;
  gap: 24px;
  padding: 8px 0;
  font-size: 13px;
  color: #444;
}
.hljs {
  background: #0b1020;
  color: #f8f8f2;
  padding: 12px;
  border-radius: 6px;
}
.mermaid {
  background: #f8f9fa;
  padding: 16px;
  border-radius: 8px;
  margin: 16px 0;
}
@media (min-width: 960px) {
  .grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
