<template>
  <div class="change-password-container">
    <!-- 居中卡片容器 -->
    <div class="change-password-card">
      <div class="card-header">
        <h1>修改密码</h1>
        <p>请输入当前密码和新密码来修改您的登录密码</p>
      </div>

      <!-- 修改密码表单 -->
      <div class="form-content">
        <el-form
            ref="passwordFormRef"
            :model="passwordForm"
            :rules="passwordRules"
            label-width="0"
        >
          <el-form-item prop="currentPassword">
            <el-input
                v-model="passwordForm.currentPassword"
                type="password"
                placeholder="请输入当前密码"
                prefix-icon="Lock"
                show-password
                size="large"
                clearable
            />
          </el-form-item>

          <el-form-item prop="newPassword">
            <el-input
                v-model="passwordForm.newPassword"
                type="password"
                placeholder="请输入新密码"
                prefix-icon="Lock"
                show-password
                size="large"
                clearable
            />
            <div class="password-tips">
              <p>• 密码长度不能少于6位</p>
              <p>• 建议包含字母和数字的组合</p>
            </div>
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
                :loading="updatingPassword"
                @click="updatePassword"
                style="width: 100%"
            >
              修改密码
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 返回聊天界面 -->
      <div class="back-to-chat">
        <el-link type="primary" @click="$router.push('/chat')">
          返回聊天
        </el-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '../api/user'

const router = useRouter()

// 密码表单引用
const passwordFormRef = ref(null)

// 密码表单数据
const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 加载状态
const updatingPassword = ref(false)

// 表单验证规则
const passwordRules = reactive({
  currentPassword: [
    { required: true, message: '请输入当前密码', trigger: 'blur' },
    { min: 6, message: '当前密码长度不能少于6位', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码长度不能少于6位', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (!value) {
          callback(new Error('请输入新密码'))
          return
        }

        // 密码强度检查
        const hasLetter = /[a-zA-Z]/.test(value)
        const hasNumber = /\d/.test(value)

        if (!hasLetter && !hasNumber) {
          callback(new Error('新密码必须包含字母或数字'))
          return
        }

        // 检查是否与当前密码相同
        if (value === passwordForm.currentPassword) {
          callback(new Error('新密码不能与当前密码相同'))
          return
        }

        callback()
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (!value) {
          callback(new Error('请确认新密码'))
          return
        }
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
})

// 修改密码
const updatePassword = async () => {
  if (!passwordFormRef.value) return

  await passwordFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        updatingPassword.value = true

        const passwordData = {
          password: passwordForm.currentPassword,
          rePassword: passwordForm.newPassword
        }

        const result = await userApi.updatePassword(passwordData)

        if (result.code === 200) {
          ElMessage.success('密码修改成功！请重新登录')

          // 清除token并跳转到登录页
          localStorage.removeItem('token')
          router.push('/login')
        } else {
          // 处理不同的错误情况
          const errorMsg = result.msg || '密码修改失败'

          if (errorMsg.includes('当前密码错误') || errorMsg.includes('原密码错误')) {
            ElMessage.error('当前密码不正确，请重新输入')
          } else if (errorMsg.includes('密码格式') || errorMsg.includes('长度')) {
            ElMessage.error('新密码格式不正确，请重新输入')
          } else {
            ElMessage.error(errorMsg)
          }
        }
      } catch (error) {
        console.error('修改密码失败:', error)
        ElMessage.error('修改密码失败，请检查网络连接')
      } finally {
        updatingPassword.value = false
      }
    }
  })
}
</script>

<style scoped>
.change-password-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(var(--app-primary-rgb), 0.08) 0%, rgba(var(--app-primary-rgb), 0.02) 100%),
    var(--app-bg);
  padding: 20px;
}

.change-password-card {
  width: 100%;
  max-width: 450px;
  background: var(--app-surface);
  border-radius: 16px;
  padding: 40px;
  box-shadow: var(--app-shadow-sm);
}

.card-header {
  text-align: center;
  margin-bottom: 40px;
}

.card-header h1 {
  font-size: 28px;
  color: var(--app-text);
  margin: 0 0 10px 0;
  font-weight: 600;
}

.card-header p {
  color: var(--app-muted-2);
  margin: 0;
  font-size: 14px;
}

.form-content {
  margin-bottom: 30px;
}

.el-form-item {
  margin-bottom: 24px;
}

/* 密码提示样式 */
.password-tips {
  margin-top: 8px;
  padding: 12px;
  background-color: var(--app-surface-2);
  border-radius: 6px;
  border-left: 3px solid var(--app-primary);
}

.password-tips p {
  margin: 0 0 4px 0;
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.4;
}

.password-tips p:last-child {
  margin-bottom: 0;
}

:deep(.el-input__inner) {
  height: 46px;
  border-radius: 8px;
  border-color: var(--app-border);
  padding: 0 15px;
  font-size: 15px;
  transition: all 0.3s ease;
}

:deep(.el-input__inner):focus {
  border-color: var(--app-primary);
  box-shadow: var(--app-ring);
}

:deep(.el-button--primary) {
  height: 48px;
  font-size: 16px;
  border-radius: 8px;
  background-color: var(--app-primary);
  border-color: var(--app-primary);
  transition: all 0.3s ease;
  font-weight: 500;
}

:deep(.el-button--primary):hover {
  background-color: var(--app-primary-hover);
  border-color: var(--app-primary-hover);
}

.back-to-chat {
  text-align: center;
  padding-top: 20px;
  border-top: 1px solid var(--app-border);
}

@media (max-width: 600px) {
  .change-password-card {
    padding: 30px 20px;
  }

  .card-header h1 {
    font-size: 24px;
  }
}
</style>
