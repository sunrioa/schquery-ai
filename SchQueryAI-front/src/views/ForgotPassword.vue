<template>
  <div class="forgot-password-container">
    <!-- 居中卡片容器 -->
    <div class="forgot-password-card">
      <div class="card-header">
        <h1>忘记密码</h1>
        <p>请输入您的邮箱地址，我们将发送验证码到您的邮箱</p>
      </div>

      <!-- 忘记密码表单 -->
      <div class="form-content">
        <!-- 步骤1: 输入邮箱 -->
        <el-form
            v-if="currentStep === 1"
            ref="emailFormRef"
            :model="emailForm"
            :rules="emailRules"
            label-width="0"
        >
          <el-form-item prop="email">
            <el-input
                v-model="emailForm.email"
                type="email"
                placeholder="请输入您的邮箱地址"
                prefix-icon="Message"
                size="large"
                clearable
                @keyup.enter="handleEmailEnter"
            />
          </el-form-item>

          <el-form-item>
            <el-button
                type="primary"
                @click="sendVerificationCode"
                :disabled="sendingCode || codeTimer > 0"
                :loading="sendingCode"
                class="code-btn send-btn"
                style="width: 100%;"
            >
              {{ codeTimer > 0 ? `${codeTimer}秒后重新发送` : '发送验证码' }}
            </el-button>
          </el-form-item>

          <div class="back-to-login">
            <el-link type="info" @click="$router.push('/login')">
              ← 返回登录
            </el-link>
          </div>
        </el-form>

        <!-- 步骤2: 验证邮箱 -->
        <el-form
            v-if="currentStep === 2"
            ref="verifyFormRef"
            :model="verifyForm"
            :rules="verifyRules"
            label-width="0"
        >
          <el-form-item>
            <div class="email-display">
              <div class="email-display-icon">
                <el-icon><Message /></el-icon>
              </div>
              <div class="email-display-text">
                <div class="email-display-title">验证码已发送</div>
                <div class="email-display-address">{{ emailForm.email }}</div>
              </div>
            </div>
          </el-form-item>

          <el-form-item prop="verificationCode">
            <el-input
                v-model="verifyForm.verificationCode"
                placeholder="请输入6位验证码"
                prefix-icon="Key"
                size="large"
                maxlength="6"
                clearable
                @keyup.enter="handleVerifyCodeEnter"
            />
          </el-form-item>

          <el-form-item>
            <el-button
                type="primary"
                :loading="verifying"
                @click="verifyCode"
                class="verify-btn"
                style="width: 100%; height: 48px; font-size: 16px;"
            >
              验证邮箱
            </el-button>
          </el-form-item>

          <el-form-item>
            <el-button
                type="text"
                @click="resendCode"
                :disabled="codeTimer > 0"
                class="resend-btn"
                style="width: 100%;"
            >
              {{ codeTimer > 0 ? `${codeTimer}秒后重新发送` : '重新发送验证码' }}
            </el-button>
          </el-form-item>

          <div class="back-to-login">
            <el-link type="info" @click="$router.push('/login')">
              ← 返回登录
            </el-link>
          </div>
        </el-form>

        <!-- 步骤3: 重置密码 -->
        <el-form
            v-if="currentStep === 3"
            ref="resetFormRef"
            :model="resetForm"
            :rules="resetRules"
            label-width="0"
        >
          <el-form-item prop="newPassword">
            <el-input
                v-model="resetForm.newPassword"
                type="password"
                placeholder="请输入新密码"
                prefix-icon="Lock"
                show-password
                size="large"
                clearable
                @keyup.enter="confirmResetPassword"
            />
          </el-form-item>

          <el-form-item prop="confirmPassword">
            <el-input
                v-model="resetForm.confirmPassword"
                type="password"
                placeholder="请再次输入新密码"
                prefix-icon="Lock"
                show-password
                size="large"
                clearable
                @keyup.enter="confirmResetPassword"
            />
          </el-form-item>

          <el-form-item>
            <el-button
                type="primary"
                :loading="resetting"
                @click="confirmResetPassword"
                style="width: 100%; height: 48px; font-size: 16px;"
            >
              重置密码
            </el-button>
          </el-form-item>

          <div class="back-to-login">
            <el-link type="info" @click="$router.push('/login')">
              ← 返回登录
            </el-link>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElRow, ElCol } from 'element-plus'
import { Message } from '@element-plus/icons-vue'
import { userApi } from '../api/user'

const router = useRouter()

// 当前步骤：1-输入邮箱 2-验证邮箱 3-重置密码
const currentStep = ref(1)

// 表单refs
const emailFormRef = ref(null)
const verifyFormRef = ref(null)
const resetFormRef = ref(null)

// 状态
const sendingCode = ref(false)
const verifying = ref(false)
const resetting = ref(false)
const codeTimer = ref(0)
let timer = null

// 表单数据
const emailForm = reactive({
  email: ''
})

const verifyForm = reactive({
  verificationCode: ''
})

const resetForm = reactive({
  newPassword: '',
  confirmPassword: '',
  resetToken: '' // 从邮箱验证后获取的重置令牌
})

// 验证规则
const emailRules = reactive({
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
})

const verifyRules = reactive({
  verificationCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码应为6位数字', trigger: 'blur' }
  ]
})

const resetRules = reactive({
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度应在6-20位之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== resetForm.newPassword) {
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

  try {
    await emailFormRef.value.validate()
    sendingCode.value = true

    // 发送验证码（后端会同时验证邮箱是否存在）
    const response = await userApi.sendResetCode(emailForm.email)

    if (response.code === 200) {
      ElMessage.success('验证码已发送到您的邮箱')
      currentStep.value = 2

      // 开始60秒倒计时
      codeTimer.value = 60
      timer = setInterval(() => {
        codeTimer.value--
        if (codeTimer.value <= 0) {
          clearInterval(timer)
          timer = null
        }
      }, 1000)
    } else {
      ElMessage.error(response.msg || '该邮箱地址未注册或发送验证码失败')
    }
  } catch (error) {
    console.error('发送验证码失败:', error)
    ElMessage.error('发送验证码失败，请稍后重试')
  } finally {
    sendingCode.value = false
  }
}

// 验证验证码
const verifyCode = async () => {
  if (!verifyFormRef.value) return

  try {
    await verifyFormRef.value.validate()
    verifying.value = true

    // 直接进入密码重置步骤，后端会在最终重置时验证验证码
    ElMessage.success('验证码验证通过，请设置新密码')
    currentStep.value = 3
  } catch (error) {
    console.error('验证失败:', error)
    ElMessage.error('验证失败，请检查验证码是否正确')
  } finally {
    verifying.value = false
  }
}


// 确认重置密码
const confirmResetPassword = async () => {
  if (!resetFormRef.value) return

  try {
    await resetFormRef.value.validate()
    resetting.value = true

    // 调用后端API重置密码
    const response = await userApi.resetPassword({
      email: emailForm.email,
      code: verifyForm.verificationCode,
      password: resetForm.newPassword
    })

    if (response.code === 200) {
      ElMessage.success('密码重置成功！')
      ElMessageBox.confirm('密码重置成功，请使用新密码登录', '提示', {
        confirmButtonText: '去登录',
        cancelButtonText: '取消',
        type: 'success'
      }).then(() => {
        router.push('/login')
      }).catch(() => {
        // 用户点击取消，不做任何操作
      })
    } else {
      ElMessage.error(response.msg || '验证码错误或密码重置失败')
    }
  } catch (error) {
    console.error('密码重置失败:', error)
    ElMessage.error('密码重置失败，请稍后重试')
  } finally {
    resetting.value = false
  }
}

// 处理邮箱输入框回车键
const handleEmailEnter = async () => {
  if (!sendingCode.value && codeTimer.value === 0) {
    await sendVerificationCode()
  }
}

// 处理验证码输入框回车键
const handleVerifyCodeEnter = async () => {
  if (!verifying.value) {
    await verifyCode()
  }
}

// 组件卸载时清除倒计时
onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
  }
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
  max-width: 400px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 15px 50px rgba(0, 0, 0, 0.12);
  padding: 40px;
}

.card-header {
  text-align: center;
  margin-bottom: 30px;
}

.card-header h1 {
  font-size: 28px;
  color: #1D2129;
  margin: 0 0 10px 0;
  font-weight: 600;
}

.card-header p {
  font-size: 14px;
  color: #6B7280;
  margin: 0;
  line-height: 1.5;
}

.form-content {
  width: 100%;
}

.el-form-item {
  margin-bottom: 20px;
}

:deep(.el-input__inner) {
  height: 48px;
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

:deep(.el-input__prefix) {
  color: #86909C;
}

:deep(.el-button--primary) {
  background-color: #165DFF;
  border-color: #165DFF;
  transition: all 0.3s ease;
  font-weight: 500;
}

:deep(.el-button--primary):hover {
  background-color: #0E42D2;
  border-color: #0E42D2;
}

.code-btn {
  height: 48px;
  font-size: 14px;
  font-weight: 500;
}

.send-btn {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  border: none;
  border-radius: 10px;
  box-shadow: 0 4px 15px rgba(59, 130, 246, 0.3);
  transition: all 0.3s ease;
}

.send-btn:hover {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(59, 130, 246, 0.4);
}

.send-btn:disabled {
  background: #94a3b8;
  box-shadow: none;
  transform: none;
}

.verify-btn {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  border: none;
  border-radius: 10px;
  box-shadow: 0 4px 15px rgba(16, 185, 129, 0.3);
  transition: all 0.3s ease;
}

.verify-btn:hover {
  background: linear-gradient(135deg, #059669 0%, #047857 100%);
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(16, 185, 129, 0.4);
}

.resend-btn {
  background: transparent;
  border: 1px solid #e2e8f0;
  color: #64748b;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.resend-btn:hover:not(:disabled) {
  border-color: #3b82f6;
  color: #3b82f6;
  background: #f0f9ff;
}

.email-display {
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border: 1px solid #bfdbfe;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.1);
}

.email-display-icon {
  width: 40px;
  height: 40px;
  background: #3b82f6;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
  color: white;
  font-size: 18px;
}

.email-display-text {
  flex: 1;
}

.email-display-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e40af;
  margin-bottom: 4px;
}

.email-display-address {
  font-size: 13px;
  color: #64748b;
  word-break: break-all;
}

.back-to-login {
  text-align: center;
  margin-top: 20px;
}

:deep(.el-link) {
  font-size: 14px;
}

/* 响应式设计 */
@media (max-width: 480px) {
  .forgot-password-card {
    padding: 30px 20px;
    margin: 10px;
  }

  .card-header h1 {
    font-size: 24px;
  }

  :deep(.el-input__inner), .code-btn {
    height: 44px;
  }
}
</style>