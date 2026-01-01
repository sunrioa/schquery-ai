<template>
  <div class="admin-page ai-chat-config">
    <div class="page-header">
      <div class="header-left">
        <h2>对话预设（角色）</h2>
        <p class="sub">为不同模型/同模型维护多套参数预设，聊天中直接切换即可生效</p>
      </div>

      <div class="header-actions">
        <el-button type="primary" @click="createPreset">新建预设</el-button>
        <el-button :loading="presetsLoading" @click="loadPresets">刷新预设</el-button>
        <el-button :loading="globalLoading" @click="loadGlobal">刷新全局</el-button>
        <el-button type="success" :loading="refreshingMcp" @click="refreshMcp">刷新 MCP 客户端</el-button>
      </div>
    </div>

    <el-row :gutter="12" class="split-panels">
      <el-col :xs="24" :lg="9">
        <el-card class="table-card split-panel" shadow="never" v-loading="presetsLoading">
          <template #header>
            <div class="card-header">
              <div class="header-left">
                <span>预设列表</span>
                <el-tag v-if="defaultPresetId" type="success" size="small">默认ID：{{ defaultPresetId }}</el-tag>
              </div>
              <div class="header-actions">
                <el-input
                  v-model="presetQuery.presetName"
                  placeholder="搜索预设名"
                  clearable
                  style="width: 180px"
                  @keyup.enter="loadPresets"
                  @clear="loadPresets"
                />
              </div>
            </div>
          </template>

          <el-table
            :data="presets"
            stripe
            highlight-current-row
            style="width: 100%"
            :row-class-name="rowClassName"
            @row-click="selectPreset"
          >
            <el-table-column prop="presetName" label="预设" min-width="140" show-overflow-tooltip />
            <el-table-column prop="model" label="模型" min-width="140" show-overflow-tooltip />
            <el-table-column prop="status" label="启用" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="Number(row.status) === 1 ? 'success' : 'info'" size="small">
                  {{ Number(row.status) === 1 ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="默认" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="Number(row.id) === Number(defaultPresetId)" type="success" size="small">默认</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="presets.length === 0" class="empty-tip">
            还没有预设。点击右上角“新建预设”创建一套角色参数。
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="15">
        <el-card class="card split-panel" shadow="never" v-loading="presetSaving">
          <template #header>
            <div class="card-header">
              <div class="header-left">
                <span>预设参数</span>
                <el-tag v-if="currentPreset?.id" type="info" size="small">ID：{{ currentPreset.id }}</el-tag>
                <el-tag v-if="Number(currentPreset?.id) === Number(defaultPresetId)" type="success" size="small">
                  默认预设
                </el-tag>
              </div>
              <div class="header-actions">
                <el-button
                  v-if="currentPreset?.id && Number(currentPreset.id) !== Number(defaultPresetId)"
                  type="success"
                  plain
                  @click="setAsDefault"
                >
                  设为默认
                </el-button>
                <el-button type="primary" :disabled="!currentPreset" @click="savePreset">保存</el-button>
                <el-button v-if="currentPreset?.id" type="danger" plain @click="deletePreset">删除</el-button>
              </div>
            </div>
          </template>

          <div v-if="!currentPreset" class="empty-state">
            <el-empty description="请选择左侧预设，或新建一套预设" :image-size="120" />
          </div>

          <el-form v-else :model="currentPreset" label-width="120px">
            <el-form-item label="预设名称" required>
              <el-input v-model="currentPreset.presetName" placeholder="例如：客服助手 / 研发助手 / 翻译专家" />
              <div class="help-text">预设名称会显示在聊天页，用于一键切换“角色”。</div>
            </el-form-item>

            <el-form-item label="模型" required>
              <el-select
                v-model="currentPreset.model"
                filterable
                allow-create
                default-first-option
                placeholder="例如：qwen-plus"
                style="width: 100%"
              >
                <el-option v-for="m in chatModelOptions" :key="m" :label="m" :value="m" />
              </el-select>
              <div class="help-text">同一个模型也可以配置多套不同参数（例如不同 system prompt）。</div>
            </el-form-item>

            <el-form-item label="启用">
              <el-switch v-model="presetEnabledSwitch" />
            </el-form-item>

            <el-form-item label="系统提示词">
              <el-input
                v-model="currentPreset.systemMessage"
                type="textarea"
                :rows="4"
                placeholder="可选：角色设定/行为约束（例如：你是一名客服…）"
              />
            </el-form-item>

            <el-divider content-position="left">生成参数</el-divider>

            <el-row :gutter="12">
              <el-col :xs="24" :md="12">
                <el-form-item label="maxTokens">
                  <el-input-number v-model="currentPreset.maxTokens" :min="1" :max="32000" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="12">
                <el-form-item label="temperature">
                  <el-input-number v-model="currentPreset.temperature" :min="0" :max="2" :step="0.1" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="12">
                <el-form-item label="topP">
                  <el-input-number v-model="currentPreset.topP" :min="0" :max="1" :step="0.05" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="12">
                <el-form-item label="presencePenalty">
                  <el-input-number v-model="currentPreset.presencePenalty" :min="-2" :max="2" :step="0.1" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="12">
                <el-form-item label="frequencyPenalty">
                  <el-input-number v-model="currentPreset.frequencyPenalty" :min="-2" :max="2" :step="0.1" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="备注">
              <el-input v-model="currentPreset.remark" type="textarea" :rows="2" placeholder="可选" />
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="card" shadow="never" v-loading="globalSaving || globalLoading">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span>全局检索 / MCP 配置</span>
            <el-tag type="info" size="small">对齐 rin-admin</el-tag>
          </div>
          <div class="header-actions">
            <el-button type="primary" :loading="globalSaving" @click="saveGlobal">保存全局</el-button>
          </div>
        </div>
      </template>

      <el-form :model="globalConfig" label-width="120px">
        <el-form-item label="默认知识库">
          <el-select
            v-model="globalConfig.kid"
            filterable
            placeholder="请选择知识库"
            style="width: 100%"
            @change="onKidChange"
          >
            <el-option
              v-for="k in knowledgeOptions"
              :key="k.id"
              :label="`${k.kname}（${k.id}）`"
              :value="String(k.id)"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="mcpServers">
          <el-input
            v-model="globalConfig.mcpServers"
            type="textarea"
            :rows="3"
            placeholder="多个用逗号分隔，例如：http://localhost:3000,http://localhost:3001"
          />
        </el-form-item>

        <el-form-item label="mcpMode">
          <el-select v-model="globalConfig.mcpMode" placeholder="选择策略" style="width: 100%">
            <el-option label="off" value="off" />
            <el-option label="fallback" value="fallback" />
            <el-option label="merge" value="merge" />
          </el-select>
          <div class="help-text">该配置为全局联网策略；角色/预设主要控制模型与生成参数。</div>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getChatDefaultConfig, saveChatDefaultConfig } from '../../../api/ai/chatConfig'
import { getChatModelList } from '../../../api/ai/chatModel'
import { getDefaultChatPresetId, getChatPresetList, removeChatPreset, saveChatPreset, setDefaultChatPresetId } from '../../../api/ai/chatPreset'
import { getKnowledgeList } from '../../../api/ai/knowledge'
import { refreshMcpClients } from '../../../api/ai/mcp'

const presetsLoading = ref(false)
const presetSaving = ref(false)
const globalLoading = ref(false)
const globalSaving = ref(false)
const refreshingMcp = ref(false)

const presetQuery = ref({
  presetName: ''
})

const presets = ref([])
const defaultPresetId = ref(null)
const currentPreset = ref(null)

const presetEnabledSwitch = computed({
  get: () => Number(currentPreset.value?.status) === 1,
  set: (val) => {
    if (!currentPreset.value) return
    currentPreset.value.status = val ? 1 : 0
  }
})

const globalConfig = ref({
  model: '',
  kid: '',
  kName: '',
  talkCount: null,
  max_tokens: null,
  systemMessage: '',
  temperature: null,
  top_p: null,
  presence_penalty: null,
  frequency_penalty: null,
  repetition_penalty: null,
  mcpServers: '',
  mcpMode: 'fallback'
})

const knowledgeOptions = ref([])
const chatModelOptions = ref([])

const loadKnowledgeOptions = async () => {
  const res = await getKnowledgeList({ pageNum: 1, pageSize: 200, status: 1 })
  knowledgeOptions.value = res?.data?.records || []
}

const loadChatModelOptions = async () => {
  const res = await getChatModelList({ pageNum: 1, pageSize: 200, category: 'chat', modelShow: 1 })
  const records = res?.data?.records || []
  chatModelOptions.value = records.map((r) => r.modelName).filter(Boolean)
}

const onKidChange = (kid) => {
  const found = knowledgeOptions.value.find((k) => String(k.id) === String(kid))
  globalConfig.value.kName = found?.kname || ''
}

const loadPresets = async () => {
  presetsLoading.value = true
  try {
    const res = await getChatPresetList({
      pageNum: 1,
      pageSize: 200,
      presetName: presetQuery.value.presetName?.trim() || undefined
    })
    const records = res?.data?.records || []
    presets.value = records

    // 维持选中项
    if (currentPreset.value?.id) {
      const found = records.find((p) => Number(p.id) === Number(currentPreset.value.id))
      if (found) {
        currentPreset.value = { ...currentPreset.value, ...found }
      }
    }

    // 默认显示第一条
    if (!currentPreset.value && records.length > 0) {
      currentPreset.value = { ...records[0] }
    }
  } finally {
    presetsLoading.value = false
  }
}

const loadDefaultPreset = async () => {
  try {
    const res = await getDefaultChatPresetId()
    defaultPresetId.value = res?.data ?? null
  } catch (e) {
    defaultPresetId.value = null
  }
}

const selectPreset = (row) => {
  if (!row) return
  currentPreset.value = { ...row }
}

const rowClassName = ({ row }) => {
  if (currentPreset.value?.id && Number(row?.id) === Number(currentPreset.value.id)) {
    return 'is-current'
  }
  return ''
}

const createPreset = () => {
  currentPreset.value = {
    id: null,
    presetName: '',
    model: '',
    talkCount: 10,
    maxTokens: 1024,
    systemMessage: '',
    temperature: 0.5,
    topP: 1,
    presencePenalty: 0,
    frequencyPenalty: 0,
    repetitionPenalty: 1,
    remark: '',
    status: 1
  }
}

const savePreset = async () => {
  if (!currentPreset.value) return
  if (!currentPreset.value.presetName || !currentPreset.value.presetName.trim()) {
    ElMessage.error('预设名称不能为空')
    return
  }
  if (!currentPreset.value.model || !currentPreset.value.model.trim()) {
    ElMessage.error('模型不能为空')
    return
  }

  const isCreate = !currentPreset.value.id
  const targetName = currentPreset.value.presetName.trim()
  const targetModel = currentPreset.value.model.trim()

  presetSaving.value = true
  try {
    await saveChatPreset({
      ...currentPreset.value,
      presetName: targetName,
      model: targetModel
    })
    ElMessage.success('保存成功')
    await Promise.all([loadDefaultPreset(), loadPresets()])

    if (isCreate) {
      const matches = presets.value.filter((p) => p.presetName === targetName && p.model === targetModel)
      if (matches.length > 0) {
        matches.sort((a, b) => Number(b.id || 0) - Number(a.id || 0))
        currentPreset.value = { ...matches[0] }
      }
    }
  } finally {
    presetSaving.value = false
  }
}

const deletePreset = async () => {
  if (!currentPreset.value?.id) return
  await ElMessageBox.confirm(`确认删除预设「${currentPreset.value.presetName}」？`, '提示', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })

  presetSaving.value = true
  try {
    await removeChatPreset(currentPreset.value.id)
    ElMessage.success('删除成功')
    currentPreset.value = null
    await Promise.all([loadDefaultPreset(), loadPresets()])
  } finally {
    presetSaving.value = false
  }
}

const setAsDefault = async () => {
  if (!currentPreset.value?.id) return
  presetSaving.value = true
  try {
    await setDefaultChatPresetId(currentPreset.value.id)
    ElMessage.success('已设置为默认预设')
    await loadDefaultPreset()
  } finally {
    presetSaving.value = false
  }
}

const loadGlobal = async () => {
  globalLoading.value = true
  try {
    await loadKnowledgeOptions()
    const res = await getChatDefaultConfig()
    globalConfig.value = { ...globalConfig.value, ...(res?.data || {}) }
    if (globalConfig.value.kid) {
      onKidChange(globalConfig.value.kid)
    }
  } finally {
    globalLoading.value = false
  }
}

const saveGlobal = async () => {
  globalSaving.value = true
  try {
    await saveChatDefaultConfig(globalConfig.value)
    ElMessage.success('全局配置已保存')
  } finally {
    globalSaving.value = false
  }
}

const refreshMcp = async () => {
  refreshingMcp.value = true
  try {
    await refreshMcpClients()
    ElMessage.success('MCP 客户端已刷新')
  } finally {
    refreshingMcp.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadChatModelOptions(), loadKnowledgeOptions()])
  await Promise.all([loadDefaultPreset(), loadPresets(), loadGlobal()])
})
</script>

<style scoped>
.empty-tip {
  padding: 12px 16px 16px;
  color: var(--admin-muted, #6b7280);
  font-size: 13px;
}

.empty-state {
  padding: 12px 0;
}

.is-current :deep(td) {
  background: rgba(59, 130, 246, 0.06) !important;
}
</style>
