<template>
  <div class="admin-page segmentation-words-page">
    <div class="page-header">
      <div class="header-left">
        <h2>分词管理</h2>
        <p class="sub">UGC 分词词库维护（启用/禁用/编辑/删除）</p>
      </div>

      <div class="header-actions">
        <el-input
          v-model="filters.word"
          placeholder="搜索分词"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 120px" @change="handleSearch">
          <el-option label="全部" :value="undefined" />
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>

        <el-button type="primary" @click="openAdd">
          <el-icon><Plus /></el-icon>
          添加
        </el-button>
        <el-button @click="reload">刷新</el-button>
      </div>
    </div>

    <el-row :gutter="12" class="stats-row">
      <el-col :xs="24" :sm="8">
        <el-card shadow="never">
          <el-statistic title="总数" :value="stats.total" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never">
          <el-statistic title="已启用" :value="enabledCount" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never">
          <el-statistic title="已禁用" :value="disabledCount" />
        </el-card>
      </el-col>
    </el-row>

    <el-card class="table-card" shadow="never" v-loading="loading">
      <el-table :data="list" stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="id" label="ID" width="90" align="center" />
        <el-table-column prop="word" label="分词" min-width="220" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="160" align="center">
          <template #default="{ row }">
            <div class="status-cell">
              <el-tag :type="Number(row.status) === 1 ? 'success' : 'info'" size="small">
                {{ Number(row.status) === 1 ? '启用' : '禁用' }}
              </el-tag>
              <el-switch
                :model-value="Number(row.status) === 1"
                :disabled="statusUpdatingId === row.id"
                @change="(val) => updateStatus(row, val)"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="180" align="center">
          <template #default="{ row }">{{ formatDate(row.updateTime || row.createTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="removeRow(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="addVisible" title="添加分词" width="520px" :close-on-click-modal="false">
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="80px">
        <el-form-item label="分词" prop="wordInput">
          <el-input v-model="addForm.wordInput" type="textarea" :rows="4" placeholder="每行一个分词，支持批量添加" />
          <div class="help-text">每行输入一个分词。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAdd">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑分词" width="420px" :close-on-click-modal="false">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="80px">
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
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { useHttp } from '@/utils/http'

const http = useHttp()

const loading = ref(false)
const submitting = ref(false)
const statusUpdatingId = ref(null)

const filters = reactive({
  word: '',
  status: undefined
})

const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const list = ref([])

const stats = ref({
  total: 0,
  enabled: 0,
  disabled: 0
})

const enabledCount = computed(() => stats.value.enabled || 0)
const disabledCount = computed(() => stats.value.disabled || 0)

const fetchStats = async () => {
  try {
    const res = await http.get('/admin/UGC/segmentation/stats')
    if (res.code === 200) {
      stats.value = {
        total: res.data?.total || 0,
        enabled: res.data?.enabled || 0,
        disabled: res.data?.disabled || 0
      }
      return
    }
  } catch (e) {
    // ignore
  }

  const localTotal = list.value.length
  const localEnabled = list.value.filter((w) => Number(w.status) === 1).length
  stats.value = { total: localTotal, enabled: localEnabled, disabled: localTotal - localEnabled }
}

const fetchList = async () => {
  loading.value = true
  try {
    const params = {
      current: pageNum.value,
      size: pageSize.value
    }

    if (filters.status === 0 || filters.status === 1) {
      params.status = filters.status
    }

    if (filters.word && filters.word.trim()) {
      params.word = filters.word.trim()
    }

    const res = await http.get('/admin/UGC/segmentation/query', { params })
    if (res.code === 200) {
      const records = res.data?.records || []
      records.forEach((w) => {
        if (w.status === undefined || w.status === null) w.status = 1
      })
      list.value = records
      total.value = res.data?.total || 0
    } else {
      ElMessage.error(res.message || '获取分词列表失败')
    }
  } catch (error) {
    console.error('获取分词列表失败:', error)
    ElMessage.error('获取分词列表失败')
  } finally {
    loading.value = false
  }
}

const reload = async () => {
  await fetchList()
  await fetchStats()
}

const handleSearch = () => {
  pageNum.value = 1
  reload()
}

const handlePageChange = (page) => {
  pageNum.value = page
  fetchList()
}

const handleSizeChange = (size) => {
  pageSize.value = size
  pageNum.value = 1
  fetchList()
}

const updateStatus = async (row, enabled) => {
  if (!row?.id) return
  const nextStatus = enabled ? 1 : 0
  if (Number(row.status) === nextStatus) return

  statusUpdatingId.value = row.id
  try {
    const res = await http.put('/admin/UGC/segmentation/update', [
      {
        id: row.id,
        word: row.word,
        status: nextStatus
      }
    ])

    if (res.code === 200) {
      row.status = nextStatus
      await fetchStats()
      ElMessage.success(nextStatus === 1 ? '已启用' : '已禁用')
    } else {
      ElMessage.error(res.message || '状态更新失败')
    }
  } catch (error) {
    console.error('状态更新失败:', error)
    ElMessage.error('状态更新失败')
  } finally {
    statusUpdatingId.value = null
  }
}

const addVisible = ref(false)
const addFormRef = ref()
const addForm = reactive({
  wordInput: ''
})

const addRules = {
  wordInput: [{ required: true, message: '请输入分词', trigger: 'blur' }]
}

const openAdd = () => {
  addForm.wordInput = ''
  addVisible.value = true
}

const submitAdd = async () => {
  if (!addFormRef.value) return
  await addFormRef.value.validate()

  const words = addForm.wordInput
    .split('\n')
    .map((w) => w.trim())
    .filter(Boolean)

  if (words.length === 0) {
    ElMessage.error('请输入有效的分词')
    return
  }

  submitting.value = true
  try {
    const res = await http.post('/admin/UGC/segmentation/add', words)
    if (res.code === 200) {
      ElMessage.success(`成功添加 ${words.length} 个分词`)
      addVisible.value = false
      handleSearch()
    } else {
      ElMessage.error(res.message || '添加失败')
    }
  } finally {
    submitting.value = false
  }
}

const editVisible = ref(false)
const editFormRef = ref()
const editForm = reactive({
  id: null,
  word: '',
  status: 1
})

const editRules = {
  word: [
    { required: true, message: '请输入分词', trigger: 'blur' },
    { min: 1, max: 50, message: '长度在 1 到 50 个字符', trigger: 'blur' }
  ]
}

const openEdit = (row) => {
  editForm.id = row.id
  editForm.word = row.word
  editForm.status = Number(row.status) === 0 ? 0 : 1
  editVisible.value = true
}

const submitEdit = async () => {
  if (!editFormRef.value) return
  await editFormRef.value.validate()

  submitting.value = true
  try {
    const res = await http.put('/admin/UGC/segmentation/update', [
      {
        id: editForm.id,
        word: editForm.word,
        status: editForm.status
      }
    ])

    if (res.code === 200) {
      ElMessage.success('更新成功')
      editVisible.value = false
      reload()
    } else {
      ElMessage.error(res.message || '更新失败')
    }
  } finally {
    submitting.value = false
  }
}

const removeRow = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除分词 "${row.word}" 吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  const res = await http.delete('/admin/UGC/segmentation/delete', {
    data: [row.id]
  })

  if (res.code === 200) {
    ElMessage.success('删除成功')
    reload()
  } else {
    ElMessage.error(res.message || '删除失败')
  }
}

const formatDate = (date) => {
  if (!date) return ''
  const d = new Date(date)
  if (Number.isNaN(d.getTime())) return String(date)
  return d.toLocaleString('zh-CN')
}

onMounted(() => reload())
</script>

<style scoped>
.stats-row {
  margin-bottom: 16px;
}

.table-card {
  margin-bottom: 16px;
}

.status-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.help-text {
  font-size: 12px;
  color: var(--admin-muted, #6b7280);
  margin-top: 6px;
}
</style>
