<template>
  <div class="profile">
    <el-card>
      <template #header>
        <span>个人信息</span>
      </template>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" disabled />
        </el-form-item>

        <el-form-item label="头像" prop="avatar">
          <el-input v-model="form.avatar" placeholder="请输入头像URL" />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>

        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入真实姓名" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleUpdate" :loading="loading">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card style="margin-top: 20px">
      <template #header>
        <span>修改密码</span>
      </template>

      <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="100px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>

        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleChangePassword" :loading="passwordLoading">
            修改密码
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '../api'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const passwordFormRef = ref(null)
const loading = ref(false)
const passwordLoading = ref(false)

const form = ref({
  username: '',
  avatar: '',
  email: '',
  realName: ''
})

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== passwordForm.value.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }]
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const loadProfile = async () => {
  try {
    const res = await userApi.getProfile()
    form.value = res.data
  } catch (error) {
    console.error(error)
  }
}

const handleUpdate = async () => {
  await formRef.value.validate()
  loading.value = true

  try {
    await userApi.updateProfile({
      avatar: form.value.avatar,
      email: form.value.email,
      realName: form.value.realName
    })

    userStore.setUserInfo(form.value)
    ElMessage.success('更新成功')
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const handleChangePassword = async () => {
  await passwordFormRef.value.validate()
  passwordLoading.value = true

  try {
    await userApi.changePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    })

    ElMessage.success('密码修改成功，请重新登录')
    userStore.logout()
    router.push('/login')
  } catch (error) {
    console.error(error)
  } finally {
    passwordLoading.value = false
  }
}

onMounted(() => {
  loadProfile()
})
</script>

<style scoped>
.profile {
  max-width: 600px;
  margin: 0 auto;
}
</style>
