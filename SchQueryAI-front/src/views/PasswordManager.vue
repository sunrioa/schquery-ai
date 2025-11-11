<template>
  <div class="password-manager">
    <el-container>
      <el-header class="header">
        <div class="header-content">
          <h1>密码管理</h1>
          <el-button type="primary" @click="$router.push('/home')">返回首页</el-button>
        </div>
      </el-header>

      <el-main>
        <el-row :gutter="20">
          <!-- 修改密码 -->
          <el-col :span="12">
            <el-card class="password-card">
              <template #header>
                <div class="card-header">
                  <h3>修改密码</h3>
                </div>
              </template>

              <el-form
                ref="updateFormRef"
                :model="updateForm"
                :rules="updateRules"
                label-width="100px"
                size="default"
              >
                <el-form-item label="原密码" prop="oldPassword">
                  <el-input
                    v-model="updateForm.oldPassword"
                    type="password"
                    placeholder="请输入原密码"
                    show-password
                    clearable
                  />
                </el-form-item>

                <el-form-item label="新密码" prop="newPassword">
                  <el-input
                    v-model="updateForm.newPassword"
                    type="password"
                    placeholder="请输入新密码"
                    show-password
                    clearable
                  />
                </el-form-item>

                <el-form-item label="确认新密码" prop="rePassword">
                  <el-input
                    v-model="updateForm.rePassword"
                    type="password"
                    placeholder="请再次输入新密码"
                    show-password
                    clearable
                  />
                </el-form-item>

                <el-form-item>
                  <el-button
                    type="primary"
                    :loading="updating"
                    @click="handleUpdatePassword"
                  >
                    修改密码
                  </el-button>
                </el-form-item>
              </el-form>
            </el-card>
          </el-col>

          <!-- 找回密码 -->
          <el-col :span="12">
            <el-card class="password-card">
              <template #header>
                <div class="card-header">
                  <h3>找回密码</h3>
                </div>
              </template>

              <el-form
                ref="findFormRef"
                :model="findForm"
                :rules="findRules"
                label-width="100px"
                size="default"
              >
                <el-form-item label="邮箱" prop="email">
                  <el-input
                    v-model="findForm.email"
                    placeholder="请输入邮箱"
                    clearable
                  >
                    <template #append>
                      <el-button
                        :disabled="findCodeTimer > 0"
                        :loading="sendingFindCode"
                        @click="sendFindPasswordCode"
                      >
                        {{ findCodeTimer > 0 ? `${findCodeTimer}s` : '发送验证码' }}
                      </el-button>
                    </template>
                  </el-input>
                </el-form-item>

                <el-form-item label="验证码" prop="code">
                  <el-input
                    v-model="findForm.code"
                    placeholder="请输入验证码"
                    maxlength="6"
                    clearable
                  />
                </el-form-item>

                <el-form-item label="新密码" prop="password">
                  <el-input
                    v-model="findForm.password"
                    type="password"
                    placeholder="请输入新密码"
                    show-password
                    clearable
                  />
                </el-form-item>

                <el-form-item label="确认密码" prop="rePassword">
                  <el-input
                    v-model="findForm.rePassword"
                    type="password"
                    placeholder="请再次输入新密码"
                    show-password
                    clearable
                  />
                </el-form-item>

                <el-form-item>
                  <el-button
                    type="primary"
                    :loading="finding"
                    @click="handleFindPassword"
                  >
                    找回密码
                  </el-button>
                </el-form-item>
              </el-form>
            </el-card>
          </el-col>
        </el-row>
      </el-main>
    </el-container>
  </div>
</template>

<script>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '../api/user'

export default {
  name: 'PasswordManager',
  setup() {
    const router = useRouter()

    // 修改密码表单
    const updateForm = reactive({
      oldPassword: '',
      newPassword: '',
      rePassword: ''
    })

    // 找回密码表单
    const findForm = reactive({
      email: '',
      code: '',
      password: '',
      rePassword: ''
    })

    const updateFormRef = ref(null)
    const findFormRef = ref(null)
    const updating = ref(false)
    const finding = ref(false)
    const sendingFindCode = ref(false)
    const findCodeTimer = ref(0)

    // 验证规则
    const validateNewPass = (rule, value, callback) => {
      if (value === '') {
        callback(new Error('请输入新密码'))
      } else if (value.length < 6) {
        callback(new Error('密码长度不能少于6位'))
      } else {
        if (updateForm.rePassword !== '') {
          updateFormRef.value.validateField('rePassword')
        }
        callback()
      }
    }

    const validateNewPass2 = (rule, value, callback) => {
      if (value === '') {
        callback(new Error('请再次输入新密码'))
      } else if (value !== updateForm.newPassword) {
        callback(new Error('两次输入密码不一致'))
      } else {
        callback()
      }
    }

    const validateFindPass = (rule, value, callback) => {
      if (value === '') {
        callback(new Error('请输入新密码'))
      } else if (value.length < 6) {
        callback(new Error('密码长度不能少于6位'))
      } else {
        if (findForm.rePassword !== '') {
          findFormRef.value.validateField('rePassword')
        }
        callback()
      }
    }

    const validateFindPass2 = (rule, value, callback) => {
      if (value === '') {
        callback(new Error('请再次输入密码'))
      } else if (value !== findForm.password) {
        callback(new Error('两次输入密码不一致'))
      } else {
        callback()
      }
    }

    const updateRules = reactive({
      oldPassword: [
        { required: true, message: '请输入原密码', trigger: 'blur' }
      ],
      newPassword: [
        { validator: validateNewPass, trigger: 'blur' }
      ],
      rePassword: [
        { validator: validateNewPass2, trigger: 'blur' }
      ]
    })

    const findRules = reactive({
      email: [
        { required: true, message: '请输入邮箱', trigger: 'blur' },
        { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
      ],
      code: [
        { required: true, message: '请输入验证码', trigger: 'blur' },
        { len: 6, message: '验证码长度为6位', trigger: 'blur' }
      ],
      password: [
        { validator: validateFindPass, trigger: 'blur' }
      ],
      rePassword: [
        { validator: validateFindPass2, trigger: 'blur' }
      ]
    })

    // 修改密码
    const handleUpdatePassword = async () => {
      if (!updateFormRef.value) return

      await updateFormRef.value.validate(async (valid) => {
        if (valid) {
          try {
            updating.value = true
            await userApi.updatePassword(updateForm)
            ElMessage.success('密码修改成功！')

            // 清空表单
            Object.keys(updateForm).forEach(key => {
              updateForm[key] = ''
            })
          } catch (error) {
            console.error('修改密码失败:', error)
          } finally {
            updating.value = false
          }
        }
      })
    }

    // 发送找回密码验证码
    const sendFindPasswordCode = async () => {
      if (!findForm.email) {
        ElMessage.error('请先输入邮箱')
        return
      }

      if (!/^[\w-]+(\.[\w-]+)*@([\w-]+\.)+[a-zA-Z]{2,7}$/.test(findForm.email)) {
        ElMessage.error('请输入正确的邮箱格式')
        return
      }

      try {
        sendingFindCode.value = true
        await userApi.sendFindPasswordCode({
          email: findForm.email
        })
        ElMessage.success('验证码发送成功')

        // 开始倒计时
        findCodeTimer.value = 60
        const timer = setInterval(() => {
          findCodeTimer.value--
          if (findCodeTimer.value <= 0) {
            clearInterval(timer)
          }
        }, 1000)
      } catch (error) {
        console.error('发送验证码失败:', error)
      } finally {
        sendingFindCode.value = false
      }
    }

    // 找回密码
    const handleFindPassword = async () => {
      if (!findFormRef.value) return

      await findFormRef.value.validate(async (valid) => {
        if (valid) {
          try {
            finding.value = true
            await userApi.findPassword(findForm)
            ElMessage.success('密码重置成功！请使用新密码登录')

            // 跳转到登录页面
            router.push('/login')
          } catch (error) {
            console.error('找回密码失败:', error)
          } finally {
            finding.value = false
          }
        }
      })
    }

    return {
      updateForm,
      findForm,
      updateFormRef,
      findFormRef,
      updateRules,
      findRules,
      updating,
      finding,
      sendingFindCode,
      findCodeTimer,
      handleUpdatePassword,
      sendFindPasswordCode,
      handleFindPassword
    }
  }
}
</script>

<style scoped>
.password-manager {
  min-height: 100vh;
  background-color: #f5f5f5;
}

.header {
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
}

.header-content h1 {
  margin: 0;
  color: #303133;
}

.password-card {
  height: 100%;
  border-radius: 8px;
}

.card-header h3 {
  margin: 0;
  color: #303133;
}

.el-form-item {
  margin-bottom: 20px;
}

.el-form-item:last-child {
  margin-bottom: 0;
}
</style>