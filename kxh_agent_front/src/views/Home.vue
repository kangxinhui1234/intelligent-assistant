<template>
  <div class="container col" style="padding-top: 40px; gap: 20px;">
    <h1>应用中心</h1>
    <div class="card col">
      <div class="row" style="justify-content: space-between;">
        <div>
          <h3>AI 恋爱大师</h3>
          <div class="subtext">聊天风格：恋爱指导与建议</div>
        </div>
        <button class="btn" @click="$router.push('/love')">进入</button>
      </div>
    </div>
    <div class="card col">
      <div class="row" style="justify-content: space-between;">
        <div>
          <h3>AI 超级智能体</h3>
          <div class="subtext">通用智能体，多工具协同</div>
        </div>
        <button class="btn" @click="$router.push('/agent')">进入</button>
      </div>
    </div>
    <div class="card col">
      <div class="row" style="justify-content: space-between;">
        <div>
          <h3>AI 投研助手</h3>
          <div class="subtext">专业投资研究分析助手</div>
        </div>
        <button class="btn" @click="$router.push('/invest')">进入</button>
      </div>
    </div>
    <div class="card col">
      <div class="row" style="justify-content: space-between;">
        <div>
          <h3>API连接测试</h3>
          <div class="subtext">测试后端API连接和SSE功能</div>
        </div>
        <button class="btn outline" @click="$router.push('/test')">测试</button>
      </div>
    </div>
    <div class="card col">
      <div class="row" style="justify-content: space-between;">
        <div>
          <h3>POI数据测试</h3>
          <div class="subtext">测试地图搜索结果的美化显示</div>
        </div>
        <button class="btn outline" @click="$router.push('/poi')">测试</button>
      </div>
    </div>
    <div class="card col">
      <div class="row" style="justify-content: space-between;">
        <div>
          <h3>调试格式化</h3>
          <div class="subtext">调试和测试格式化功能</div>
        </div>
        <button class="btn outline" @click="$router.push('/debug')">调试</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts"></script>

<style scoped>
h1 { margin: 0; }
h3 { margin: 0 0 4px; }
</style>






