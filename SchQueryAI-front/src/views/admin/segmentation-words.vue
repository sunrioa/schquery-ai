<template>
  <div class="segmentation-words-management">
    <div class="page-header">
      <div class="header-left">
        <el-button @click="goBack" type="info" plain class="back-button">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <h1>分词管理</h1>
      </div>
    </div>

  <!-- 搜索和操作区域 -->
    <div class="header-actions">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索分词..."
        clearable
        style="width: 250px"
        @input="filterWords"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button @click="toggleDarkMode" type="default" :icon="isDarkMode ? 'sunny' : 'moon'">
        {{ isDarkMode ? '浅色' : '暗夜' }}
      </el-button>
      <el-button type="primary" @click="showAddDialog = true">
        <el-icon><Plus /></el-icon>
        添加分词
      </el-button>
    </div>

    <!-- 统计信息 -->
    <div class="stats-cards">
      <el-card
        class="stat-card"
        :class="{ active: statusFilter === 'all' }"
        @click="toggleStatusFilter('all')"
      >
        <div class="stat-content">
          <div class="stat-number">{{ wordsData.length }}</div>
          <div class="stat-label">数据库总计</div>
        </div>
      </el-card>
      <el-card
        class="stat-card enabled"
        :class="{ active: statusFilter === 'enabled' }"
        @click="toggleStatusFilter('enabled')"
      >
        <div class="stat-content">
          <div class="stat-number">{{ enabledWordsCount }}</div>
          <div class="stat-label">已启用</div>
        </div>
      </el-card>
      <el-card
        class="stat-card disabled"
        :class="{ active: statusFilter === 'disabled' }"
        @click="toggleStatusFilter('disabled')"
      >
        <div class="stat-content">
          <div class="stat-number">{{ disabledWordsCount }}</div>
          <div class="stat-label">已禁用</div>
        </div>
      </el-card>
    </div>

    <!-- 分词列表 -->
    <el-card class="words-list-card" v-loading="loading">
      <div class="words-container">
        <div
          v-for="word in paginatedWords"
          :key="word.id"
          :class="[
            'word-item',
            {
              'enabled': word.status === 1,
              'disabled': word.status === 0
            }
          ]"
          @click="toggleWordStatus(word)"
          @contextmenu.prevent="showContextMenu($event, word)"
        >
          <span class="word-text">{{ word.word }}</span>
          <div class="word-status">
            <el-icon v-if="word.status === 1" class="status-icon enabled">
              <CircleCheck />
            </el-icon>
            <el-icon v-else class="status-icon disabled">
              <CircleClose />
            </el-icon>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-if="filteredWords.length === 0" class="empty-state">
          <el-empty description="当前条件下暂无分词数据">
            <el-button type="primary" @click="showAddDialog = true">添加分词</el-button>
          </el-empty>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination-container" v-if="filteredWords.length > pageSize">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="filteredWords.length"
          layout="prev, pager, next, total, jumper"
          @current-change="handlePageChange"
        />
        <div class="pagination-info">
          当前显示第 {{ (currentPage - 1) * pageSize + 1 }}-{{ Math.min(currentPage * pageSize, filteredWords.length) }} 项，共 {{ filteredWords.length }} 项
        </div>
      </div>
    </el-card>

    <!-- 右键菜单 -->
    <div
      v-if="contextMenuVisible"
      class="context-menu"
      :style="contextMenuStyle"
    >
      <div class="menu-item" @click.stop="editWord">
        <el-icon><Edit /></el-icon>
        修改分词
      </div>
      <div class="menu-item danger" @click.stop="deleteWord">
        <el-icon><Delete /></el-icon>
        删除分词
      </div>
    </div>

    <!-- 添加分词对话框 -->
    <el-dialog
      v-model="showAddDialog"
      title="添加分词"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        :model="addForm"
        :rules="addRules"
        ref="addFormRef"
        label-width="80px"
      >
        <el-form-item label="分词" prop="wordInput">
          <el-input
            v-model="addForm.wordInput"
            type="textarea"
            :rows="4"
            placeholder="请输入分词，多个分词用换行分隔"
          />
          <div class="help-text">每行输入一个分词，支持批量添加。分词用于对用户输入进行预处理的词汇切分。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAdd" :loading="submitLoading">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑分词对话框 -->
    <el-dialog
      v-model="showEditDialog"
      title="编辑分词"
      width="400px"
      :close-on-click-modal="false"
    >
      <el-form
        :model="editForm"
        :rules="editRules"
        ref="editFormRef"
        label-width="80px"
      >
        <el-form-item label="分词" prop="word">
          <el-input v-model="editForm.word" placeholder="请输入分词" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="editForm.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUpdate" :loading="submitLoading">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, ArrowLeft, Edit, Delete, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { useHttp } from '@/utils/http'
import { useRouter } from 'vue-router'

const router = useRouter()
const http = useHttp()

// 响应式数据
const loading = ref(false)
const submitLoading = ref(false)
const wordsData = ref([])
const showAddDialog = ref(false)
const showEditDialog = ref(false)
const searchKeyword = ref('')
const isDarkMode = ref(false)

// 分页数据
const currentPage = ref(1)
const pageSize = ref(100) // 每页显示100个分词

// 筛选状态：'all', 'enabled', 'disabled'
const statusFilter = ref('all')

// 统计数据
const stats = ref({
  total: 0,
  enabled: 0,
  disabled: 0
})

// 右键菜单相关
const contextMenuVisible = ref(false)
const contextMenuStyle = ref({})
const currentWord = ref(null)

// 添加表单
const addForm = reactive({
  wordInput: ''
})

// 编辑表单
const editForm = reactive({
  id: null,
  word: '',
  status: 1
})

// 表单验证规则
const addRules = {
  wordInput: [
    { required: true, message: '请输入分词', trigger: 'blur' }
  ]
}

const editRules = {
  word: [
    { required: true, message: '请输入分词', trigger: 'blur' },
    { min: 1, max: 50, message: '长度在 1 到 50 个字符', trigger: 'blur' }
  ]
}

// 表单引用
const addFormRef = ref()
const editFormRef = ref()

// 计算属性
const filteredWords = computed(() => {
  let result = wordsData.value

  // 按状态筛选
  if (statusFilter.value === 'enabled') {
    result = result.filter(word => word.status === 1)
  } else if (statusFilter.value === 'disabled') {
    result = result.filter(word => word.status === 0)
  }

  // 按关键词搜索
  if (searchKeyword.value.trim()) {
    result = result.filter(word =>
      word.word.toLowerCase().includes(searchKeyword.value.toLowerCase())
    )
  }

  return result
})

const enabledWordsCount = computed(() => {
  return stats.value.enabled
})

const disabledWordsCount = computed(() => {
  return stats.value.disabled
})

// 分页显示的分词
const paginatedWords = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredWords.value.slice(start, end)
})

// 获取统计数据
const getSegmentationWordsStats = async () => {
  try {
    const response = await http.get('/admin/UGC/segmentation/stats')

    if (response.code === 200) {
      stats.value = {
        total: response.data.total || 0,
        enabled: response.data.enabled || 0,
        disabled: response.data.disabled || 0
      }
    } else {
      console.warn('获取统计数据失败:', response.message)
      // 如果获取失败，使用前端计算
      const total = wordsData.value.length
      const enabled = wordsData.value.filter(word => word.status === 1).length
      const disabled = wordsData.value.filter(word => word.status === 0).length

      stats.value = { total, enabled, disabled }
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
    // 如果接口失败，使用前端计算
    const total = wordsData.value.length
    const enabled = wordsData.value.filter(word => word.status === 1).length
    const disabled = wordsData.value.filter(word => word.status === 0).length

    stats.value = { total, enabled, disabled }
  }
}

// 获取分词列表
const getSegmentationWordsList = async () => {
  loading.value = true
  try {
    let allWords = []
    let current = 1
    let size = 100 // 每次获取100条
    let hasMore = true

    // 循环获取所有数据，直到没有更多数据
    while (hasMore) {
      const params = {
        current: current,
        size: size
      }

      const response = await http.get('/admin/UGC/segmentation/query', { params })

      if (response.code === 200) {
        const records = response.data.records || []

        if (records.length === 0) {
          hasMore = false
        } else {
          allWords = allWords.concat(records)

          // 如果返回的数据少于请求的数量，说明已经获取完所有数据
          if (records.length < size) {
            hasMore = false
          } else {
            current++
          }
        }
      } else {
        ElMessage.error(response.message || '获取分词列表失败')
        hasMore = false
      }
    }

    // 设置状态字段
    allWords.forEach(word => {
      if (word.status === undefined) {
        word.status = 1
      }
    })

    wordsData.value = allWords
    stats.value.total = allWords.length
    console.log(`获取到 ${allWords.length} 个分词`)

    // 获取详细统计数据
    await getSegmentationWordsStats()
  } catch (error) {
    console.error('获取分词列表失败:', error)
    ElMessage.error('获取分词列表失败')
  } finally {
    loading.value = false
  }
}

// 过滤分词
const filterWords = () => {
  // 筛选时重置到第一页
  currentPage.value = 1
}

// 切换状态筛选
const toggleStatusFilter = (filter) => {
  statusFilter.value = filter
  currentPage.value = 1
}

// 处理分页变化
const handlePageChange = (page) => {
  currentPage.value = page
}


// 添加分词
const handleAdd = async () => {
  if (!addFormRef.value) return

  try {
    await addFormRef.value.validate()
    submitLoading.value = true

    // 将输入内容按行分割成数组
    const words = addForm.wordInput
      .split('\n')
      .map(word => word.trim())
      .filter(word => word.length > 0)

    if (words.length === 0) {
      ElMessage.error('请输入有效的分词')
      submitLoading.value = false
      return
    }

    console.log('准备发送分词:', words)
    const response = await http.post('/admin/UGC/segmentation/add', words)
    console.log('添加分词响应:', response)

    if (response.code === 200) {
      ElMessage.success(`成功添加 ${words.length} 个分词`)
      showAddDialog.value = false
      addForm.wordInput = ''
      // 重新获取数据以确保统计准确
      getSegmentationWordsList()
    } else {
      ElMessage.error(response.message || '添加分词失败')
    }
  } catch (error) {
    console.error('添加分词异常:', error)
    if (error && error.message !== '表单验证失败') {
      ElMessage.error(error.msg || error.message || '添加分词失败')
    }
  } finally {
    submitLoading.value = false
  }
}

// 编辑
const handleEdit = (row) => {
  editForm.id = row.id
  editForm.word = row.word
  editForm.status = row.status || 1
  showEditDialog.value = true
}

// 更新
const handleUpdate = async () => {
  if (!editFormRef.value) return

  try {
    await editFormRef.value.validate()
    submitLoading.value = true

    const response = await http.put('/admin/UGC/segmentation/update', [{
      id: editForm.id,
      word: editForm.word,
      status: editForm.status
    }])

    if (response.code === 200) {
      ElMessage.success('更新成功')
      showEditDialog.value = false
      getSegmentationWordsList()
    } else {
      ElMessage.error(response.message || '更新失败')
    }
  } catch (error) {
    if (error.message !== '表单验证失败') {
      console.error('更新失败:', error)
      ElMessage.error('更新失败')
    }
  } finally {
    submitLoading.value = false
  }
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除分词 "${row.word}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const response = await http.delete('/admin/UGC/segmentation/delete', {
      data: [row.id]
    })

    if (response.code === 200) {
      ElMessage.success('删除成功')
      getSegmentationWordsList()
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 切换分词状态
const toggleWordStatus = async (word) => {
  try {
    const newStatus = word.status === 1 ? 0 : 1
    const response = await http.put('/admin/UGC/segmentation/update', [{
      id: word.id,
      word: word.word,
      status: newStatus
    }])

    if (response.code === 200) {
      word.status = newStatus
      // 更新统计数据
      if (newStatus === 1) {
        stats.value.enabled++
        stats.value.disabled--
      } else {
        stats.value.enabled--
        stats.value.disabled++
      }
      ElMessage.success(newStatus === 1 ? '分词已启用' : '分词已禁用')
    } else {
      ElMessage.error(response.message || '状态更新失败')
    }
  } catch (error) {
    console.error('状态更新失败:', error)
    ElMessage.error('状态更新失败')
  }
}

// 显示右键菜单
const showContextMenu = (event, word) => {
  event.preventDefault()
  currentWord.value = word
  contextMenuVisible.value = true

  // 计算菜单位置，确保不超出视窗
  const x = event.clientX
  const y = event.clientY
  const menuWidth = 150
  const menuHeight = 80

  const finalX = x + menuWidth > window.innerWidth ? x - menuWidth : x
  const finalY = y + menuHeight > window.innerHeight ? y - menuHeight : y

  contextMenuStyle.value = {
    position: 'fixed',
    left: `${finalX}px`,
    top: `${finalY}px`,
    zIndex: 9999
  }
}

// 隐藏右键菜单
const hideContextMenu = () => {
  contextMenuVisible.value = false
}

// 编辑分词
const editWord = () => {
  if (currentWord.value) {
    editForm.id = currentWord.value.id
    editForm.word = currentWord.value.word
    editForm.status = currentWord.value.status
    showEditDialog.value = true
  }
  hideContextMenu()
}

// 删除分词
const deleteWord = async () => {
  if (!currentWord.value) return

  try {
    await ElMessageBox.confirm(
      `确定要删除分词 "${currentWord.value.word}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const response = await http.delete('/admin/UGC/segmentation/delete', {
      data: [currentWord.value.id]
    })

    if (response.code === 200) {
      ElMessage.success('删除成功')
      // 重新获取数据以确保统计准确
      getSegmentationWordsList()
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }

  hideContextMenu()
}

// 格式化日期
const formatDate = (date) => {
  if (!date) return ''
  return new Date(date).toLocaleString('zh-CN')
}

// 返回管理员界面
const goBack = () => {
  router.push('/admin/system-management')
}

// 切换暗夜模式
const toggleDarkMode = () => {
  isDarkMode.value = !isDarkMode.value
  const html = document.documentElement
  if (isDarkMode.value) {
    html.setAttribute('data-theme', 'dark')
    localStorage.setItem('theme', 'dark')
  } else {
    html.removeAttribute('data-theme')
    localStorage.setItem('theme', 'light')
  }
  window.dispatchEvent(new CustomEvent('theme-change', { detail: { isDark: isDarkMode.value } }))
}

// 页面加载
onMounted(() => {
  // 初始化主题
  const savedTheme = localStorage.getItem('theme')
  if (savedTheme === 'dark') {
    isDarkMode.value = true
    document.documentElement.setAttribute('data-theme', 'dark')
  }

  // 监听全局主题改变事件
  window.addEventListener('theme-change', (e) => {
    isDarkMode.value = e.detail?.isDark ?? false
  })

  getSegmentationWordsList()
})
</script>

<style scoped>
.segmentation-words-management {
  padding: 20px;
}

[data-theme="dark"] .segmentation-words-management {
  background-color: #1a1a1a;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.back-button {
  padding: 8px 16px;
  border-radius: 6px;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #333;
  text-align: center;
}

[data-theme="dark"] .page-header h1 {
  color: #e0e0e0;
}

.header-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 20px;
  gap: 10px;
  align-items: center;
}

/* 统计卡片样式 */
.stats-cards {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
}

.stat-card {
  flex: 1;
  text-align: center;
  cursor: pointer;
  user-select: none;
  position: relative;
  transition: all 0.2s ease;
}

.stat-card:hover {
  background-color: #f8f9fa;
}

.stat-card.active {
  background-color: #e3f2fd;
  border-color: #2196F3;
}

.stat-card.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  background-color: #2196F3;
}

.stat-card.enabled {
  border-left: 4px solid #67C23A;
}

.stat-card.enabled.active {
  border-left-color: #2196F3;
}

.stat-card.disabled {
  border-left: 4px solid #F56C6C;
}

.stat-card.disabled.active {
  border-left-color: #2196F3;
}

.stat-content {
  padding: 20px;
}

.stat-number {
  font-size: 28px;
  font-weight: bold;
  color: #333;
  margin-bottom: 5px;
}

[data-theme="dark"] .stat-number {
  color: #e0e0e0;
}

.stat-label {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

[data-theme="dark"] .stat-label {
  color: #999;
}

[data-theme="dark"] .stat-card {
  background-color: #2a2a2a !important;
  border-color: #444 !important;
}

[data-theme="dark"] .stat-card:hover {
  background-color: #333 !important;
}

[data-theme="dark"] .stat-card.active {
  background-color: rgba(33, 150, 243, 0.2) !important;
  border-color: #2196F3 !important;
}

/* 分词列表样式 */
.words-list-card {
  min-height: 500px;
}

[data-theme="dark"] .words-list-card {
  background-color: #2a2a2a !important;
  border-color: #444 !important;
}

.words-container {
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  padding: 20px;
  min-height: 400px;
  gap: 8px 12px;
  justify-content: flex-start;
  align-items: stretch;
}

.word-item {
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 500;
  user-select: none;
  white-space: nowrap;
  border: 1px solid transparent;
  transition: all 0.2s ease;
  padding: 8px 12px;
  margin: 2px;
  min-width: 80px;
  max-width: 200px;
  flex: 0 1 auto;
}

.word-item:hover {
  background-color: #f8f9fa;
  border-color: #d1d5db;
}

[data-theme="dark"] .word-item:hover {
  background-color: #333;
  border-color: #555;
}

.word-item.enabled {
  background: linear-gradient(135deg, #f0f9ff, #e0f2fe);
  color: #059669;
  border-color: #10b981;
}

.word-item.enabled:hover {
  background: linear-gradient(135deg, #dcfce7, #bbf7d0);
}

.word-item.disabled {
  background: linear-gradient(135deg, #fef2f2, #fee2e2);
  color: #dc2626;
  border-color: #ef4444;
}

.word-item.disabled:hover {
  background: linear-gradient(135deg, #fee2e2, #fecaca);
}

.word-text {
  flex: 1;
  margin-right: 8px;
  font-size: 14px;
  line-height: 1.4;
  word-break: break-all;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
}

.word-status {
  flex-shrink: 0;
}

.status-icon {
  font-size: 16px;
}

.status-icon.enabled {
  color: #10b981;
}

.status-icon.disabled {
  color: #ef4444;
}

/* 右键菜单样式 */
.context-menu {
  position: fixed;
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  padding: 8px 0;
  min-width: 150px;
  z-index: 9999;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 10px 16px;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
  transition: all 0.3s ease;
}

.menu-item:hover {
  background-color: #f5f7fa;
  color: #409EFF;
}

.menu-item.danger {
  color: #F56C6C;
}

.menu-item.danger:hover {
  background-color: #fef0f0;
  color: #F56C6C;
}

.menu-item .el-icon {
  margin-right: 8px;
  font-size: 16px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 空状态样式 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  color: #909399;
  text-align: center;
  width: 100%;
}

/* 分页样式 */
.pagination-container {
  margin-top: 20px;
  text-align: center;
  padding: 20px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.pagination-info {
  margin-top: 10px;
  font-size: 13px;
  color: #666;
}

/* 帮助文本样式 */
.help-text {
  font-size: 12px;
  color: #999;
  margin-top: 5px;
}

[data-theme="dark"] .help-text {
  color: #666;
}

/* Element Plus组件暗夜模式 */
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

[data-theme="dark"] :deep(.el-form-item__label) {
  color: #e0e0e0 !important;
}

[data-theme="dark"] :deep(.el-input__wrapper) {
  background-color: #1a1a1a !important;
  border-color: #444 !important;
}

[data-theme="dark"] :deep(.el-input__inner) {
  color: #e0e0e0 !important;
  background-color: #1a1a1a;
}

[data-theme="dark"] :deep(.el-textarea__inner) {
  background-color: #1a1a1a !important;
  color: #e0e0e0 !important;
  border-color: #444 !important;
}

[data-theme="dark"] :deep(.el-pagination__item) {
  background-color: #2a2a2a !important;
  color: #e0e0e0 !important;
}

[data-theme="dark"] :deep(.el-pagination__item.active) {
  background-color: #2196F3 !important;
  color: white !important;
}

[data-theme="dark"] :deep(.el-radio-group) {
  color: #e0e0e0 !important;
}

[data-theme="dark"] :deep(.el-radio__label) {
  color: #e0e0e0 !important;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: 15px;
    align-items: stretch;
  }

  .header-actions {
    justify-content: center;
  }

  .stats-cards {
    flex-direction: column;
  }

  .words-container {
    padding: 15px;
    gap: 6px 8px;
  }

  .word-item {
    padding: 6px 10px;
    min-width: 70px;
    max-width: 150px;
  }

  .word-text {
    font-size: 13px;
  }

  .status-icon {
    font-size: 14px;
  }

  .context-menu {
    min-width: 120px;
  }

  .menu-item {
    padding: 8px 12px;
    font-size: 13px;
  }
}

@media (max-width: 480px) {
  .segmentation-words-management {
    padding: 10px;
  }

  .stat-number {
    font-size: 24px;
  }

  .words-container {
    padding: 10px;
    gap: 4px 6px;
  }

  .word-item {
    padding: 5px 8px;
    min-width: 60px;
    max-width: 120px;
  }

  .word-text {
    font-size: 12px;
    margin-right: 6px;
  }

  .status-icon {
    font-size: 12px;
  }
}

/* 动画效果 */
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.word-item {
  animation: fadeIn 0.3s ease-out;
}

/* 加载状态样式 */
.words-list-card :deep(.el-loading-mask) {
  border-radius: 8px;
}
</style>