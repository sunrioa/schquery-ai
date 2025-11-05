<template>
  <div class="forgot-password-container">
    <!-- 居中卡片容器 -->
    <div class="forgot-password-card">
      <div class="card-header">
        <h1>找回密码</h1>
        <p>请按照以下步骤重置您的密码</p>
      </div>

      <!-- 步骤指示器 -->
      <div class="step-indicator">
        <div class="step" :class="{ active: currentStep >= 1, completed: currentStep > 1 }">
          <div class="step-number">1</div>
          <div class="step-text">验证邮箱</div>
        </div>
        <div class="step-line"></div>
        <div class="step" :class="{ active: currentStep >= 2, completed: currentStep > 2 }">
          <div class="step-number">2</div>
          <div class="step-text">输入验证码</div>
        </div>
        <div class="step-line"></div>
        <div class="step" :class="{ active: currentStep >= 3 }">
          <div class="step-number">3</div>
          <div class="step-text">重置密码</div>
        </div>
      </div>

      <!-- 表单内容 -->
      <div class="form-content">
        <!-- 步骤1：输入用户名和邮箱 -->
        <div v-if="currentStep === 1" class="step-form">
          <el-form
              ref="emailFormRef"
              :model="emailForm"
              :rules="emailRules"
              label-width="0"
          >
            <el-form-item prop="userName">
              <el-input
                  v-model="emailForm.userName"
                  placeholder="请输入用户名"
                  prefix-icon="User"
                  size="large"
                  clearable
              />
            </el-form-item>
            <el-form-item prop="email">
              <el-input
                  v-model="emailForm.email"
                  placeholder="请输入邮箱地址"
                  prefix-icon="Message"
                  size="large"
                  clearable
              />
            </el-form-item>
            <el-form-item>
              <el-button
                  type="primary"
                  size="large"
                  :loading="sendingCode"
                  @click="sendVerificationCode"
                  style="width: 100%"
              >
                发送验证码
              </el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- 步骤2：输入验证码 -->
        <div v-if="currentStep === 2" class="step-form">
          <el-form
              ref="codeFormRef"
              :model="codeForm"
              :rules="codeRules"
              label-width="0"
          >
            <el-form-item>
              <div class="email-display">
                <span>验证码已发送至：</span>
                <strong>{{ emailForm.email }}</strong>
              </div>
            </el-form-item>
            <el-form-item prop="code">
              <el-input
                  v-model="codeForm.code"
                  placeholder="请输入6位验证码"
                  prefix-icon="Key"
                  maxlength="6"
                  size="large"
                  clearable
              />
            </el-form-item>
            <el-form-item>
              <el-button
                  type="primary"
                  size="large"
                  :loading="verifyingCode"
                  @click="verifyCode"
                  style="width: 100%"
              >
                验证码
              </el-button>
            </el-form-item>
            <el-form-item>
              <el-button
                  type="text"
                  @click="resendCode"
                  :disabled="countdown > 0"
                  style="width: 100%"
              >
                {{ countdown > 0 ? `重新发送(${countdown}s)` : '重新发送验证码' }}
              </el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- 步骤3：重置密码 -->
        <div v-if="currentStep === 3" class="step-form">
          <el-form
              ref="passwordFormRef"
              :model="passwordForm"
              :rules="passwordRules"
              label-width="0"
          >
            <el-form-item prop="password">
              <el-input
                  v-model="passwordForm.password"
                  type="password"
                  placeholder="请输入新密码"
                  prefix-icon="Lock"
                  show-password
                  size="large"
                  clearable
              />
            </el-form-item>
            <el-form-item prop="confirmPassword">
              <el-input
                  v-model="passwordForm.confirmPassword"
                  type="password"
                  placeholder="请确认新密码"
                  prefix-icon="Lock"
                  show-password
                  size="large"
                  clearable
              />
            </el-form-item>
            <el-form-item>
              <el-button
                  type="primary"
                  size="large"
                  :loading="resettingPassword"
                  @click="resetPassword"
                  style="width: 100%"
              >
                重置密码
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <!-- 返回登录 -->
      <div class="back-to-login">
        <el-link type="primary" @click="$router.push('/login')">
          返回登录
        </el-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '../api/user'

const router = useRouter()

// 当前步骤
const currentStep = ref(1)

// 步骤1：邮箱表单
const emailFormRef = ref(null)
const emailForm = reactive({
  userName: '',
  email: ''
})

// 步骤2：验证码表单
const codeFormRef = ref(null)
const codeForm = reactive({
  code: ''
})

// 步骤3：密码表单
const passwordFormRef = ref(null)
const passwordForm = reactive({
  password: '',
  confirmPassword: ''
})

// 加载状态
const sendingCode = ref(false)
const verifyingCode = ref(false)
const resettingPassword = ref(false)

// 倒计时
const countdown = ref(0)
let timer = null

// 表单验证规则
const emailRules = reactive({
  userName: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
})

const codeRules = reactive({
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码必须是6位数字', trigger: 'blur' },
    { pattern: /^\d+$/, message: '验证码必须是数字', trigger: 'blur' }
  ]
})

const passwordRules = reactive({
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
})

// 发送验证码
const sendVerificationCode = async () => {
  if (!emailFormRef.value) return

  await emailFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        sendingCode.value = true
        const result = await userApi.sendFindPasswordCode(emailForm)

        if (result.code === 200) {
          ElMessage.success('验证码已发送至您的邮箱')
          currentStep.value = 2
          startCountdown()
        } else {
          ElMessage.error(result.msg || '发送验证码失败')
        }
      } catch (error) {
        console.error('发送验证码失败:', error)
        ElMessage.error('发送验证码失败，请重试')
      } finally {
        sendingCode.value = false
      }
    }
  })
}

// 验证码
const verifyCode = async () => {
  if (!codeFormRef.value) return

  await codeFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        verifyingCode.value = true
        // 这里可以先调用验证码验证接口（如果后端有的话）
        // 或者直接进入下一步，在最后重置密码时一起验证
        currentStep.value = 3
      } catch (error) {
        console.error('验证码验证失败:', error)
        ElMessage.error('验证码验证失败')
      } finally {
        verifyingCode.value = false
      }
    }
  })
}

// 重置密码
const resetPassword = async () => {
  if (!passwordFormRef.value) return

  await passwordFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        resettingPassword.value = true
        const resetData = {
          userName: emailForm.userName,
          email: emailForm.email,
          code: codeForm.code,
          password: passwordForm.password
        }

        const result = await userApi.findPassword(resetData)

        if (result.code === 200) {
          ElMessage.success('密码重置成功！')
          router.push('/login')
        } else {
          ElMessage.error(result.msg || '密码重置失败')
        }
      } catch (error) {
        console.error('密码重置失败:', error)
        ElMessage.error('密码重置失败，请重试')
      } finally {
        resettingPassword.value = false
      }
    }
  })
}

// 重新发送验证码
const resendCode = async () => {
  try {
    sendingCode.value = true
    const result = await userApi.sendFindPasswordCode(emailForm)

    if (result.code === 200) {
      ElMessage.success('验证码已重新发送')
      startCountdown()
    } else {
      ElMessage.error(result.msg || '重新发送验证码失败')
    }
  } catch (error) {
    console.error('重新发送验证码失败:', error)
    ElMessage.error('重新发送验证码失败')
  } finally {
    sendingCode.value = false
  }
}

// 开始倒计时
const startCountdown = () => {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
    }
  }, 1000)
}

// 清理定时器
const clearTimer = () => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

// 组件卸载时清理定时器
onUnmounted(() => {
  clearTimer()
})
</script>

<style scoped>
.forgot-password-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4edf5 100%);
  padding: 20px;
}

.forgot-password-card {
  width: 100%;
  max-width: 500px;
  background: #fff;
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 15px 50px rgba(0, 0, 0, 0.12);
}

.card-header {
  text-align: center;
  margin-bottom: 40px;
}

.card-header h1 {
  font-size: 28px;
  color: #1D2129;
  margin: 0 0 10px 0;
  font-weight: 600;
}

.card-header p {
  color: #86909C;
  margin: 0;
  font-size: 14px;
}

.step-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 40px;
}

.step {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}

.step-number {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #E5E6EB;
  color: #86909C;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 16px;
  transition: all 0.3s ease;
}

.step.active .step-number {
  background: #165DFF;
  color: #fff;
}

.step.completed .step-number {
  background: #00B42A;
  color: #fff;
}

.step-text {
  margin-top: 8px;
  font-size: 12px;
  color: #86909C;
  transition: color 0.3s ease;
}

.step.active .step-text,
.step.completed .step-text {
  color: #1D2129;
}

.step-line {
  width: 60px;
  height: 2px;
  background: #E5E6EB;
  margin: 0 20px;
}

.step.completed + .step-line {
  background: #00B42A;
}

.form-content {
  margin-bottom: 30px;
}

.step-form {
  min-height: 200px;
}

.el-form-item {
  margin-bottom: 24px;
}

.email-display {
  text-align: center;
  color: #4E5969;
  font-size: 14px;
  padding: 16px;
  background: #F7F8FA;
  border-radius: 8px;
}

.email-display strong {
  color: #165DFF;
  display: block;
  margin-top: 4px;
}

:deep(.el-input__inner) {
  height: 46px;
  border-radius: 8px;
  border-color: #E5E6EB;
  padding: 0 15px;
  font-size: 15px;
  transition: all 0.3s ease;
}

:deep(.el-input__inner):focus {
  border-color: #165DFF;
  box-shadow: 0 0 0 2px rgba(22, 93, 255, 0.15);
}

:deep(.el-button--primary) {
  height: 48px;
  font-size: 16px;
  border-radius: 8px;
  background-color: #165DFF;
  border-color: #165DFF;
  transition: all 0.3s ease;
  font-weight: 500;
}

:deep(.el-button--primary):hover {
  background-color: #0E42D2;
  border-color: #0E42D2;
}

.back-to-login {
  text-align: center;
  padding-top: 20px;
  border-top: 1px solid #E5E6EB;
}

@media (max-width: 600px) {
  .forgot-password-card {
    padding: 30px 20px;
  }

  .card-header h1 {
    font-size: 24px;
  }

  .step-indicator {
    margin-bottom: 30px;
  }

  .step-line {
    width: 40px;
    margin: 0 10px;
  }
}
</style>