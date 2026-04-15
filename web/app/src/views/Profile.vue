<template>
  <div class="profile-page fade-in">
    <!-- 基本信息卡片 -->
    <el-card class="profile-card">
      <template #header>
        <div class="card-header-row">
          <span class="card-title">个人信息</span>
        </div>
      </template>

      <div v-if="profileLoading" class="loading-wrap">
        <el-skeleton :rows="4" animated />
      </div>

      <el-form
        v-else
        :model="form"
        :rules="rules"
        ref="formRef"
        label-width="90px"
        label-position="left"
      >
        <!-- 头像预览 -->
        <el-form-item label="头像预览">
          <div class="avatar-row">
            <el-avatar
              :size="64"
              :src="form.avatar || defaultAvatar"
              class="preview-avatar"
            />
            <el-input
              v-model="form.avatar"
              placeholder="输入头像图片 URL"
              class="avatar-input"
              clearable
            />
          </div>
        </el-form-item>

        <el-form-item label="用户名">
          <el-input v-model="form.username" disabled />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" clearable />
        </el-form-item>

        <el-form-item label="真实姓名" prop="realName">
          <el-input
            v-model="form.realName"
            placeholder="请输入真实姓名"
            clearable
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            @click="handleUpdate"
            :loading="loading"
            style="width: 120px"
          >
            保存修改
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 修改密码卡片 -->
    <el-card class="profile-card">
      <template #header>
        <div class="card-header-row">
          <span class="card-title">修改密码</span>
        </div>
      </template>

      <el-form
        :model="passwordForm"
        :rules="passwordRules"
        ref="passwordFormRef"
        label-width="90px"
        label-position="left"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            show-password
            placeholder="请输入原密码"
          />
        </el-form-item>

        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            show-password
            placeholder="至少 6 位"
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            show-password
            placeholder="再次输入新密码"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            @click="handleChangePassword"
            :loading="passwordLoading"
            style="width: 120px"
          >
            修改密码
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { userApi } from "../api";
import { useUserStore } from "../stores/user";

const router = useRouter();
const userStore = useUserStore();
const formRef = ref(null);
const passwordFormRef = ref(null);
const loading = ref(false);
const passwordLoading = ref(false);
const profileLoading = ref(true);

const defaultAvatar =
  "https://api.dicebear.com/7.x/avataaars/svg?seed=Felix";

const form = ref({
  username: "",
  avatar: "",
  email: "",
  realName: "",
});

const passwordForm = ref({
  oldPassword: "",
  newPassword: "",
  confirmPassword: "",
});

// ── 验证规则 ──────────────────────────────────────────────────────────────────

const validateConfirmPassword = (rule, value, callback) => {
  if (!value) {
    callback(new Error("请再次输入新密码"));
  } else if (value !== passwordForm.value.newPassword) {
    callback(new Error("两次输入的密码不一致"));
  } else {
    callback();
  }
};

const rules = {
  email: [
    { required: true, message: "请输入邮箱", trigger: "blur" },
    { type: "email", message: "邮箱格式不正确", trigger: "blur" },
  ],
};

const passwordRules = {
  oldPassword: [{ required: true, message: "请输入原密码", trigger: "blur" }],
  newPassword: [
    { required: true, message: "请输入新密码", trigger: "blur" },
    { min: 6, message: "密码长度至少 6 位", trigger: "blur" },
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: "blur" },
  ],
};

// ── 加载个人信息 ───────────────────────────────────────────────────────────────

const loadProfile = async () => {
  profileLoading.value = true;
  try {
    const res = await userApi.getProfile();
    const user = res.data;
    form.value = {
      username: user.username || "",
      avatar: user.avatar || "",
      email: user.email || "",
      realName: user.realName || "",
    };
  } catch (error) {
    ElMessage.error("获取个人信息失败");
    console.error(error);
  } finally {
    profileLoading.value = false;
  }
};

// ── 保存基本信息 ───────────────────────────────────────────────────────────────

const handleUpdate = async () => {
  try {
    await formRef.value.validate();
  } catch {
    return; // 表单验证未通过，Element Plus 已在表单上显示错误信息
  }

  loading.value = true;
  try {
    await userApi.updateProfile({
      avatar: form.value.avatar,
      email: form.value.email,
      realName: form.value.realName,
    });

    // 同步更新 Pinia store，确保头部头像/用户名实时更新
    userStore.setUserInfo({
      ...userStore.userInfo,
      avatar: form.value.avatar,
      email: form.value.email,
      realName: form.value.realName,
      username: form.value.username,
    });

    ElMessage.success("个人信息更新成功");
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

// ── 修改密码 ───────────────────────────────────────────────────────────────────

const handleChangePassword = async () => {
  try {
    await passwordFormRef.value.validate();
  } catch {
    return;
  }

  passwordLoading.value = true;
  try {
    await userApi.changePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword,
    });

    ElMessage.success("密码修改成功，请重新登录");
    userStore.logout();
    router.push("/login");
  } catch (error) {
    console.error(error);
  } finally {
    passwordLoading.value = false;
  }
};

onMounted(() => {
  loadProfile();
});
</script>

<style scoped>
.profile-page {
  max-width: 640px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.profile-card {
  border-radius: 12px;
}

.card-header-row {
  display: flex;
  align-items: center;
}

.card-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-primary);
}

.loading-wrap {
  padding: 8px 0;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
}

.preview-avatar {
  flex-shrink: 0;
  border: 2px solid #e2e8f0;
}

.avatar-input {
  flex: 1;
}
</style>
