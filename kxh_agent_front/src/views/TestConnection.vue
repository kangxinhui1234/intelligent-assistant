<template>
  <div class="container col" style="gap: 12px; padding-top: 16px;">
    <h2>API连接测试</h2>
    
    <div class="card">
      <h3>测试信息</h3>
      <p><strong>API基础URL:</strong> {{ apiBaseUrl }}</p>
      <p><strong>完整API路径:</strong> {{ fullApiUrl }}</p>
      <p><strong>测试消息:</strong> {{ testMessage }}</p>
    </div>

    <div class="card">
      <h3>连接测试</h3>
      <button class="btn" @click="testConnection" :disabled="testing">
        {{ testing ? '测试中...' : '测试连接' }}
      </button>
      <button class="btn outline" @click="testSSE" :disabled="testingSSE">
        {{ testingSSE ? 'SSE测试中...' : '测试SSE连接' }}
      </button>
    </div>

    <div class="card" v-if="testResults.length > 0">
      <h3>测试结果</h3>
      <div v-for="(result, index) in testResults" :key="index" class="test-result">
        <strong>{{ result.type }}:</strong> {{ result.message }}
        <div v-if="result.data" class="test-data">{{ result.data }}</div>
      </div>
    </div>

    <div class="card" v-if="sseMessages.length > 0">
      <h3>SSE消息</h3>
      <div v-for="(msg, index) in sseMessages" :key="index" class="sse-message">
        {{ msg }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { openSSE } from '../services/sse'

const apiBaseUrl = import.meta.env.VITE_API_BASE || 'http://localhost:8092'
const testMessage = '你好，这是一个测试消息'
const fullApiUrl = computed(() => `${apiBaseUrl}/api/agent/manus/chat`)

const testing = ref(false)
const testingSSE = ref(false)
const testResults = ref<Array<{type: string, message: string, data?: string}>>([])
const sseMessages = ref<string[]>([])

function addResult(type: string, message: string, data?: string) {
  testResults.value.push({ type, message, data })
}

async function testConnection() {
  testing.value = true
  testResults.value = []
  
  try {
    // 测试健康检查接口
    const healthUrl = `${apiBaseUrl}/api/health/getInfo`
    addResult('INFO', '测试健康检查接口', healthUrl)
    
    const response = await fetch(healthUrl)
    const data = await response.text()
    
    if (response.ok) {
      addResult('SUCCESS', '健康检查成功', data)
    } else {
      addResult('ERROR', '健康检查失败', `状态码: ${response.status}`)
    }
  } catch (error) {
    addResult('ERROR', '网络请求失败', error instanceof Error ? error.message : String(error))
  } finally {
    testing.value = false
  }
}

function testSSE() {
  testingSSE.value = true
  sseMessages.value = []
  testResults.value = []
  
  addResult('INFO', '开始SSE连接测试', fullApiUrl.value)
  
  const closeFn = openSSE(
    fullApiUrl.value,
    { message: testMessage },
    (chunk) => {
      sseMessages.value.push(chunk)
      addResult('SUCCESS', '收到SSE数据', chunk)
    },
    () => {
      addResult('SUCCESS', 'SSE连接正常完成')
      testingSSE.value = false
    },
    (error) => {
      addResult('ERROR', 'SSE连接失败', error instanceof Error ? error.message : String(error))
      testingSSE.value = false
    }
  )
  
  // 10秒后自动关闭测试
  setTimeout(() => {
    if (testingSSE.value) {
      closeFn()
      addResult('INFO', 'SSE测试超时，已关闭连接')
      testingSSE.value = false
    }
  }, 10000)
}
</script>

<style scoped>
.test-result {
  margin: 8px 0;
  padding: 8px;
  border-radius: 4px;
  background: #f5f5f5;
}

.test-data {
  font-family: monospace;
  background: #e8e8e8;
  padding: 4px;
  margin-top: 4px;
  border-radius: 2px;
}

.sse-message {
  margin: 4px 0;
  padding: 4px;
  background: #e8f5e8;
  border-radius: 2px;
  font-family: monospace;
}
</style>

