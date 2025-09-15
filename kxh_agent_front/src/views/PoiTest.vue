<template>
  <div class="container col" style="gap: 12px; padding-top: 16px;">
    <h2>POI格式化测试</h2>
    
    <div class="card">
      <h3>原始数据</h3>
      <pre>{{ rawData }}</pre>
    </div>

    <div class="card">
      <h3>格式化后效果</h3>
      <div class="bubble left" v-html="formattedData"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { formatMessageContent } from '../utils/formatter'

const rawData = ref(`当前工具：maps_geo,调用结果：[{"type":"text","text":"{\\n  \\"return\\": [\\n    {\\n      \\"country\\": \\"中国\\",\\n      \\"province\\": \\"广东省\\",\\n      \\"city\\": \\"深圳市\\",\\n      \\"citycode\\": \\"0755\\",\\n      \\"district\\": \\"南山区\\",\\n      \\"street\\": [],\\n      \\"number\\": [],\\n      \\"adcode\\": \\"440305\\",\\n      \\"location\\": \\"113.930447,22.518038\\",\\n      \\"level\\": \\"公交地铁站点\\"\\n    }\\n  ]\\n}"}]

当前工具：maps_around_search,调用结果：[{"type":"text","text":"{\\n  \\"pois\\": [\\n    {\\n      \\"id\\": \\"B0L6F5GQ3W\\",\\n      \\"name\\": \\"FOODPARK美食公园\\",\\n      \\"address\\": \\"文心三路9号中洲控股中心B座\\",\\n      \\"typecode\\": \\"050100\\",\\n      \\"photos\\": {\\n        \\"title\\": [],\\n        \\"url\\": \\"https://aos-comment.amap.com/B0L6F5GQ3W/comment/36DE244E_9838_4054_AE36_C1CFCF75631F_L0_001_1500_200_1747839472251_65733404.jpg\\"\\n      }\\n    },\\n    {\\n      \\"id\\": \\"B0LAOLS301\\",\\n      \\"name\\": \\"真秀香延边朝鲜族餐厅\\",\\n      \\"address\\": \\"创业路3023号凯德公园1号广场C座1层\\",\\n      \\"typecode\\": \\"050000\\",\\n      \\"photos\\": {\\n        \\"title\\": [],\\n        \\"url\\": \\"http://store.is.autonavi.com/showpic/bce409ed28f71fac58ed99912bd90b37\\"\\n      }\\n    }\\n  ]\\n}"}]`)

const formattedData = computed(() => {
  return formatMessageContent(rawData.value)
})
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

/* POI列表样式 */
.bubble :deep(.poi-list) {
  margin: 12px 0;
}

.bubble :deep(.poi-item) {
  background: rgba(0, 0, 0, 0.02);
  border: 1px solid rgba(0, 0, 0, 0.1);
  border-radius: 8px;
  padding: 12px;
  margin: 8px 0;
  transition: all 0.2s ease;
}

.bubble :deep(.poi-item:hover) {
  background: rgba(59, 130, 246, 0.05);
  border-color: rgba(59, 130, 246, 0.3);
}

.bubble :deep(.poi-header) {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.bubble :deep(.poi-index) {
  background: #3b82f6;
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

.bubble :deep(.poi-name) {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  flex: 1;
}

.bubble :deep(.poi-details) {
  margin-left: 32px;
}

.bubble :deep(.poi-address) {
  margin: 4px 0;
  color: #6b7280;
  font-size: 14px;
  line-height: 1.4;
}

.bubble :deep(.poi-type) {
  display: inline-block;
  background: #e5e7eb;
  color: #374151;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  margin: 4px 0;
}

.bubble :deep(.poi-photo) {
  margin: 8px 0;
}

.bubble :deep(.poi-photo img) {
  max-width: 100%;
  height: auto;
  border-radius: 6px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
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
