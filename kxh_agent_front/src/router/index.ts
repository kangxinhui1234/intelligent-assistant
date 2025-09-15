import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  { path: '/', name: 'home', component: () => import('../views/Home.vue') },
  { path: '/love', name: 'love', component: () => import('../views/LoveMaster.vue') },
  { path: '/agent', name: 'agent', component: () => import('../views/SuperAgent.vue') },
  { path: '/invest', name: 'invest', component: () => import('../views/InvestAgent.vue') },
  { path: '/test', name: 'test', component: () => import('../views/TestConnection.vue') },
  { path: '/poi', name: 'poi', component: () => import('../views/PoiTest.vue') },
  { path: '/debug', name: 'debug', component: () => import('../views/DebugTest.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router






