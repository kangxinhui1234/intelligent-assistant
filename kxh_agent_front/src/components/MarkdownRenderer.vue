<template>
  <div class="markdown-content" v-html="renderedMarkdown"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'

const props = defineProps<{
  content: string
}>()

// 配置marked选项
marked.setOptions({
  breaks: true, // 支持换行
  gfm: true, // 支持GitHub风格的markdown
  sanitize: false, // 允许HTML（在安全环境下）
  headerIds: false, // 不生成标题ID
  mangle: false, // 不混淆HTML
})

const renderedMarkdown = computed(() => {
  return marked(props.content)
})
</script>

<style scoped>
.markdown-content {
  line-height: 1.7;
  color: #374151;
  font-size: 14px;
}

/* 标题样式 */
.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin: 20px 0 12px 0;
  font-weight: 600;
  line-height: 1.3;
  color: #1f2937;
}

.markdown-content :deep(h1) {
  font-size: 1.5em;
  border-bottom: 1px solid #eaecef;
  padding-bottom: 8px;
}

.markdown-content :deep(h2) {
  font-size: 1.3em;
  border-bottom: 1px solid #eaecef;
  padding-bottom: 6px;
}

.markdown-content :deep(h3) {
  font-size: 1.1em;
}

/* 段落样式 */
.markdown-content :deep(p) {
  margin: 12px 0;
  line-height: 1.7;
}

/* 列表样式 */
.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 12px 0;
  padding-left: 24px;
}

.markdown-content :deep(li) {
  margin: 6px 0;
  line-height: 1.6;
}

/* 代码样式 */
.markdown-content :deep(code) {
  background-color: #f1f5f9;
  border-radius: 4px;
  font-size: 0.9em;
  padding: 3px 6px;
  font-family: 'SF Mono', 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  color: #e11d48;
  border: 1px solid #e2e8f0;
}

.markdown-content :deep(pre) {
  background-color: #f8fafc;
  border-radius: 8px;
  padding: 16px 20px;
  overflow-x: auto;
  margin: 16px 0;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.markdown-content :deep(pre code) {
  background: none;
  padding: 0;
  font-size: 0.9em;
}

/* 引用样式 */
.markdown-content :deep(blockquote) {
  border-left: 4px solid #3b82f6;
  color: #64748b;
  margin: 16px 0;
  padding: 12px 20px;
  background-color: #f8fafc;
  border-radius: 0 8px 8px 0;
  font-style: italic;
  line-height: 1.6;
}

/* 表格样式 */
.markdown-content :deep(table) {
  border-collapse: collapse;
  margin: 16px 0;
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 12px 16px;
  text-align: left;
  line-height: 1.5;
}

.markdown-content :deep(th) {
  background-color: #f8fafc;
  font-weight: 600;
  color: #374151;
}

/* 链接样式 */
.markdown-content :deep(a) {
  color: #3b82f6;
  text-decoration: none;
  font-weight: 500;
  border-bottom: 1px solid transparent;
  transition: all 0.2s ease;
  cursor: pointer;
}

.markdown-content :deep(a:hover) {
  color: #1d4ed8;
  border-bottom-color: #3b82f6;
}

/* 确保所有链接都在新窗口打开 */
.markdown-content :deep(a) {
  target: _blank;
  rel: noopener noreferrer;
}

/* 分割线样式 */
.markdown-content :deep(hr) {
  border: none;
  border-top: 2px solid #e2e8f0;
  margin: 24px 0;
  border-radius: 1px;
}

/* 强调样式 */
.markdown-content :deep(strong) {
  font-weight: 600;
}

.markdown-content :deep(em) {
  font-style: italic;
}

/* 删除线样式 */
.markdown-content :deep(del) {
  text-decoration: line-through;
  color: #6a737d;
}
</style>
