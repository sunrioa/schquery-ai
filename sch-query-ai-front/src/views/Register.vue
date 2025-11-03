<template>
  <div class="register-container">
    <el-card class="register-card">
      <template #header>
        <div class="card-header">
          <h2>用户注册</h2>
          <el-link type="primary" @click="$router.push('/login')">已有账号？去登录</el-link>
        </div>
      </template>

      <el-form
        ref="registerForm"
        :model="registerForm"
        :rules="registerRules"
        label-width="80px"
        size="large"
      >
        <el-form-item label="用户名" prop="userName">
          <el-input
            :value="registerForm.userName"
            @input="(val) => { registerForm.userName = val; handleInput() }"
            placeholder="请输入用户名"
            prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input
            :value="registerForm.email"
            @input="(val) => { registerForm.email = val; handleInput() }"
            placeholder="请输入邮箱"
            prefix-icon="Message"
            clearable
          >
            <template #append>
              <el-button
                :disabled="codeTimer > 0"
                :loading="sendingCode"
                @click="sendRegisterCode"
              >
                {{ codeTimer > 0 ? `${codeTimer}s` : '发送验证码' }}
              </el-button>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="验证码" prop="code">
          <el-input
            :value="registerForm.code"
            @input="(val) => { registerForm.code = val; handleInput() }"
            placeholder="请输入验证码"
            prefix-icon="Key"
            maxlength="6"
            clearable
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            :value="registerForm.password"
            @input="(val) => { registerForm.password = val; handleInput() }"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            show-password
            clearable
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="rePassword">
          <el-input
            :value="registerForm.rePassword"
            @input="(val) => { registerForm.rePassword = val; handleInput() }"
            type="password"
            placeholder="请再次输入密码"
            prefix-icon="Lock"
            show-password
            clearable
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="registering"
            @click="handleRegister"
            style="width: 100%"
          >
            注册
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
  name: 'RegisterPage',
  setup() {
    const router = useRouter()
    const registerForm = ref({
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

    // 验证规则
    const validatePass = (rule, value, callback) => {
      if (value === '') {
        callback(new Error('请输入密码'))
      } else if (value.length < 6) {
        callback(new Error('密码长度不能少于6位'))
      } else {
        if (registerForm.value.rePassword !== '') {
          registerFormRef.value.validateField('rePassword')
        }
        callback()
      }
    }

    const validatePass2 = (rule, value, callback) => {
      if (value === '') {
        callback(new Error('请再次输入密码'))
      } else if (value !== registerForm.value.password) {
        callback(new Error('两次输入密码不一致'))
      } else {
        callback()
      }
    }

    const registerRules = reactive({
      userName: [
        { required: true, message: '请输入用户名', trigger: 'blur' },
        { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
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

    // 强制响应式更新
    const handleInput = async () => {
      // 使用nextTick确保DOM更新
      await nextTick()
    }

    // 发送注册验证码
    const sendRegisterCode = async () => {
      if (!registerForm.value.email) {
        ElMessage.error('请先输入邮箱')
        return
      }

      if (!/^[\w-]+(\.[\w-]+)*@([\w-]+\.)+[a-zA-Z]{2,7}$/.test(registerForm.value.email)) {
        ElMessage.error('请输入正确的邮箱格式')
        return
      }

      try {
        sendingCode.value = true
        await userApi.sendRegisterCode({
          email: registerForm.value.email
        })
        ElMessage.success('验证码发送成功')

        // 开始倒计时
        codeTimer.value = 60
        const timer = setInterval(() => {
          codeTimer.value--
          if (codeTimer.value <= 0) {
            clearInterval(timer)
          }
        }, 1000)
      } catch (error) {
        console.error('发送验证码失败:', error)
      } finally {
        sendingCode.value = false
      }
    }

    // 注册
    const handleRegister = async () => {
      if (!registerFormRef.value) return

      await registerFormRef.value.validate(async (valid) => {
        if (valid) {
          try {
            registering.value = true
            await userApi.register(registerForm.value)
            ElMessage.success('注册成功！')
            router.push('/login')
          } catch (error) {
            console.error('注册失败:', error)
          } finally {
            registering.value = false
          }
        }
      })
    }

    return {
      registerForm,
      registerFormRef,
      registerRules,
      registering,
      sendingCode,
      codeTimer,
      sendRegisterCode,
      handleRegister,
      handleInput
    }
  }
}
</script>

<style scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.register-card {
  width: 100%;
  max-width: 500px;
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

.el-form-item {
  margin-bottom: 25px;
}
</style>