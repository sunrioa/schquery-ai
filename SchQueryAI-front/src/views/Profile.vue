<template>
  <div class="profile-container">
    <!-- 居中卡片容器 -->
    <div class="profile-card">
      <div class="card-header">
        <h1>个人信息</h1>
        <p>管理您的个人资料和头像</p>
      </div>

      <!-- 个人信息表单 -->
      <div class="form-content">
        <!-- 头像上传区域 -->
        <div class="avatar-section">
          <div class="avatar-upload">
            <div class="avatar-preview" @click="triggerFileUpload">
              <img v-if="userStore.userAvatar" :src="userStore.userAvatar" alt="用户头像" class="avatar-image" />
              <img v-else :src="userStore.getDisplayAvatar()" alt="默认头像" class="avatar-image" />
              <div class="avatar-overlay">
                <el-icon><Camera /></el-icon>
                <span>更换头像</span>
              </div>
            </div>
            <input
              ref="fileInput"
              type="file"
              accept="image/*"
              style="display: none"
              @change="handleFileSelect"
            />
          </div>
          <div class="avatar-info">
            <h3>头像</h3>
            <p>支持JPG、PNG格式，文件大小不超过5MB</p>
            <el-button
              v-if="userStore.userAvatar"
              type="danger"
              size="small"
              plain
              @click="removeAvatar"
            >
              移除头像
            </el-button>
          </div>
        </div>

        <el-divider />

        <!-- 用户信息展示 -->
        <div class="user-info-section">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="用户名">
              {{ userStore.userInfo.userName || '未知用户' }}
            </el-descriptions-item>
            <el-descriptions-item label="邮箱">
              {{ userStore.userInfo.email || '未设置邮箱' }}
            </el-descriptions-item>
            <el-descriptions-item label="注册时间">
              {{ formatDate(userStore.userInfo.createTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="最后更新">
              {{ formatDate(userStore.userInfo.updateTime) }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-buttons">
        <el-button @click="$router.push('/chat')">
          返回聊天
        </el-button>
        <el-button type="primary" @click="$router.push('/password')">
          修改密码
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserFilled, Camera } from '@element-plus/icons-vue'
import { userApi } from '../api/user'
import { useUserStore } from '../stores/userStore'
import { getUserAvatar } from '../utils/avatarUtils'

const router = useRouter()
const userStore = useUserStore()

// 响应式数据
const fileInput = ref(null)
const uploading = ref(false)

// 触发文件选择
const triggerFileUpload = () => {
  fileInput.value?.click()
}

// 处理文件选择
const handleFileSelect = async (event) => {
  const file = event.target.files[0]
  if (!file) return

  // 文件类型验证
  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片格式的文件')
    return
  }

  // 文件大小验证（5MB）
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片文件大小不能超过5MB')
    return
  }

  await uploadAvatar(file)
}

// 上传头像
const uploadAvatar = async (file) => {
  try {
    uploading.value = true
    ElMessage.info('正在上传头像...')

    const result = await userApi.updateAvatar(file)

    if (result.code === 200) {
      ElMessage.success('头像上传成功！')
      // 更新头像显示
      const fileReader = new FileReader()
      fileReader.onload = (e) => {
        userStore.updateUserAvatar(e.target.result)
      }
      fileReader.readAsDataURL(file)
    } else {
      ElMessage.error(result.msg || '头像上传失败')
    }
  } catch (error) {
    console.error('头像上传失败:', error)
    ElMessage.error('头像上传失败，请检查网络连接')
  } finally {
    uploading.value = false
    // 清空文件输入
    if (fileInput.value) {
      fileInput.value.value = ''
    }
  }
}

// 移除头像
const removeAvatar = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要移除当前头像吗？此操作不可恢复。',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    // 调用后端API删除头像
    const result = await userApi.deleteAvatar()

    if (result.code === 200) {
      // 清除前端头像缓存，这会自动触发显示默认头像
      userStore.updateUserAvatar('')
      // 更新用户信息中的头像字段
      userStore.userInfo.avatar = null
      ElMessage.success('头像已移除')
    } else {
      ElMessage.error(result.msg || '头像移除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('移除头像失败:', error)
      ElMessage.error('移除头像失败，请稍后重试')
    }
  }
}

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return '未知'
  try {
    return new Date(dateString).toLocaleString('zh-CN')
  } catch {
    return '未知'
  }
}

// 页面加载时获取用户信息和头像
onMounted(async () => {
  await userStore.fetchUserInfo()
})
</script>

<style scoped>
.profile-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(var(--app-primary-rgb), 0.08) 0%, rgba(var(--app-primary-rgb), 0.02) 100%),
    var(--app-bg);
  padding: 20px;
}

.profile-card {
  width: 100%;
  max-width: 600px;
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

/* 头像上传区域 */
.avatar-section {
  display: flex;
  align-items: center;
  gap: 30px;
  margin-bottom: 30px;
}

.avatar-upload {
  position: relative;
}

.avatar-preview {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  border: 3px solid var(--app-border);
  cursor: pointer;
  overflow: hidden;
  position: relative;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: transparent;
}

.avatar-preview:hover {
  border-color: var(--app-primary);
  box-shadow: var(--app-shadow-xs);
}

.avatar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  font-size: 40px;
  color: var(--app-muted-2);
}

.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s ease;
  font-size: 12px;
  gap: 4px;
}

.avatar-preview:hover .avatar-overlay {
  opacity: 1;
}

.avatar-info h3 {
  margin: 0 0 8px 0;
  font-size: 16px;
  color: var(--app-text);
  font-weight: 600;
}

.avatar-info p {
  margin: 0 0 12px 0;
  color: var(--app-muted-2);
  font-size: 12px;
  line-height: 1.4;
}

/* 用户信息区域 */
.user-info-section {
  margin-top: 20px;
}

:deep(.el-descriptions__label) {
  font-weight: 600;
  color: var(--app-muted);
  width: 120px;
}

:deep(.el-descriptions__content) {
  color: var(--app-text);
}

/* 分割线 */
.el-divider {
  margin: 30px 0;
}

/* 操作按钮 */
.action-buttons {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding-top: 20px;
  border-top: 1px solid var(--app-border);
}

:deep(.el-button) {
  height: 40px;
  padding: 0 24px;
  font-size: 14px;
  border-radius: 8px;
}

:deep(.el-button--primary) {
  background-color: var(--app-primary);
  border-color: var(--app-primary);
}

:deep(.el-button--primary):hover {
  background-color: var(--app-primary-hover);
  border-color: var(--app-primary-hover);
}

/* 响应式设计 */
@media (max-width: 600px) {
  .profile-card {
    padding: 30px 20px;
  }

  .avatar-section {
    flex-direction: column;
    align-items: center;
    text-align: center;
    gap: 20px;
  }

  .card-header h1 {
    font-size: 24px;
  }

  .action-buttons {
    flex-direction: column;
    width: 100%;
  }

  .action-buttons .el-button {
    width: 100%;
  }
}
</style>
