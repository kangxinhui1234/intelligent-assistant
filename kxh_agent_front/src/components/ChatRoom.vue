<template>
  <div class="container col" style="gap: 12px; padding-top: 16px;">
    <div class="row" style="justify-content: space-between; align-items: baseline;">
      <h2 style="margin:0">{{ title }}</h2>
      <div class="subtext">会话ID：{{ sessionId }}</div>
    </div>

    <div class="card chat" ref="chatBox">
      <div v-for="m in messages" :key="m.id" class="chat-item" :class="m.role === 'user' ? 'right' : 'left'">
        <div class="bubble" :class="m.role === 'user' ? 'right' : 'left'">
          <template v-if="m.role === 'assistant'">
            <div v-if="m.isStep" class="step-message">
              <div class="step-header">
                <span class="step-number">{{ m.stepNumber }}</span>
                <span class="step-title">{{ m.stepTitle }}</span>
                <span v-if="m.isCompleted" class="step-status completed">✓</span>
                <span v-else-if="m.isError" class="step-status error">✗</span>
                <span v-else class="step-status loading">⏳</span>
              </div>
              <div class="step-content">
                <MarkdownRenderer :content="m.content" />
              </div>
            </div>
            <div v-else class="regular-message">
              <MarkdownRenderer :content="m.content" />
            </div>
          </template>
          <template v-else>
            {{ m.content }}
          </template>
        </div>
      </div>
      <div v-if="loading" class="loading-indicator">
        <div class="loading-dots">
          <span></span>
          <span></span>
          <span></span>
        </div>
        <span class="loading-text">AI正在思考中...</span>
      </div>
    </div>

    <div class="row input-area">
      <input class="input" v-model="input" :disabled="loading" placeholder="请输入消息，回车发送" @keyup.enter="send" />
      <button class="btn" :disabled="!input || loading" @click="send">发送</button>
      <button class="btn outline" :disabled="!loading" @click="stop">停止</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, reactive, ref, watch, nextTick } from 'vue'
import { openSSE } from '../services/sse'
import MarkdownRenderer from './MarkdownRenderer.vue'

interface Message { 
  id: string; 
  role: 'user' | 'assistant'; 
  content: string;
  isStep?: boolean;
  stepNumber?: number;
  stepTitle?: string;
  isCompleted?: boolean;
  isError?: boolean;
}

const props = defineProps<{ title: string; apiEndpoint?: string }>()

const sessionId = ref(generateId())
const input = ref('')
const loading = ref(false)
const messages = reactive<Message[]>([])
let closeFn: null | (() => void) = null
const chatBox = ref<HTMLDivElement | null>(null)

function generateId() {
  return Math.random().toString(36).slice(2) + Date.now().toString(36)
}

function parseStepMessage(chunk: string): Message | null {
  // 解析步骤消息格式: "Step 1: 内容"
  const stepMatch = chunk.match(/^Step (\d+):\s*(.*)$/s)
  
  if (stepMatch) {
    const stepNumber = parseInt(stepMatch[1])
    const content = stepMatch[2].trim()
    
    // 更智能的状态判断
    let isCompleted = false
    let isError = false
    let stepTitle = `步骤 ${stepNumber}`
    
    // 检查完成状态的关键词
    if (content.includes('执行结束') || content.includes('完成') || 
        content.includes('已结束') || content.includes('finish') ||
        content.includes('done') || content.includes('success')) {
      isCompleted = true
      stepTitle = `步骤 ${stepNumber} - 完成`
    } 
    // 检查错误状态的关键词
    else if (content.includes('错误') || content.includes('失败') || 
             content.includes('error') || content.includes('failed') ||
             content.includes('异常') || content.includes('exception')) {
      isError = true
      stepTitle = `步骤 ${stepNumber} - 错误`
    } 
    // 检查工具执行状态
    else if (content.includes('工具') || content.includes('调用') || 
             content.includes('tool') || content.includes('executing') ||
             content.includes('执行工具') || content.includes('调用工具')) {
      stepTitle = `步骤 ${stepNumber} - 执行工具`
    }
    // 检查思考状态
    else if (content.includes('思考') || content.includes('分析') || 
             content.includes('thinking') || content.includes('analyzing') ||
             content.includes('正在') || content.includes('processing')) {
      stepTitle = `步骤 ${stepNumber} - 思考中`
    }
    // 检查是否有具体的结果内容（表示步骤已完成）
    else if (content.length > 50 && (
      content.includes('结果') || content.includes('数据') || 
      content.includes('报告') || content.includes('分析') ||
      content.includes('建议') || content.includes('结论') ||
      content.includes('##') || content.includes('**') ||
      content.includes('表格') || content.includes('图表')
    )) {
      isCompleted = true
      stepTitle = `步骤 ${stepNumber} - 完成`
    }
    // 默认状态
    else {
      stepTitle = `步骤 ${stepNumber} - 进行中`
    }
    
    return {
      id: generateId(),
      role: 'assistant' as const,
      content: content,
      isStep: true,
      stepNumber: stepNumber,
      stepTitle: stepTitle,
      isCompleted: isCompleted,
      isError: isError
    }
  }
  
  // 检查是否是最终结果消息
  if (chunk.includes('执行结束') || chunk.includes('达到最大步骤')) {
    return {
      id: generateId(),
      role: 'assistant' as const,
      content: chunk,
      isStep: false
    }
  }
  
  return null
}

function scrollToBottom() {
  nextTick(() => {
    if (chatBox.value) chatBox.value.scrollTop = chatBox.value.scrollHeight
  })
}

function send() {
  const text = input.value.trim()
  if (!text || loading.value) return

  const user: Message = { id: generateId(), role: 'user', content: text }
  messages.push(user)
  input.value = ''

  const assistant: Message = { id: generateId(), role: 'assistant', content: '' }
  messages.push(assistant)
  loading.value = true
  scrollToBottom()

  // SSE stream
  const endpoint = props.apiEndpoint || '/api/agent/manus/chat'
  closeFn = openSSE(
    `${import.meta.env.VITE_API_BASE}${endpoint}`,
    { message: text, sessionId: sessionId.value },
    (chunk) => { 
      // 解析步骤消息
      const stepMessage = parseStepMessage(chunk)
      
      if (stepMessage) {
        // 如果是步骤消息，替换当前助手消息
        const stepIndex = messages.findIndex(m => m.id === assistant.id)
        if (stepIndex !== -1) {
          messages[stepIndex] = stepMessage
        } else {
          // 如果找不到助手消息，直接添加新消息
          messages.push(stepMessage)
        }
      } else {
        // 普通消息，追加内容
        assistant.content += chunk
      }
      scrollToBottom() 
    },
    () => { loading.value = false },
    () => { loading.value = false }
  )
}

function stop() {
  if (closeFn) { closeFn(); closeFn = null }
}

onMounted(() => { /* session already generated */ })
onBeforeUnmount(() => { stop() })

watch(messages, scrollToBottom)
</script>

<style scoped>
</style>






