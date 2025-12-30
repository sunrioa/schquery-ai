<template>
  <div class="admin-page user-management">
    <div class="page-header">
      <div class="header-left">
        <div>
          <h2>用户管理</h2>
          <p class="sub">用户列表、账号状态与登录历史</p>
        </div>
      </div>
      <div class="header-actions">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索用户名/邮箱..."
          clearable
          style="width: 180px"
          @input="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="roleFilter" placeholder="筛选" style="width: 120px">
          <el-option label="全部用户" value="all" />
          <el-option label="管理员" value="admin" />
          <el-option label="普通用户" value="user" />
          <el-option label="已禁用" value="disabled" />
        </el-select>
        <el-button type="primary" @click="refreshData" size="small">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <el-card class="user-table-card" v-loading="loading">
      <div class="table-wrapper">
        <el-table
          :data="filteredUserList"
          stripe
          style="width: 100%"
          :default-sort="{ prop: 'createTime', order: 'descending' }"
          :row-class-name="tableRowClassName"
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
      </div>

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
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Refresh,
  Location,
  Search
} from '@element-plus/icons-vue'
import { userApi } from '../../api/user'
import { getFullDeviceInfo } from '../../utils/browserUtils'

// 响应式数据
const loading = ref(false)
const userList = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const detailDialogVisible = ref(false)
const loginHistoryDialogVisible = ref(false)
const selectedUser = ref(null)
const avatarCache = ref({})
const searchKeyword = ref('')
const roleFilter = ref('all')

// 登录历史数据
const loginHistoryList = ref([])
const loginHistoryLoading = ref(false)
const loginHistoryPage = ref(1)
const loginHistoryPageSize = ref(10)
const loginHistoryTotal = ref(0)

// 计算属性 - 过滤后的用户列表
const filteredUserList = computed(() => {
  let result = userList.value
  
  // 按角色/状态筛选
  if (roleFilter.value === 'admin') {
    result = result.filter(u => u.role === 'admin')
  } else if (roleFilter.value === 'user') {
    result = result.filter(u => u.role === 'user')
  } else if (roleFilter.value === 'disabled') {
    result = result.filter(u => u.status === 0)
  }
  
  // 按关键词搜索
  if (searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(u => 
      (u.userName && u.userName.toLowerCase().includes(keyword)) ||
      (u.email && u.email.toLowerCase().includes(keyword))
    )
  }
  
  return result
})

// 搜索处理
const handleSearch = () => {
  // 搜索时自动过滤，无需额外操作
}

// 表格行样式
const tableRowClassName = ({ row }) => {
  if (row.status === 0) return 'disabled-row'
  return ''
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
  padding: 12px 16px;
  width: 100%;
  background: #f5f7fa;
  min-height: 100vh;
}

[data-theme="dark"] .user-management {
  background-color: #0f172a;
}

.user-management.is-embedded {
  padding: 8px;
  background: transparent;
  max-width: 100%;
  min-height: auto;
  height: 100%;
  display: flex;
  flex-direction: column;
}

[data-theme="dark"] .user-management.is-embedded {
  background: transparent;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  padding: 8px 12px;
  background: #ffffff;
  border-radius: 10px;
  box-shadow: 0 4px 12px rgba(31, 45, 61, 0.05);
  border: 1px solid #edf2f7;
}

.user-management.is-embedded .page-header {
  margin-bottom: 8px;
  box-shadow: none;
  border-color: #e5e7eb;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-button {
  padding: 6px 12px;
  border-radius: 6px;
}

.page-header h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

[data-theme="dark"] .page-header h1 {
  color: #e0e0e0;
}

[data-theme="dark"] .page-header {
  background: #111827;
  border-color: #1f2937;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

[data-theme="dark"] .user-management.is-embedded .page-header {
  border-color: #1f2937;
  box-shadow: none;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.user-table-card {
  margin-bottom: 10px;
  border: none;
  border-radius: 10px;
  box-shadow: 0 4px 12px rgba(31, 45, 61, 0.06);
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.user-table-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 12px;
}

.table-wrapper {
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

.user-management.is-embedded .user-table-card {
  margin-bottom: 10px;
  box-shadow: none;
  border: 1px solid #e5e7eb;
}

.user-table-card :deep(.el-card__header) {
  padding: 12px 14px;
  border-bottom: 1px solid #f0f0f0;
}

[data-theme="dark"] .user-table-card {
  background-color: #111827 !important;
  border: 1px solid #1f2937 !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

[data-theme="dark"] .user-management.is-embedded .user-table-card {
  box-shadow: none;
}

[data-theme="dark"] .user-table-card :deep(.el-card__body) {
  background-color: #111827 !important;
  border-radius: 0 0 16px 16px;
}

[data-theme="dark"] .user-table-card :deep(.el-card__header) {
  background-color: #111827 !important;
  border-bottom-color: #1f2937 !important;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #333;
}

[data-theme="dark"] .card-header {
  color: #e0e0e0;
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

[data-theme="dark"] .no-data {
  color: #666;
}

.pagination {
  display: flex;
  justify-content: center;
  padding: 12px;
  flex-shrink: 0;
  border-top: 1px solid #f0f0f0;
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

[data-theme="dark"] .history-tip {
  color: #999;
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

[data-theme="dark"] .device-info {
  background-color: rgba(64, 158, 255, 0.1);
  color: #b3d8ff;
  border-left-color: #409EFF;
}

/* Element Plus 组件暗夜模式适配 */
:deep(.el-card) {
  --el-card-bg-color: white;
  --el-card-border-color: #ebeef5;
}

[data-theme="dark"] :deep(.el-card) {
  --el-card-bg-color: #2a2a2a !important;
  --el-card-border-color: #444 !important;
  background-color: #2a2a2a !important;
  border-color: #444 !important;
  color: #e0e0e0;
}

[data-theme="dark"] :deep(.el-card__header) {
  border-bottom-color: #444 !important;
  background-color: #2a2a2a !important;
}

[data-theme="dark"] :deep(.el-card__body) {
  background-color: #2a2a2a !important;
  color: #e0e0e0 !important;
}

[data-theme="dark"] .user-table-card :deep(.el-table),
[data-theme="dark"] .user-table-card :deep(.el-table__header-wrapper),
[data-theme="dark"] .user-table-card :deep(.el-table__body-wrapper) {
  background-color: #2a2a2a !important;
}

[data-theme="dark"] :deep(.el-table) {
  background-color: #2a2a2a !important;
  color: #e0e0e0 !important;
}

[data-theme="dark"] :deep(.el-table__header) {
  background-color: #333 !important;
}

[data-theme="dark"] :deep(.el-table__header th) {
  background-color: #333 !important;
  color: #e0e0e0 !important;
  border-color: #444 !important;
}

[data-theme="dark"] :deep(.el-table__body) {
  background-color: #2a2a2a !important;
}

[data-theme="dark"] :deep(.el-table__body tr) {
  background-color: #2a2a2a !important;
}

[data-theme="dark"] :deep(.el-table__body tr:hover > td) {
  background-color: #333 !important;
}

[data-theme="dark"] :deep(.el-table__body td) {
  border-color: #444 !important;
  color: #e0e0e0 !important;
  background-color: #2a2a2a !important;
}

[data-theme="dark"] :deep(.el-table--striped .el-table__body tr.el-table__row--striped) {
  background-color: #242424 !important;
}

[data-theme="dark"] :deep(.el-table__cell) {
  background-color: #2a2a2a !important;
  color: #e0e0e0 !important;
}

[data-theme="dark"] :deep(.el-scrollbar__view) {
  background-color: #2a2a2a !important;
}

[data-theme="dark"] :deep(table) {
  background-color: #2a2a2a !important;
}

[data-theme="dark"] :deep(.el-pagination) {
  color: #e0e0e0;
}

[data-theme="dark"] :deep(.el-pagination__item) {
  background-color: #2a2a2a !important;
  color: #e0e0e0 !important;
}

[data-theme="dark"] :deep(.el-pagination__item.active) {
  background-color: #2196F3 !important;
  color: white !important;
}

[data-theme="dark"] :deep(.el-pagination button) {
  color: #e0e0e0 !important;
}

[data-theme="dark"] :deep(.el-pagination button:hover) {
  color: #fff !important;
}

[data-theme="dark"] :deep(.el-dialog) {
  --el-dialog-bg-color: #2a2a2a !important;
  background-color: #2a2a2a !important;
  color: #e0e0e0;
}

[data-theme="dark"] :deep(.el-dialog__header) {
  border-bottom-color: #444 !important;
}

[data-theme="dark"] :deep(.el-dialog__title) {
  color: #e0e0e0 !important;
}

[data-theme="dark"] :deep(.el-dialog__close) {
  color: #999 !important;
}

[data-theme="dark"] :deep(.el-dialog__close:hover) {
  color: #e0e0e0 !important;
}

[data-theme="dark"] :deep(.el-dialog__body) {
  color: #e0e0e0;
}

[data-theme="dark"] :deep(.el-descriptions) {
  color: #e0e0e0;
}

[data-theme="dark"] :deep(.el-descriptions__header) {
  border-bottom-color: #444 !important;
}

[data-theme="dark"] :deep(.el-descriptions__body) {
  border-color: #444 !important;
}

[data-theme="dark"] :deep(.el-descriptions__item) {
  border-bottom-color: #444 !important;
}

[data-theme="dark"] :deep(.el-descriptions__label) {
  color: #999 !important;
  border-right-color: #444 !important;
}

[data-theme="dark"] :deep(.el-descriptions__content) {
  color: #e0e0e0 !important;
}

[data-theme="dark"] :deep(.el-tag) {
  border-color: auto !important;
}

/* 禁用行样式 */
:deep(.el-table .disabled-row) {
  background-color: #fef0f0 !important;
}

[data-theme="dark"] :deep(.el-table .disabled-row) {
  background-color: rgba(245, 108, 108, 0.1) !important;
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

/* 在 AdminLayout 内统一风格 */
.user-management {
  padding: 16px;
  background: transparent;
  min-height: auto;
}

.user-management .page-header {
  padding: 0;
  background: transparent;
  border: none;
  box-shadow: none;
  margin-bottom: 16px;
  align-items: flex-end;
}

.user-table-card {
  border: 1px solid var(--admin-border, #eef2f7);
  box-shadow: none;
}

.user-table-card :deep(.el-card__body) {
  padding: 16px;
}

.pagination {
  justify-content: flex-end;
  border-top: none;
  padding: 16px 0 0;
}
</style>
