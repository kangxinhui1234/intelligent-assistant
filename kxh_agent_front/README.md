# KXH Agent Frontend

Vue.js 前端应用，用于与 AI 智能体进行聊天交互。

## 功能特性

- 实时聊天界面
- Server-Sent Events (SSE) 流式响应
- 支持多个聊天场景（超级智能体、恋爱大师等）

## 环境要求

- Node.js 16+
- Spring Boot 后端服务运行在 http://localhost:8092

## 快速开始

1. 安装依赖：
```bash
npm install
```

2. 启动开发服务器：
```bash
npm run dev
# 或者使用批处理文件
start-dev.bat
```

3. 访问应用：
- 打开浏览器访问 http://localhost:5173
- 超级智能体：http://localhost:5173/agent
- 恋爱大师：http://localhost:5173/love

## 环境变量

应用使用以下环境变量：
- `VITE_API_BASE`: 后端API基础URL（默认：http://localhost:8092）

## 项目结构

```
src/
├── components/
│   └── ChatRoom.vue          # 聊天室组件
├── services/
│   ├── http.ts               # HTTP请求服务
│   └── sse.ts                # SSE连接服务
├── views/
│   ├── Home.vue              # 首页
│   ├── SuperAgent.vue        # 超级智能体页面
│   └── LoveMaster.vue        # 恋爱大师页面
└── router/
    └── index.ts              # 路由配置
```

## 故障排除

如果聊天框无法显示返回数据，请检查：

1. 后端服务是否正在运行
2. 后端接口 `/api/agent/manus/chat` 是否可访问
3. 浏览器控制台是否有CORS或网络错误
4. 确保环境变量 `VITE_API_BASE` 正确设置
