<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <h2>用户登录</h2>
          <el-link type="primary" @click="$router.push('/register')">没有账号？去注册</el-link>
        </div>
      </template>

      <el-form
        ref="loginForm"
        :model="loginForm"
        :rules="loginRules"
        label-width="80px"
        size="large"
      >
        <el-form-item label="用户名" prop="userName">
          <el-input
            :value="loginForm.userName"
            @input="(val) => { loginForm.userName = val; handleInput() }"
            placeholder="请输入用户名"
            prefix-icon="User"
            clearable
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            :value="loginForm.password"
            @input="(val) => { loginForm.password = val; handleInput() }"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            show-password
            clearable
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <div class="form-options">
            <el-checkbox v-model="rememberMe">记住我</el-checkbox>
            <el-link type="primary" @click="$router.push('/password')">忘记密码？</el-link>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="logging"
            @click="handleLogin"
            style="width: 100%"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script>
import { ref, reactive, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '../api/user'

export default {
  name: 'LoginPage',
  setup() {
    const router = useRouter()
    const loginForm = ref({
      userName: '',
      password: ''
    })

    const loginFormRef = ref(null)
    const logging = ref(false)
    const rememberMe = ref(false)

    const loginRules = reactive({
      userName: [
        { required: true, message: '请输入用户名', trigger: 'blur' }
      ],
      password: [
        { required: true, message: '请输入密码', trigger: 'blur' }
      ]
    })

    // 强制响应式更新
    const handleInput = async () => {
      // 使用nextTick确保DOM更新
      await nextTick()
    }

    // 登录处理
    const handleLogin = async () => {
      if (!loginFormRef.value) return

      await loginFormRef.value.validate(async (valid) => {
        if (valid) {
          try {
            logging.value = true
            const result = await userApi.login(loginForm.value)

            // 保存token
            localStorage.setItem('token', result.data)

            // 记住用户名
            if (rememberMe.value) {
              localStorage.setItem('rememberedUser', loginForm.value.userName)
            } else {
              localStorage.removeItem('rememberedUser')
            }

            ElMessage.success('登录成功！')
            router.push('/home')
          } catch (error) {
            console.error('登录失败:', error)
          } finally {
            logging.value = false
          }
        }
      })
    }

    // 页面加载时检查是否有记住的用户名
    const rememberedUser = localStorage.getItem('rememberedUser')
    if (rememberedUser) {
      loginForm.value.userName = rememberedUser
      rememberMe.value = true
    }

    return {
      loginForm,
      loginFormRef,
      loginRules,
      logging,
      rememberMe,
      handleLogin,
      handleInput
    }
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
}

.card-header {
  text-align: center;
}

.card-header h2 {
  margin: 0 0 10px 0;
  color: #303133;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.el-form-item {
  margin-bottom: 25px;
}

.el-form-item:last-child {
  margin-bottom: 0;
}
</style>