<template>
  <div class="admin-page system-logs-container">
    <div class="page-header">
      <div class="header-left">
        <h2>系统日志</h2>
        <p class="sub">操作日志查询</p>
      </div>
      <div class="header-actions">
        <el-input
          v-model="filters.operator"
          placeholder="操作人"
          clearable
          style="width: 160px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><User /></el-icon>
          </template>
        </el-input>
        <el-input
          v-model="filters.action"
          placeholder="操作类型"
          clearable
          style="width: 160px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Edit /></el-icon>
          </template>
        </el-input>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 120px" @change="handleSearch">
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="0" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          搜索
        </el-button>
        <el-button @click="handleReset">
          <el-icon><RefreshRight /></el-icon>
          重置
        </el-button>
      </div>
    </div>

    <!-- 日志列表 -->
    <el-card class="logs-table-card" shadow="never" v-loading="loading">
      <el-table :data="logsList" stripe style="width: 100%" :default-sort="{ prop: 'timestamp', order: 'descending' }">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="operator" label="操作人" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.operator }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="操作类型" width="140" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getActionTagType(row.action)">{{ row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="操作详情" min-width="260" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP地址" width="130" align="center" />
        <el-table-column prop="timestamp" label="操作时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatTime(row.timestamp) }}
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshRight } from '@element-plus/icons-vue'
import { operationLogApi } from '../../api/operationLog'

// 筛选条件
const filters = ref({
  operator: '',
  action: '',
  status: null
})

// 分页
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

// 数据
const logsList = ref([])
const loading = ref(false)

// 加载日志列表
const loadLogs = async () => {
  try {
    loading.value = true
    const response = await operationLogApi.getAllLogs(
      currentPage.value,
      pageSize.value,
      filters.value.action,
      filters.value.operator,
      filters.value.status
    )
    if (response.code === 200) {
      logsList.value = response.data.records || []
      total.value = response.data.total || 0
    } else {
      ElMessage.error(response.message || '获取日志列表失败')
    }
  } catch (error) {
    console.error('获取日志列表失败:', error)
    ElMessage.error('获取日志列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  currentPage.value = 1
  loadLogs()
}

// 重置
const handleReset = () => {
  filters.value = {
    operator: '',
    action: '',
    status: null
  }
  currentPage.value = 1
  loadLogs()
}

// 分页变化
const handleSizeChange = () => {
  currentPage.value = 1
  loadLogs()
}

const handlePageChange = () => {
  loadLogs()
}

// 格式化时间
const formatTime = (timestamp) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN')
}

// 根据操作类型返回不同的标签类型
const getActionTagType = (action) => {
  if (!action) return ''
  if (action.includes('删除')) return 'danger'
  if (action.includes('添加') || action.includes('新增')) return 'success'
  if (action.includes('修改') || action.includes('更新')) return 'warning'
  if (action.includes('登录')) return 'primary'
  return 'info'
}

onMounted(() => {
  loadLogs()
})
</script>
