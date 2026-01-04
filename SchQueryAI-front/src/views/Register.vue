<template>
  <div class="register-container">
    <!-- 居中卡片容器 -->
    <div class="register-card-container">
      <el-card class="register-card">
        <template #header>
          <div class="card-header">
            <h2>用户注册</h2>
            <el-link type="primary" @click="$router.push('/login')">
              已有账号？去登录
            </el-link>
          </div>
        </template>

        <el-form
            ref="registerFormRef"
            :model="registerForm"
            :rules="registerRules"
            label-width="80px"
            size="large"
        >
          <!-- 用户名输入框 -->
          <el-form-item label="用户名" prop="userName">
            <el-input
                v-model="registerForm.userName"
                placeholder="请输入用户名"
                prefix-icon="User"
                clearable
            />
          </el-form-item>

          <!-- 邮箱输入框 -->
          <el-form-item label="邮箱" prop="email">
            <el-input
                v-model="registerForm.email"
                placeholder="请输入邮箱"
                prefix-icon="Message"
                clearable
            />
          </el-form-item>

          <!-- 验证码输入框 -->
          <el-form-item label="验证码" prop="code">
            <el-row :gutter="10">
              <el-col :span="14">
                <el-input
                    v-model="registerForm.code"
                    placeholder="请输入验证码"
                    prefix-icon="Key"
                    maxlength="6"
                    clearable
                />
              </el-col>
              <el-col :span="10">
                <el-button
                    type="primary"
                    @click="sendRegisterCode"
                    :disabled="sendingCode || codeTimer > 0"
                    class="code-btn"
                >
                  {{ codeTimer > 0 ? `${codeTimer}秒后重新发送` : '发送验证码' }}
                </el-button>
              </el-col>
            </el-row>
          </el-form-item>

          <!-- 密码输入框 -->
          <el-form-item label="密码" prop="password">
            <el-input
                v-model="registerForm.password"
                type="password"
                placeholder="请输入密码"
                prefix-icon="Lock"
                show-password
                clearable
            />
          </el-form-item>

          <!-- 确认密码输入框 -->
          <el-form-item label="确认密码" prop="rePassword">
            <el-input
                v-model="registerForm.rePassword"
                type="password"
                placeholder="请再次输入密码"
                prefix-icon="Lock"
                show-password
                clearable
            />
          </el-form-item>

          <!-- 注册按钮 -->
          <el-form-item>
            <el-button
                type="primary"
                class="register-btn"
                @click="handleRegister"
                :loading="registering"
            >
              注册
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup>
// 导入依赖
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '../api/user'

// 路由实例
const router = useRouter()

// 响应式数据
const registerForm = reactive({
  userName: '',
  email: '',
  password: '',
  rePassword: '',
  code: ''
})
const registerFormRef = ref(null)
const registering = ref(false)
const sendingCode = ref(false)
const codeTimer = ref(0)

// 密码验证规则
const validatePass = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请输入密码'))
  } else if (value.length < 6) {
    callback(new Error('密码长度不能少于6位'))
  } else {
    if (registerForm.rePassword !== '') {
      registerFormRef.value?.validateField('rePassword')
    }
    callback()
  }
}

// 确认密码验证规则
const validatePass2 = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== registerForm.password) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

// 表单验证规则
const registerRules = reactive({
  userName: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 6, max: 20, message: '用户名长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码长度为6位', trigger: 'blur' }
  ],
  password: [
    { validator: validatePass, trigger: 'blur' }
  ],
  rePassword: [
    { validator: validatePass2, trigger: 'blur' }
  ]
})

// 发送注册验证码
const sendRegisterCode = async () => {
  if (!registerForm.email) {
    ElMessage.error('请先输入邮箱')
    return
  }

  if (!/^[\w-]+(\.[\w-]+)*@([\w-]+\.)+[a-zA-Z]{2,7}$/.test(registerForm.email)) {
    ElMessage.error('请输入正确的邮箱格式')
    return
  }

  try {
    sendingCode.value = true
    await userApi.sendRegisterCode({
      email: registerForm.email
    })
    ElMessage.success('验证码发送成功')

    // 倒计时逻辑
    codeTimer.value = 60
    const timer = setInterval(() => {
      codeTimer.value--
      if (codeTimer.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (error) {
    console.error('发送验证码失败:', error)
    ElMessage.error('发送验证码失败，请稍后重试')
  } finally {
    sendingCode.value = false
  }
}

// 处理注册
const handleRegister = async () => {
  if (!registerFormRef.value) return

  try {
    await registerFormRef.value.validate()
    registering.value = true
    await userApi.register(registerForm)
    ElMessage.success('注册成功！即将跳转到登录页')
    setTimeout(() => {
      router.push('/login')
    }, 1500)
  } catch (error) {
    if (error.name !== 'Error') {
      // 表单验证失败不处理
      return
    }
    console.error('注册失败:', error)
    ElMessage.error('注册失败，请稍后重试')
  } finally {
    registering.value = false
  }
}
</script>

<style scoped>
/* 整体容器 - 与登录页保持一致的渐变背景 */
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(var(--app-primary-rgb), 0.08) 0%, rgba(var(--app-primary-rgb), 0.02) 100%),
    var(--app-bg);
  padding: 20px;
}

/* 卡片容器 - 统一阴影和圆角 */
.register-card-container {
  width: 100%;
  max-width: 500px;
}

/* 注册卡片样式 - 与登录页卡片风格统一 */
.register-card {
  border-radius: 16px;
  box-shadow: var(--app-shadow-sm);
  border: none;
  padding: 40px 50px;
  background-color: var(--app-surface);
}

/* 卡片头部 - 与登录页保持一致的布局 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.card-header h2 {
  font-size: 24px;
  color: var(--app-text);
  margin: 0;
  font-weight: 600;
}

/* 表单样式统一 */
.el-form {
  width: 100%;
}

.el-form-item {
  margin-bottom: 22px; /* 与登录页表单项间距一致 */
}

.el-form-item__label {
  font-size: 15px;
  color: var(--app-muted);
  padding-right: 15px;
}

/* 输入框样式 - 与登录页完全同步 */
:deep(.el-input__inner) {
  height: 46px;
  border-radius: 8px;
  border-color: var(--app-border);
  padding: 0 15px;
  font-size: 15px;
  transition: all 0.3s ease;
  color: #303133 !important;
}

:deep(.el-input__inner):focus {
  border-color: var(--app-primary);
  box-shadow: var(--app-ring);
}

:deep(.el-input__prefix) {
  color: var(--app-muted-2);
}

/* 验证码按钮 */
.code-btn {
  width: 100%;
  height: 46px;
  font-size: 15px;
  border-radius: 8px;
}

/* 注册按钮 - 与登录按钮样式统一 */
.register-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  border-radius: 8px;
  transition: all 0.3s ease;
  font-weight: 500;
}

:deep(.el-button--primary) {
  background-color: var(--app-primary);
  border-color: var(--app-primary);
}

:deep(.el-button--primary):hover {
  background-color: var(--app-primary-hover);
  border-color: var(--app-primary-hover);
}

:deep(.el-button--primary).is-loading {
  background-color: var(--app-primary);
  border-color: var(--app-primary);
}

/* 链接样式统一 */
:deep(.el-link) {
  font-size: 14px;
  color: var(--app-primary);
  padding: 0;
  height: auto;
}

:deep(.el-link):hover {
  color: var(--app-primary-hover);
}

/* 响应式调整 - 小屏幕适配 */
@media (max-width: 576px) {
  .register-card {
    padding: 30px 20px;
  }

  .card-header h2 {
    font-size: 22px;
  }

  .el-form-item {
    margin-bottom: 18px;
  }

  :deep(.el-input__inner), .code-btn {
    height: 42px;
    font-size: 14px;
  }

  .register-btn {
    height: 44px;
    font-size: 15px;
  }
}
</style>
