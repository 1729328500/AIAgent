<template>
  <el-container class="layout-container">
    <el-aside :width="isCollapse ? '64px' : '260px'" class="aside-menu">
      <div class="logo-container" @click="router.push('/dashboard')">
        <div class="logo-icon">
          <el-icon :size="24" color="#fff"><Cpu /></el-icon>
        </div>
        <h2 v-if="!isCollapse" class="logo-text">AgentAi Studio</h2>
      </div>
      
      <el-menu
        :default-active="$route.path"
        class="el-menu-vertical"
        :collapse="isCollapse"
        router
      >
        <el-menu-item index="/dashboard">
          <el-icon><Monitor /></el-icon>
          <template #title>工作台</template>
        </el-menu-item>
        <el-menu-item index="/agents">
          <el-icon><Coordinate /></el-icon>
          <template #title>智能体枢纽</template>
        </el-menu-item>
        <el-menu-item index="/workflows">
          <el-icon><Operation /></el-icon>
          <template #title>构建记录</template>
        </el-menu-item>
        <el-menu-item index="/logs">
          <el-icon><Memo /></el-icon>
          <template #title>运行日志</template>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer" v-if="!isCollapse">
        <el-button text @click="isCollapse = !isCollapse">
          <el-icon><Fold /></el-icon>
        </el-button>
      </div>
      <div class="sidebar-footer" v-else>
        <el-button text @click="isCollapse = !isCollapse">
          <el-icon><Expand /></el-icon>
        </el-button>
      </div>
    </el-aside>

    <el-container class="content-container">
      <el-header height="64px" class="main-header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentRouteName }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        
        <div class="header-right">
          <el-tooltip content="通知" placement="bottom">
            <el-badge is-dot class="notice-badge">
              <el-icon :size="20"><Bell /></el-icon>
            </el-badge>
          </el-tooltip>

          <el-dropdown @command="handleCommand" trigger="click">
            <div class="user-profile">
              <el-avatar :size="32" :src="userStore.userInfo.avatar || 'https://api.dicebear.com/7.x/avataaars/svg?seed=Felix'" />
              <span class="username">{{ userStore.userInfo.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人设置
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided class="logout-item">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { 
  Monitor, Coordinate, Operation, Memo, Cpu, 
  Bell, ArrowDown, User, SwitchButton, Fold, Expand 
} from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'
import { authApi } from '../api'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const isCollapse = ref(false)

const currentRouteName = computed(() => {
  const nameMap = {
    '/dashboard': '工作台',
    '/agents': '智能体枢纽',
    '/workflows': '构建记录',
    '/logs': '运行日志',
    '/profile': '个人中心'
  }
  return nameMap[route.path] || '页面'
})

const handleCommand = async (command) => {
  if (command === 'logout') {
    try {
      await authApi.logout()
    } catch (error) {
      console.error(error)
    }
    userStore.logout()
    ElMessage.success('已安全退出')
    router.push('/login')
  } else if (command === 'profile') {
    router.push('/profile')
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
  background-color: var(--bg-color);
}

.aside-menu {
  background-color: var(--sidebar-bg);
  display: flex;
  flex-direction: column;
  box-shadow: 4px 0 10px rgba(0, 0, 0, 0.02);
  z-index: 10;
}

.logo-container {
  height: 80px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  cursor: pointer;
  gap: 12px;
}

.logo-icon {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, var(--primary-color), #818cf8);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.logo-text {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  white-space: nowrap;
}

.el-menu-vertical {
  flex: 1;
  padding: 10px;
}

:deep(.el-menu-item) {
  height: 50px;
  line-height: 50px;
  margin-bottom: 4px;
  border-radius: 8px;
  color: var(--text-secondary);
}

:deep(.el-menu-item:hover) {
  background-color: #f1f5f9;
  color: var(--text-primary);
}

:deep(.el-menu-item.is-active) {
  background-color: #eef2ff;
  color: var(--primary-color);
  font-weight: 600;
}

.sidebar-footer {
  padding: 20px;
  border-top: 1px solid var(--border-color);
}

.main-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background-color: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 24px;
}

.notice-badge {
  cursor: pointer;
  color: var(--text-secondary);
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background-color 0.2s;
}

.user-profile:hover {
  background-color: #f1f5f9;
}

.username {
  font-weight: 500;
  color: var(--text-primary);
}

.logout-item {
  color: #ef4444;
}

.main-content {
  padding: 24px;
  overflow-y: auto;
}

/* Transition */
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.3s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-20px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>
