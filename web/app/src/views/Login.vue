<template>
  <div class="login-studio fade-in">
    <div class="login-visual">
      <div class="visual-content">
        <div class="visual-logo">
          <el-icon :size="48" color="#fff"><Cpu /></el-icon>
        </div>
        <h1>AgentAi Studio</h1>
        <p>基于多智能体协作的自动化软件构建平台</p>
        <div class="visual-features">
          <div class="feature-item">
            <el-icon><MagicStick /></el-icon>
            <span>一键生成全栈代码</span>
          </div>
          <div class="feature-item">
            <el-icon><Coordinate /></el-icon>
            <span>多智能体自动审查</span>
          </div>
          <div class="feature-item">
            <el-icon><Operation /></el-icon>
            <span>可视化构建轨迹</span>
          </div>
        </div>
      </div>
    </div>
    
    <div class="login-form-area">
      <div class="form-card">
        <div class="form-header">
          <h2>欢迎回来</h2>
          <p>请登录您的账户以开始构建项目</p>
        </div>

        <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
          <el-form-item label="用户名" prop="username">
            <el-input 
              v-model="form.username" 
              placeholder="请输入用户名" 
              prefix-icon="User"
              size="large"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input 
              v-model="form.password" 
              type="password" 
              placeholder="请输入密码" 
              show-password 
              prefix-icon="Lock"
              size="large"
            />
          </el-form-item>

          <div class="form-actions">
            <el-checkbox v-model="rememberMe">记住我</el-checkbox>
            <el-button link type="primary">忘记密码？</el-button>
          </div>

          <el-button 
            type="primary" 
            @click="handleLogin" 
            :loading="loading" 
            class="submit-btn"
            size="large"
          >
            立即登录
          </el-button>

          <div class="form-footer">
            <span>还没有账号？</span>
            <el-button link type="primary" @click="$router.push('/register')">
              创建新账号
            </el-button>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Cpu, MagicStick, Coordinate, Operation } from '@element-plus/icons-vue'
import { authApi, userApi } from '../api'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)
const rememberMe = ref(false)

const form = ref({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  loading.value = true

  try {
    const res = await authApi.login(form.value)
    userStore.setToken(res.data.token)

    const profileRes = await userApi.getProfile()
    userStore.setUserInfo(profileRes.data)

    ElMessage.success('欢迎回来，' + profileRes.data.username)
    router.push('/')
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-studio {
  display: flex;
  min-height: 100vh;
  background-color: #fff;
}

.login-visual {
  flex: 1.2;
  background: linear-gradient(135deg, #4f46e5 0%, #6366f1 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  padding: 60px;
  position: relative;
  overflow: hidden;
}

.login-visual::after {
  content: '';
  position: absolute;
  width: 500px;
  height: 500px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 50%;
  top: -100px;
  right: -100px;
}

.visual-content {
  max-width: 500px;
  position: relative;
  z-index: 1;
}

.visual-logo {
  width: 80px;
  height: 80px;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 32px;
}

.visual-content h1 {
  font-size: 3rem;
  font-weight: 800;
  margin: 0 0 16px 0;
  letter-spacing: -1px;
}

.visual-content p {
  font-size: 1.25rem;
  opacity: 0.9;
  line-height: 1.6;
  margin-bottom: 48px;
}

.visual-features {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 1.1rem;
}

.feature-item .el-icon {
  font-size: 24px;
  padding: 8px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 8px;
}

.login-form-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background-color: #f8fafc;
}

.form-card {
  width: 100%;
  max-width: 420px;
  padding: 40px;
  background: #fff;
  border-radius: 24px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.05);
}

.form-header {
  margin-bottom: 32px;
}

.form-header h2 {
  font-size: 1.875rem;
  font-weight: 700;
  margin: 0 0 8px 0;
  color: var(--text-primary);
}

.form-header p {
  color: var(--text-secondary);
  font-size: 0.9375rem;
}

.form-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.submit-btn {
  width: 100%;
  height: 50px;
  font-size: 1.1rem;
  font-weight: 600;
  margin-bottom: 24px;
}

.form-footer {
  text-align: center;
  font-size: 0.9375rem;
  color: var(--text-secondary);
}

@media (max-width: 992px) {
  .login-visual {
    display: none;
  }
}
</style>
