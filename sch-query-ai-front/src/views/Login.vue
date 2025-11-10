<template>
  <div class="login-container">
    <!-- 居中卡片容器 -->
    <div class="login-card-container">
      <!-- 卡片内部左右布局 -->
      <div class="card-content">
        <!-- 左侧品牌展示区 -->
        <div class="login-promo">
          <div class="promo-content">
            <h1 class="brand-name">SchQuery智招平台</h1>
            <p class="brand-slogan">仲恺农业工程学院招生智能查询，一站式志愿决策助手</p>
            <div class="decor-pattern"></div>
          </div>
        </div>

        <!-- 右侧登录表单区 -->
        <div class="login-form-wrapper">
          <div class="card-header">
            <h2>用户登录</h2>
            <el-link type="primary" class="register-link" @click="$router.push('/register')">
              没有账号？去注册
            </el-link>
          </div>

          <el-form
              ref="loginFormRef"
              :model="loginForm"
              :rules="loginRules"
              label-width="80px"
              size="large"
          >
            <!-- 用户名输入框 -->
            <el-form-item label="用户名" prop="userName">
              <el-input
                  v-model="loginForm.userName"
                  placeholder="请输入用户名"
                  prefix-icon="User"
                  clearable
                  @keyup.enter="handleLogin"
              />
            </el-form-item>

            <!-- 密码输入框 -->
            <el-form-item label="密码" prop="password">
              <el-input
                  v-model="loginForm.password"
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
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// 1. 导入依赖（直接顶层导入，无需嵌套）
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '../api/user'

// 2. 初始化路由实例
const router = useRouter()

// 3. 声明响应式数据（顶层定义，自动暴露给模板）
const loginForm = reactive({
  userName: '',
  password: ''
})
const loginFormRef = ref(null) // 表单ref引用
const logging = ref(false) // 登录加载状态
const rememberMe = ref(false) // 记住我勾选状态

// 4. 表单验证规则
const loginRules = reactive({
  userName: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
})

// 5. 登录核心逻辑（顶层函数，自动暴露给模板）
const handleLogin = async () => {
  if (!loginFormRef.value) return

  // 表单验证
  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        logging.value = true // 开启加载
        const result = await userApi.login(loginForm)

        // 处理登录结果
        if (result.data) {
          console.log('Login successful, token received:', result.data)
          localStorage.setItem('token', result.data)
          console.log('Token stored in localStorage:', localStorage.getItem('token'))
        } else {
          throw new Error('登录返回数据格式异常')
        }

        // 处理"记住我"
        if (rememberMe.value) {
          localStorage.setItem('rememberedUser', loginForm.userName)
        } else {
          localStorage.removeItem('rememberedUser')
        }

        // 登录成功：提示+跳转
        ElMessage.success('登录成功！')
        router.push('/chat')
      } catch (error) {
        console.error('登录失败:', error)
        ElMessage.error('登录失败，请检查用户名或密码是否正确')
      } finally {
        logging.value = false // 关闭加载
      }
    }
  })
}

// 6. 初始化"记住我"的用户信息（页面加载时执行）
const rememberedUser = localStorage.getItem('rememberedUser')
if (rememberedUser) {
  loginForm.userName = rememberedUser
  rememberMe.value = true
}
</script>

<style scoped>
/* 样式部分完全不变，保留原视觉效果 */
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4edf5 100%);
  padding: 20px;
}

.login-card-container {
  width: 100%;
  max-width: 900px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 15px 50px rgba(0, 0, 0, 0.12);
  background: #fff;
}

.card-content {
  display: flex;
  height: 520px;
}

.login-promo {
  flex: 1;
  min-width: 400px;
  background: linear-gradient(135deg, #165DFF 0%, #0A2463 100%);
  color: #fff;
  padding: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.promo-content {
  max-width: 400px;
  z-index: 2;
  text-align: center;
}

.brand-name {
  font-size: 36px;
  font-weight: 700;
  margin-bottom: 20px;
  letter-spacing: 1px;
}

.brand-slogan {
  font-size: 16px;
  opacity: 0.9;
  line-height: 1.6;
  margin-bottom: 30px;
}

.decor-pattern {
  width: 200px;
  height: 200px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  margin: 0 auto;
}

.decor-pattern::before {
  content: '';
  position: absolute;
  top: -30px;
  right: -30px;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
}

.login-form-wrapper {
  flex: 1;
  min-width: 350px;
  background-color: #fff;
  padding: 40px 50px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.card-header h2 {
  font-size: 24px;
  color: #1D2129;
  margin: 0;
  font-weight: 600;
}

.register-link {
  font-size: 14px;
  padding: 0;
  height: auto;
}

.el-form {
  width: 100%;
}

.el-form-item {
  margin-bottom: 22px;
}

.el-form-item__label {
  font-size: 15px;
  color: #4E5969;
  padding-right: 15px;
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

:deep(.el-input__prefix) {
  color: #86909C;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 5px;
}

:deep(.el-checkbox__label) {
  font-size: 14px;
  color: #4E5969;
}

:deep(.el-link) {
  font-size: 14px;
  color: #165DFF;
}

:deep(.el-link):hover {
  color: #0E42D2;
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

:deep(.el-button--primary).is-loading {
  background-color: #165DFF;
  border-color: #165DFF;
}

@media (max-width: 900px) {
  .card-content {
    flex-direction: column;
    height: auto;
  }

  .login-promo {
    min-width: auto;
    padding: 30px 20px;
    height: 220px;
  }

  .login-form-wrapper {
    min-width: auto;
    padding: 30px 20px;
  }

  .brand-name {
    font-size: 28px;
  }

  .brand-slogan {
    font-size: 14px;
    margin-bottom: 15px;
  }

  .decor-pattern {
    width: 150px;
    height: 150px;
  }

  .decor-pattern::before {
    width: 100px;
    height: 100px;
    top: -25px;
    right: -25px;
  }
}
</style>