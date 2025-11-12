<template>
  <div class="user-management">
    <div class="page-header">
      <h1>用户管理</h1>
      <el-button @click="goBack" type="default">
        <el-icon><ArrowLeft /></el-icon>
        返回系统管理
      </el-button>
    </div>

    <el-card class="user-table-card">
      <template #header>
        <div class="card-header">
          <span>用户列表</span>
          <div class="header-actions">
            <el-button type="primary" @click="refreshData">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        :data="userList"
        v-loading="loading"
        stripe
        style="width: 100%"
        :default-sort="{ prop: 'createTime', order: 'descending' }"
      >
        <el-table-column type="index" label="序号" width="60" align="center" />
        
        <el-table-column prop="id" label="用户ID" width="80" align="center" />
        
        <el-table-column prop="userName" label="用户名" width="150" align="center">
          <template #default="{ row }">
            <div class="user-info">
              <el-avatar :size="32" :src="getAvatarUrl(row.avatar)" />
              <span class="user-name">{{ row.userName }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="email" label="邮箱" width="200" align="center" />
        
        <el-table-column prop="role" label="角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getRoleType(row.role)">
              {{ getRoleText(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column prop="lastLoginIp" label="最后登录IP" width="150" align="center">
          <template #default="{ row }">
            <span v-if="row.lastLoginIp">{{ row.lastLoginIp }}</span>
            <span v-else class="no-data">未登录</span>
          </template>
        </el-table-column>
        
        <el-table-column prop="lastLoginLocation" label="登录地点" width="200" align="center">
          <template #default="{ row }">
            <div v-if="row.lastLoginLocation && row.lastLoginLocation !== '未知'" class="location-info">
              <el-icon><Location /></el-icon>
              <span>{{ row.lastLoginLocation }}</span>
            </div>
            <span v-else class="no-data">未知</span>
          </template>
        </el-table-column>
        
        <el-table-column prop="lastLoginTime" label="最后登录时间" width="180" align="center">
          <template #default="{ row }">
            <span v-if="row.lastLoginTime">{{ formatTime(row.lastLoginTime) }}</span>
            <span v-else class="no-data">从未登录</span>
          </template>
        </el-table-column>
        
        <el-table-column prop="createTime" label="注册时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '弃用' }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column label="操作" width="250" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewLoginHistory(row)">
              登录历史
            </el-button>
            <el-button type="info" size="small" @click="viewUserDetail(row)">
              详情
            </el-button>
            <el-button 
              :type="row.status === 0 ? 'success' : 'danger'" 
              size="small" 
              @click="handleToggleBlacklist(row)"
            >
              {{ row.status === 0 ? '解除拉黑' : '拉黑' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 用户详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="用户详细信息" width="600px">
      <div v-if="selectedUser" class="user-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="用户ID">{{ selectedUser.id }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ selectedUser.userName }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ selectedUser.email }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag :type="getRoleType(selectedUser.role)">
              {{ getRoleText(selectedUser.role) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ formatTime(selectedUser.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatTime(selectedUser.updateTime) }}</el-descriptions-item>
          <el-descriptions-item label="最后登录IP">
            {{ selectedUser.lastLoginIp || '未登录' }}
          </el-descriptions-item>
          <el-descriptions-item label="最后登录时间">
            {{ selectedUser.lastLoginTime ? formatTime(selectedUser.lastLoginTime) : '从未登录' }}
          </el-descriptions-item>
          <el-descriptions-item label="登录地点" :span="2">
            <div v-if="selectedUser.lastLoginLocation && selectedUser.lastLoginLocation !== '未知'">
              {{ selectedUser.lastLoginLocation }}
            </div>
            <span v-else>未知</span>
          </el-descriptions-item>
          <el-descriptions-item label="账号状态">
            <el-tag :type="selectedUser.status === 1 ? 'success' : 'danger'">
              {{ selectedUser.status === 1 ? '正常' : '弃用' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <!-- 登录历史对话框 -->
    <el-dialog v-model="loginHistoryDialogVisible" title="登录历史" width="1000px" @open="loadLoginHistory">
      <div class="login-history">
        <p class="history-tip">显示用户 <strong>{{ selectedUser?.userName }}</strong> 的登录历史记录</p>
        
        <el-table
          :data="loginHistoryList"
          stripe
          style="width: 100%"
          :loading="loginHistoryLoading"
        >
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="loginIp" label="登录IP" width="150" align="center" />
          <el-table-column prop="location" label="登录地点" width="200" align="center" />
          <el-table-column prop="loginTime" label="登录时间" width="200" align="center">
            <template #default="{ row }">
              {{ formatTime(row.loginTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="status" label="登录状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                {{ row.status === 1 ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="userAgent" label="登录设备" width="280" align="center">
            <template #default="{ row }">
              <span v-if="row.userAgent" class="device-info">{{ getFullDeviceInfo(row.userAgent) }}</span>
              <span v-else class="no-data">未知</span>
            </template>
          </el-table-column>
          <el-table-column prop="failReason" label="失败原因" width="200" align="center">
            <template #default="{ row }">
              <span v-if="row.status === 1" class="no-data">—</span>
              <span v-else>{{ row.failReason || '未知' }}</span>
            </template>
          </el-table-column>
        </el-table>

        <!-- 登录历史分页 -->
        <div class="pagination" style="margin-top: 20px">
          <el-pagination
            v-model:current-page="loginHistoryPage"
            v-model:page-size="loginHistoryPageSize"
            :page-sizes="[5, 10, 20, 50]"
            :total="loginHistoryTotal"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleHistorySizeChange"
            @current-change="handleHistoryPageChange"
          />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Refresh,
  Location
} from '@element-plus/icons-vue'
import { userApi } from '../../api/user'
import { getFullDeviceInfo } from '../../utils/browserUtils'

const router = useRouter()

// 响应式数据
const loading = ref(false)
const userList = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const detailDialogVisible = ref(false)
const loginHistoryDialogVisible = ref(false)
const selectedUser = ref(null)
const avatarCache = ref({}) // 头像缓存
// 登录历史数据
const loginHistoryList = ref([])
const loginHistoryLoading = ref(false)
const loginHistoryPage = ref(1)
const loginHistoryPageSize = ref(10)
const loginHistoryTotal = ref(0)

// 返回系统管理
const goBack = () => {
  router.push('/admin/system-management')
}

// 获取用户列表
const fetchUserList = async () => {
  try {
    loading.value = true
    const response = await userApi.getAllUsers(currentPage.value, pageSize.value)
    
    if (response.code === 200) {
      const pageData = response.data
      userList.value = pageData.records || []
      total.value = pageData.total || 0
      
      // 加载每个用户的头像
      loadAvatarsForUsers(userList.value)
      
      ElMessage.success('用户列表加载成功')
    } else {
      ElMessage.error(response.message || '获取用户列表失败')
    }
  } catch (error) {
    console.error('获取用户列表失败:', error)
    ElMessage.error('获取用户列表失败')
  } finally {
    loading.value = false
  }
}

// 加载用户头像
const loadAvatarsForUsers = async (users) => {
  for (const user of users) {
    if (user.avatar && !avatarCache.value[user.avatar]) {
      try {
        const response = await userApi.getAvatarById(user.avatar)
        if (response.code === 200 && response.data) {
          // 后端已经返回了完整的 data:image/png;base64,... 格式，直接使用
          avatarCache.value[user.avatar] = response.data
        }
      } catch (error) {
        console.error(`加载用户 ${user.id} 头像失败:`, error)
      }
    }
  }
}

// 刷新数据
const refreshData = () => {
  fetchUserList()
}

// 分页处理
const handlePageChange = (page) => {
  currentPage.value = page
  fetchUserList()
}

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  fetchUserList()
}

// 查看用户详情
const viewUserDetail = (user) => {
  selectedUser.value = user
  detailDialogVisible.value = true
}

// 加载用户登录历史
const loadLoginHistory = async () => {
  if (!selectedUser.value) return
  
  try {
    loginHistoryLoading.value = true
    const response = await userApi.getUserLoginHistory(
      selectedUser.value.id,
      loginHistoryPage.value,
      loginHistoryPageSize.value
    )
    
    if (response.code === 200) {
      // 后端返回 List 数组，接口直接返回该页数据
      if (Array.isArray(response.data)) {
        loginHistoryList.value = response.data
        // 简单处理：对于简单分页，用数组长度表示总数
        loginHistoryTotal.value = loginHistoryList.value.length
      } else if (response.data && Array.isArray(response.data.records)) {
        loginHistoryList.value = response.data.records
        loginHistoryTotal.value = response.data.total || response.data.records.length
      }
    } else {
      ElMessage.error(response.message || '获取登录历史失败')
    }
  } catch (error) {
    console.error('获取登录历史失败:', error)
    ElMessage.error('获取登录历史失败')
  } finally {
    loginHistoryLoading.value = false
  }
}

// 查看登录历史
const viewLoginHistory = (user) => {
  selectedUser.value = user
  loginHistoryPage.value = 1  // 重置分页
  loginHistoryList.value = [] // 清空数据
  loginHistoryDialogVisible.value = true
  // 对话框打开时会自动触发 @open 事件，执行 loadLoginHistory()
}

// 登录历史分页处理
const handleHistoryPageChange = (page) => {
  loginHistoryPage.value = page
  loadLoginHistory()
}

const handleHistorySizeChange = (size) => {
  loginHistoryPageSize.value = size
  loginHistoryPage.value = 1
  loadLoginHistory()
}

// 切换用户拉黑状态
const handleToggleBlacklist = async (row) => {
  try {
    let message = ''
    let action = ''
    
    if (row.status === 0) {
      // 账户当前是弃用，执行解除拉黑
      message = `是否确定解除拉黑用户 "${row.userName}" ？解除拉黑后该用户可以正常登录`
      action = 'unblacklist'
    } else {
      // 账户当前是正常，执行拉黑
      message = `是否确定拉黑用户 "${row.userName}" ？拉黑后该用户无法登录`
      action = 'blacklist'
    }
    
    await ElMessageBox.confirm(
      message,
      action === 'blacklist' ? '拉黑用户' : '解除拉黑',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    // 执行响应的操作
    let response
    if (action === 'blacklist') {
      response = await userApi.blacklistUser(row.id)
    } else {
      response = await userApi.unblacklistUser(row.id)
    }
    
    if (response.code === 200) {
      const successMsg = action === 'blacklist' ? `用户 ${row.userName} 已拉黑` : `用户 ${row.userName} 拉黑已解除`
      ElMessage.success(successMsg)
      // 刷新用户列表
      fetchUserList()
    } else {
      ElMessage.error(response.msg || (action === 'blacklist' ? '拉黑失败' : '解除拉黑失败'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('操作错误:', error)
      ElMessage.error('操作失败')
    }
  }
}

// 获取头像URL
const getAvatarUrl = (avatarId) => {
  if (!avatarId) {
    return 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
  }
  
  // 如果缓存中有，直接返回
  if (avatarCache.value[avatarId]) {
    return avatarCache.value[avatarId]
  }
  
  // 否则返回默认头像
  return 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
}

// 获取角色类型
const getRoleType = (role) => {
  const typeMap = {
    'admin': 'danger',
    'worker': 'warning',
    'user': 'info'
  }
  return typeMap[role] || 'info'
}

// 获取角色文本
const getRoleText = (role) => {
  const textMap = {
    'admin': '管理员',
    'worker': '员工',
    'user': '普通用户'
  }
  return textMap[role] || role
}

// 时间格式化
const formatTime = (timestamp) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 页面加载时获取数据
onMounted(() => {
  fetchUserList()
})
</script>

<style scoped>
.user-management {
  padding: 20px;
  max-width: 1600px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0;
  color: #333;
}

.user-table-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-name {
  font-weight: 500;
}

.location-info {
  display: flex;
  align-items: center;
  gap: 5px;
  justify-content: center;
}

.no-data {
  color: #999;
  font-style: italic;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.user-detail {
  padding: 10px 0;
}

.login-history {
  min-height: 200px;
}

.history-tip {
  margin-bottom: 15px;
  color: #666;
}

.history-tip strong {
  color: #409EFF;
}

.device-info {
  display: inline-block;
  padding: 4px 8px;
  background-color: #f0f9ff;
  border-left: 3px solid #409EFF;
  color: #333;
  font-size: 12px;
  border-radius: 2px;
  max-width: 250px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .user-management {
    padding: 10px;
  }

  .page-header {
    flex-direction: column;
    gap: 10px;
    align-items: flex-start;
  }
}
</style>
