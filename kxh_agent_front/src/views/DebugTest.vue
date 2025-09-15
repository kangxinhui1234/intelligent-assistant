<template>
  <div class="container col" style="gap: 12px; padding-top: 16px;">
    <h2>调试格式化功能</h2>
    
    <div class="card">
      <h3>测试数据</h3>
      <textarea v-model="testInput" style="width: 100%; height: 200px; font-family: monospace;"></textarea>
      <div style="display: flex; gap: 8px; margin-top: 8px;">
        <button @click="testFormat" class="btn">测试完整格式化</button>
        <button @click="testSimpleFormat" class="btn outline">测试地理编码格式化</button>
    <button @click="testFullFormat" class="btn outline">测试完整格式化</button>
      </div>
    </div>

    <div class="card">
      <h3>格式化结果</h3>
      <div class="bubble left" v-html="formattedResult"></div>
    </div>

    <div class="card">
      <h3>控制台日志</h3>
      <pre>{{ consoleLogs }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { formatMessageContent } from '../utils/formatter'

// 直接导入格式化函数进行测试
function formatGeoData(content: string): string {
  console.log('开始处理地理编码数据:', content)
  
  try {
    // 先尝试直接解析整个内容
    let data = null
    try {
      data = JSON.parse(content)
      console.log('直接解析成功:', data)
    } catch (e) {
      console.log('直接解析失败:', e)
      return content
    }
    
    if (data) {
      // 处理数组格式的数据
      if (Array.isArray(data)) {
        console.log('处理数组格式数据')
        for (const item of data) {
          if (item.type === 'text' && item.text) {
            console.log('处理嵌套text字段:', item.text)
            try {
              const innerData = JSON.parse(item.text)
              console.log('内层数据解析成功:', innerData)
              if (innerData.return && Array.isArray(innerData.return)) {
                console.log('找到return数组，开始格式化')
                return formatGeoList(innerData.return)
              }
            } catch (e) {
              console.log('内层解析失败:', e)
            }
          }
        }
      }
      
      // 处理嵌套的text字段
      if (data.type === 'text' && data.text) {
        console.log('处理嵌套text字段:', data.text)
        try {
          const innerData = JSON.parse(data.text)
          console.log('内层数据解析成功:', innerData)
          if (innerData.return && Array.isArray(innerData.return)) {
            console.log('找到return数组，开始格式化')
            return formatGeoList(innerData.return)
          }
        } catch (e) {
          console.log('内层解析失败:', e)
        }
      }
      
      // 直接处理return数组
      if (data.return && Array.isArray(data.return)) {
        console.log('直接处理return数组')
        return formatGeoList(data.return)
      }
    }
    
    console.log('地理编码数据格式化失败，返回原内容')
    return content
  } catch (error) {
    console.error('地理编码格式化错误:', error)
    return content
  }
}

function formatGeoList(geoResults: any[]): string {
  let geoHtml = '<div class="geo-list">\n'
  
  geoResults.forEach((geo: any, index: number) => {
    geoHtml += `<div class="geo-item">
      <div class="geo-header">
        <span class="geo-index">${index + 1}</span>
        <h4 class="geo-name">${geo.district || geo.city || '未知位置'}</h4>
      </div>
      <div class="geo-details">
        <p class="geo-address">📍 ${geo.country} ${geo.province} ${geo.city} ${geo.district}</p>
        <div class="geo-info">
          <span class="geo-level">${geo.level || '未知级别'}</span>
          ${geo.citycode ? `<span class="geo-code">区号: ${geo.citycode}</span>` : ''}
          ${geo.adcode ? `<span class="geo-adcode">编码: ${geo.adcode}</span>` : ''}
        </div>
        ${geo.location ? `<div class="geo-coords">🗺️ 坐标: ${geo.location}</div>` : ''}
      </div>
    </div>\n`
  })
  
  geoHtml += '</div>'
  return geoHtml
}

const testInput = ref(`🔧 **maps_geo**

[{"type":"text","text":"{\\n  \\"return\\": [\\n    {\\n      \\"country\\": \\"中国\\",\\n      \\"province\\": \\"广东省\\",\\n      \\"city\\": \\"深圳市\\",\\n      \\"citycode\\": \\"0755\\",\\n      \\"district\\": \\"南山区\\",\\n      \\"street\\": [],\\n      \\"number\\": [],\\n      \\"adcode\\": \\"440305\\",\\n      \\"location\\": \\"113.930478,22.533191\\",\\n      \\"level\\": \\"区县\\"\\n    }\\n  ]\\n}"}]`)

// 添加一个简单的测试函数
function testSimpleFormat() {
  consoleLogs.value = ''
  
  const testData = `[{"type":"text","text":"{\n  \"return\": [\n    {\n      \"country\": \"中国\",\n      \"province\": \"广东省\",\n      \"city\": \"深圳市\",\n      \"citycode\": \"0755\",\n      \"district\": \"南山区\",\n      \"street\": [],\n      \"number\": [],\n      \"adcode\": \"440305\",\n      \"location\": \"113.930478,22.533191\",\n      \"level\": \"区县\"\n    }\n  ]\n}"}]`
  
  // 临时重写console.log来捕获日志
  const originalConsoleLog = console.log
  const logs: string[] = []
  
  console.log = (...args) => {
    originalConsoleLog(...args)
    logs.push(args.join(' '))
  }
  
  try {
    // 直接测试地理编码格式化
    const result = formatGeoData(testData)
    formattedResult.value = result
  } finally {
    // 恢复原始console.log
    console.log = originalConsoleLog
    consoleLogs.value = logs.join('\n')
  }
}

function testFullFormat() {
  consoleLogs.value = ''
  
  const testData = `🔧 **maps_geo** [{"type":"text","text":"{\n  \"return\": [\n    {\n      \"country\": \"中国\",\n      \"province\": \"广东省\",\n      \"city\": \"深圳市\",\n      \"citycode\": \"0755\",\n      \"district\": \"南山区\",\n      \"street\": [],\n      \"number\": [],\n      \"adcode\": \"440305\",\n      \"location\": \"113.930478,22.533191\",\n      \"level\": \"区县\"\n    }\n  ]\n}"}] 南山书城的经纬度坐标是113.930447,22.518038。接下来，我们可以使用maps_around_search工具，在南山书城周围搜索餐厅或咖啡馆作为约会地点。`
  
  // 临时重写console.log来捕获日志
  const originalConsoleLog = console.log
  const logs: string[] = []
  
  console.log = (...args) => {
    originalConsoleLog(...args)
    logs.push(args.join(' '))
  }
  
  try {
    // 测试完整格式化
    const result = formatMessageContent(testData)
    formattedResult.value = result
  } finally {
    // 恢复原始console.log
    console.log = originalConsoleLog
    consoleLogs.value = logs.join('\n')
  }
}

const consoleLogs = ref('')
const formattedResult = ref('')

function testFormat() {
  consoleLogs.value = ''
  
  // 临时重写console.log来捕获日志
  const originalConsoleLog = console.log
  const logs: string[] = []
  
  console.log = (...args) => {
    originalConsoleLog(...args)
    logs.push(args.join(' '))
  }
  
  try {
    formattedResult.value = formatMessageContent(testInput.value)
  } finally {
    // 恢复原始console.log
    console.log = originalConsoleLog
    consoleLogs.value = logs.join('\n')
  }
}

// 初始化
testFormat()
</script>

<style scoped>
pre {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  font-family: monospace;
  font-size: 12px;
  line-height: 1.4;
  max-height: 300px;
  overflow-y: auto;
}

textarea {
  padding: 8px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.4;
}

.bubble {
  max-width: 100%;
  padding: 10px 12px;
  border-radius: 12px;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
  background: #f3f4f6;
  color: #111827;
  border-top-left-radius: 4px;
}

/* 地理编码列表样式 */
.bubble :deep(.geo-list) {
  margin: 12px 0;
}

.bubble :deep(.geo-item) {
  background: rgba(0, 0, 0, 0.02);
  border: 1px solid rgba(0, 0, 0, 0.1);
  border-radius: 8px;
  padding: 12px;
  margin: 8px 0;
  transition: all 0.2s ease;
}

.bubble :deep(.geo-item:hover) {
  background: rgba(34, 197, 94, 0.05);
  border-color: rgba(34, 197, 94, 0.3);
}

.bubble :deep(.geo-header) {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.bubble :deep(.geo-index) {
  background: #22c55e;
  color: white;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: bold;
  margin-right: 8px;
  flex-shrink: 0;
}

.bubble :deep(.geo-name) {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  flex: 1;
}

.bubble :deep(.geo-details) {
  margin-left: 32px;
}

.bubble :deep(.geo-address) {
  margin: 4px 0;
  color: #6b7280;
  font-size: 14px;
  line-height: 1.4;
}

.bubble :deep(.geo-info) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 8px 0;
}

.bubble :deep(.geo-level) {
  display: inline-block;
  background: #dbeafe;
  color: #1e40af;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.bubble :deep(.geo-code) {
  display: inline-block;
  background: #f3f4f6;
  color: #374151;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.bubble :deep(.geo-adcode) {
  display: inline-block;
  background: #fef3c7;
  color: #92400e;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.bubble :deep(.geo-coords) {
  margin: 8px 0;
  padding: 8px;
  background: rgba(0, 0, 0, 0.02);
  border-radius: 6px;
  font-family: monospace;
  font-size: 13px;
  color: #6b7280;
}

/* 工具调用结果样式 */
.bubble :deep(.tool-result) {
  background: rgba(59, 130, 246, 0.1);
  border-left: 3px solid #3b82f6;
  padding: 8px 12px;
  margin: 8px 0;
  border-radius: 0 6px 6px 0;
}
</style>
