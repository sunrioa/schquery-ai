<template>
  <div class="admin-page ai-chat-config-container">
    <div class="page-header">
      <div class="header-info">
        <h2 class="title">AI 对话配置中心</h2>
        <p class="subtitle">统一管理角色预设与检索策略</p>
      </div>
      <div class="header-ops">
        <el-button type="primary" @click="createPreset">
          <el-icon><Plus /></el-icon>新建角色预设
        </el-button>
        <el-button type="success" plain :loading="refreshingMcp" @click="refreshMcp">
          <el-icon><Connection /></el-icon>刷新 MCP
        </el-button>
      </div>
    </div>

    <div class="tab-content preset-manager">
      <div class="sidebar">
        <div class="sidebar-header">
          <el-input
            v-model="presetQuery.presetName"
            placeholder="搜索预设..."
            clearable
            @input="loadPresets"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button :icon="Refresh" circle @click="loadPresets" :loading="presetsLoading" />
        </div>
        <div class="preset-list" v-loading="presetsLoading">
          <div
            v-for="item in presets"
            :key="item.id"
            class="preset-item"
            :class="{ active: currentPreset?.id === item.id }"
            @click="selectPreset(item)"
          >
            <div class="item-main">
              <span class="name">{{ item.presetName }}</span>
              <el-tag v-if="Number(item.id) === Number(defaultPresetId)" size="small" type="success" effect="dark">默认</el-tag>
            </div>
            <div class="item-sub">{{ item.model }}</div>
            <div class="item-status">
              <el-tag :type="Number(item.status) === 1 ? 'success' : 'info'" size="small" hit>
                {{ Number(item.status) === 1 ? '已启用' : '未启用' }}
              </el-tag>
            </div>
          </div>
          <el-empty v-if="!presets.length" description="暂无预设" :image-size="60" />
        </div>
      </div>

      <div class="main-form" v-loading="presetSaving">
        <div v-if="!currentPreset" class="empty-placeholder">
          <el-empty description="请选择或新建一个角色预设进行配置" />
        </div>
        <div v-else class="config-form-wrapper">
          <div class="form-header">
            <div class="form-title">
              <span>{{ currentPreset.id ? '编辑角色' : '新建角色' }}</span>
              <el-tag v-if="currentPreset.id" type="info" size="small">ID: {{ currentPreset.id }}</el-tag>
            </div>
            <div class="form-actions">
              <el-button
                v-if="currentPreset.id && Number(currentPreset.id) !== Number(defaultPresetId)"
                type="warning"
                link
                @click="setAsDefault"
              >
                <el-icon><Star /></el-icon>设为默认
              </el-button>
              <el-divider direction="vertical" v-if="currentPreset.id" />
              <el-button type="primary" @click="savePreset">保存配置</el-button>
              <el-button v-if="currentPreset.id" type="danger" plain @click="deletePreset">删除</el-button>
            </div>
          </div>

          <el-form :model="currentPreset" label-position="top" class="custom-form">
                <el-row :gutter="24">
                  <el-col :span="14">
                    <el-form-item required>
                      <template #label>
                        <div class="form-label-container">
                          <span>预设名称</span>
                          <el-tooltip content="给这个角色起个名字，方便识别" placement="top">
                            <el-icon class="help-icon"><QuestionFilled /></el-icon>
                          </el-tooltip>
                        </div>
                      </template>
                      <el-input v-model="currentPreset.presetName" placeholder="如：编程专家、文案助手..." />
                    </el-form-item>
                  </el-col>
                  <el-col :span="10">
                    <el-form-item required>
                      <template #label>
                        <div class="form-label-container">
                          <span>绑定模型</span>
                          <el-tooltip content="该角色默认使用的AI模型" placement="top">
                            <el-icon class="help-icon"><QuestionFilled /></el-icon>
                          </el-tooltip>
                        </div>
                      </template>
                      <el-select v-model="currentPreset.model" filterable allow-create style="width: 100%">
                        <el-option v-for="m in chatModelOptions" :key="m" :label="m" :value="m" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-form-item>
                  <template #label>
                    <div class="form-label-container">
                      <span>系统提示词 (System Prompt)</span>
                      <el-tooltip content="设置模型的默认系统角色，定义其行为模式和回答风格" placement="top">
                        <el-icon class="help-icon"><QuestionFilled /></el-icon>
                      </el-tooltip>
                    </div>
                  </template>
                  <el-input
                    v-model="currentPreset.systemMessage"
                    type="textarea"
                    :rows="5"
                    placeholder="在此输入角色的系统设定，引导模型的回答风格和范围..."
                  />
                </el-form-item>

                <div class="form-section-title">生成参数控制</div>
                <div class="params-grid">
                  <div class="param-card">
                    <div class="label-row">
                      <span class="label">Temperature (随机性)</span>
                      <el-tooltip content="值越大回答越随机，值越小越严谨" placement="top">
                        <el-icon class="help-icon"><QuestionFilled /></el-icon>
                      </el-tooltip>
                    </div>
                    <el-slider v-model="currentPreset.temperature" :min="0" :max="2" :step="0.1" show-input />
                  </div>
                  <div class="param-card">
                    <div class="label-row">
                      <span class="label">Top P (核采样)</span>
                      <el-tooltip content="控制模型选择下一个token的概率范围" placement="top">
                        <el-icon class="help-icon"><QuestionFilled /></el-icon>
                      </el-tooltip>
                    </div>
                    <el-slider v-model="currentPreset.topP" :min="0" :max="1" :step="0.05" show-input />
                  </div>
                  <div class="param-card">
                    <div class="label-row">
                      <span class="label">Max Tokens (最大长度)</span>
                      <el-tooltip content="单次回复生成的最大长度限制" placement="top">
                        <el-icon class="help-icon"><QuestionFilled /></el-icon>
                      </el-tooltip>
                    </div>
                    <el-input-number
                      v-model="currentPreset.maxTokens"
                      :min="1"
                      :max="64000"
                      style="width: 100%"
                    />
                  </div>
                </div>

                <el-row :gutter="24" style="margin-top: 20px;">
                  <el-col :span="8">
                    <el-form-item>
                      <template #label>
                        <div class="form-label-container">
                          <span>Presence Penalty</span>
                          <el-tooltip content="数值越大，越倾向于谈论新话题" placement="top">
                            <el-icon class="help-icon"><QuestionFilled /></el-icon>
                          </el-tooltip>
                        </div>
                      </template>
                      <el-input-number
                        v-model="currentPreset.presencePenalty"
                        :min="-2"
                        :max="2"
                        :step="0.1"
                        style="width: 100%"
                      />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item>
                      <template #label>
                        <div class="form-label-container">
                          <span>Frequency Penalty</span>
                          <el-tooltip content="数值越大，越减少重复词汇的出现" placement="top">
                            <el-icon class="help-icon"><QuestionFilled /></el-icon>
                          </el-tooltip>
                        </div>
                      </template>
                      <el-input-number
                        v-model="currentPreset.frequencyPenalty"
                        :min="-2"
                        :max="2"
                        :step="0.1"
                        style="width: 100%"
                      />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item>
                      <template #label>
                        <div class="form-label-container">
                          <span>启用状态</span>
                          <el-tooltip content="停用后，前端将无法选择该预设" placement="top">
                            <el-icon class="help-icon"><QuestionFilled /></el-icon>
                          </el-tooltip>
                        </div>
                      </template>
                      <el-switch v-model="presetEnabledSwitch" active-text="激活" inactive-text="停用" />
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-form-item style="margin-top: 10px;">
                  <template #label>
                    <div class="form-label-container">
                      <span>备注说明</span>
                      <el-tooltip content="仅管理员可见的备注信息" placement="top">
                        <el-icon class="help-icon"><QuestionFilled /></el-icon>
                      </el-tooltip>
                    </div>
                  </template>
                  <el-input v-model="currentPreset.remark" type="textarea" :rows="2" />
                </el-form-item>

                <div class="form-section-title">提示词融合策略</div>
                <el-row :gutter="24">
                  <el-col :span="12">
                    <el-form-item>
                      <template #label>
                        <div class="form-label-container">
                          <span>提示词融合模式</span>
                          <el-tooltip content="为空则使用默认值" placement="top">
                            <el-icon class="help-icon"><QuestionFilled /></el-icon>
                          </el-tooltip>
                        </div>
                      </template>
                      <el-select
                        v-model="currentPreset.promptMergeMode"
                        clearable
                        style="width: 100%"
                        :placeholder="`默认：${DEFAULT_PROMPT_MERGE_MODE}`"
                      >
                        <el-option label="用户优先 (user_first)" value="user_first" />
                        <el-option label="知识库优先 (knowledge_first)" value="knowledge_first" />
                        <el-option label="平衡 (balanced)" value="balanced" />
                        <el-option label="分层 (layered)" value="layered" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-row v-if="currentPreset.promptMergeMode === 'balanced'" :gutter="24">
                  <el-col :span="8">
                    <el-form-item>
                      <template #label>
                        <div class="form-label-container">
                          <span>用户权重</span>
                          <el-tooltip content="仅 balanced 生效，范围 0~1（留空=使用默认）" placement="top">
                            <el-icon class="help-icon"><QuestionFilled /></el-icon>
                          </el-tooltip>
                        </div>
                      </template>
                      <el-input-number
                        v-model="currentPreset.weightUser"
                        :min="0"
                        :max="1"
                        :step="0.01"
                        controls-position="right"
                        style="width: 100%"
                      />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item>
                      <template #label>
                        <div class="form-label-container">
                          <span>知识库权重</span>
                          <el-tooltip content="仅 balanced 生效，范围 0~1（留空=使用默认）" placement="top">
                            <el-icon class="help-icon"><QuestionFilled /></el-icon>
                          </el-tooltip>
                        </div>
                      </template>
                      <el-input-number
                        v-model="currentPreset.weightKnowledge"
                        :min="0"
                        :max="1"
                        :step="0.01"
                        controls-position="right"
                        style="width: 100%"
                      />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item>
                      <template #label>
                        <div class="form-label-container">
                          <span>MCP 权重</span>
                          <el-tooltip content="仅 balanced 生效，范围 0~1（留空=使用默认）" placement="top">
                            <el-icon class="help-icon"><QuestionFilled /></el-icon>
                          </el-tooltip>
                        </div>
                      </template>
                      <el-input-number
                        v-model="currentPreset.weightMcp"
                        :min="0"
                        :max="1"
                        :step="0.01"
                        controls-position="right"
                        style="width: 100%"
                      />
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-alert
                  v-if="currentPreset.promptMergeMode === 'balanced'"
                  :type="presetWeightSumValid ? 'success' : 'warning'"
                  show-icon
                  :closable="false"
                  :title="presetWeightAllNull
                    ? `权重：使用默认（用户 ${DEFAULT_WEIGHT_USER.toFixed(2)} / 知识库 ${DEFAULT_WEIGHT_KNOWLEDGE.toFixed(2)} / MCP ${DEFAULT_WEIGHT_MCP.toFixed(2)}）`
                    : `权重总和：${presetWeightSumText}（需=1.00）`"
                  description="balanced 模式会按权重比例分配各部分提示词长度；可全部留空使用默认，也可自定义三项权重（总和需为 1.00）。"
                  style="margin: 6px 0 12px;"
                />

                <el-button
                  v-if="currentPreset.promptMergeMode === 'balanced' && !presetWeightAllNull"
                  type="primary"
                  link
                  style="padding: 0; margin-top: -4px; margin-bottom: 8px;"
                  @click="resetPresetMergeWeights"
                >
                  清空权重（使用默认）
                </el-button>

                <div class="form-section-title">RAG 与 MCP 策略</div>
                <el-row :gutter="24">
                  <el-col :span="12">
                    <el-form-item>
                      <template #label>
                        <div class="form-label-container">
                          <span>绑定知识库</span>
                          <el-tooltip content="关联知识库后，AI将根据知识库内容进行回答" placement="top">
                            <el-icon class="help-icon"><QuestionFilled /></el-icon>
                          </el-tooltip>
                        </div>
                      </template>
                      <el-select
                        v-model="currentPreset.kid"
                        filterable
                        clearable
                        style="width: 100%"
                        placeholder="选择知识库（可选）"
                      >
                        <el-option
                          v-for="k in knowledgeOptions"
                          :key="k.id"
                          :label="k.kname"
                          :value="String(k.id)"
                        />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item>
                      <template #label>
                        <div class="form-label-container">
                          <span>MCP 运行模式</span>
                          <el-tooltip content="控制Model Context Protocol工具的调用方式" placement="top">
                            <el-icon class="help-icon"><QuestionFilled /></el-icon>
                          </el-tooltip>
                        </div>
                      </template>
                      <el-select v-model="currentPreset.mcpMode" style="width: 100%">
                        <el-option label="关闭 (Off)" value="off" />
                        <el-option label="降级 (Fallback)" value="fallback" />
                        <el-option label="合并 (Merge)" value="merge" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-form-item>
                  <template #label>
                    <div class="form-label-container">
                      <span>MCP 服务器</span>
                      <el-tooltip content="指定该预设可使用的外部MCP工具服务地址" placement="top">
                        <el-icon class="help-icon"><QuestionFilled /></el-icon>
                      </el-tooltip>
                    </div>
                  </template>
                  <el-input
                    v-model="currentPreset.mcpServers"
                    type="textarea"
                    :rows="3"
                    placeholder="请输入服务器地址，多个地址请用逗号分隔..."
                  />
                </el-form-item>
          </el-form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Refresh,
  Connection,
  Search,
  Star,
  QuestionFilled
} from '@element-plus/icons-vue'
import { getChatModelList } from '../../../api/ai/chatModel'
import { getDefaultChatPresetId, getChatPresetList, removeChatPreset, saveChatPreset, setDefaultChatPresetId } from '../../../api/ai/chatPreset'
import { getKnowledgeList } from '../../../api/ai/knowledge'
import { refreshMcpClients } from '../../../api/ai/mcp'

const presetsLoading = ref(false)
const presetSaving = ref(false)
const refreshingMcp = ref(false)

const DEFAULT_PROMPT_MERGE_MODE = 'user_first'
const DEFAULT_WEIGHT_USER = 0.6
const DEFAULT_WEIGHT_KNOWLEDGE = 0.25
const DEFAULT_WEIGHT_MCP = 0.15

const presetQuery = ref({
  presetName: ''
})

const presets = ref([])
const defaultPresetId = ref(null)
const currentPreset = ref(null)

const presetWeightAllNull = computed(() => {
  const u = currentPreset.value?.weightUser
  const k = currentPreset.value?.weightKnowledge
  const m = currentPreset.value?.weightMcp
  return u == null && k == null && m == null
})

const presetWeightSum = computed(() => {
  const uRaw = currentPreset.value?.weightUser
  const kRaw = currentPreset.value?.weightKnowledge
  const mRaw = currentPreset.value?.weightMcp
  if (uRaw == null || kRaw == null || mRaw == null) return NaN
  const u = Number(uRaw)
  const k = Number(kRaw)
  const m = Number(mRaw)
  if (!Number.isFinite(u) || !Number.isFinite(k) || !Number.isFinite(m)) return NaN
  return u + k + m
})

const presetWeightSumText = computed(() => {
  const sum = presetWeightSum.value
  return Number.isFinite(sum) ? sum.toFixed(2) : '-'
})

const presetWeightSumValid = computed(() => {
  if (currentPreset.value?.promptMergeMode !== 'balanced') return true
  if (presetWeightAllNull.value) return true
  const u = currentPreset.value?.weightUser
  const k = currentPreset.value?.weightKnowledge
  const m = currentPreset.value?.weightMcp
  if (u == null || k == null || m == null) return false
  const sum = presetWeightSum.value
  if (!Number.isFinite(sum)) return false
  return Math.abs(sum - 1) <= 0.01
})

const resetPresetMergeWeights = () => {
  if (!currentPreset.value) return
  currentPreset.value.weightUser = null
  currentPreset.value.weightKnowledge = null
  currentPreset.value.weightMcp = null
}

const presetEnabledSwitch = computed({
  get: () => Number(currentPreset.value?.status) === 1,
  set: (val) => {
    if (!currentPreset.value) return
    currentPreset.value.status = val ? 1 : 0
  }
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
    status: 1,
    kid: '',
    mcpMode: 'fallback',
    mcpServers: '',
    promptMergeMode: null,
    weightUser: null,
    weightKnowledge: null,
    weightMcp: null
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
  if (currentPreset.value.promptMergeMode === 'balanced' && !presetWeightSumValid.value) {
    const u = currentPreset.value.weightUser
    const k = currentPreset.value.weightKnowledge
    const m = currentPreset.value.weightMcp
    if (u == null || k == null || m == null) {
      ElMessage.error('balanced 模式下如需覆盖权重，请同时设置 用户/知识库/MCP 三项权重，或全部留空以跟随全局。')
      return
    }
    ElMessage.error(`balanced 模式下权重和需为 1.00（当前：${presetWeightSumText.value}）`)
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
  await Promise.all([loadDefaultPreset(), loadPresets()])
})
</script>

<style scoped>
.ai-chat-config-container {
  padding: 0;
  background: transparent;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 16px;
}

.page-header .title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--app-text);
}

.page-header .subtitle {
  margin: 4px 0 0;
  color: var(--app-muted);
  font-size: 14px;
}

.tab-content {
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: 8px;
  min-height: 600px;
}

.global-config-inner {
  padding: 20px;
}

.global-config-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

/* 预设管理布局 */
.preset-manager {
  display: flex;
  height: 700px;
}

.preset-manager .sidebar {
  width: 300px;
  border-right: 1px solid var(--app-border);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  display: flex;
  gap: 10px;
  border-bottom: 1px solid var(--app-border);
}

.preset-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}

.preset-item {
  padding: 12px 16px;
  margin-bottom: 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  border: 1px solid transparent;
}

.preset-item:hover {
  background: var(--app-surface-2);
}

.preset-item.active {
  background: var(--app-primary-soft);
  border-color: var(--app-primary);
}

.preset-item .item-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.preset-item .name {
  font-weight: 600;
  font-size: 14px;
  color: var(--app-text);
}

.preset-item .item-sub {
  font-size: 12px;
  color: var(--app-muted-2);
  margin-bottom: 6px;
}

/* 主表单区域 */
.main-form {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

.empty-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.form-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f2f6fc;
}

.form-title {
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 12px;
}

.form-section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 30px 0 16px;
  padding-left: 10px;
  border-left: 4px solid var(--app-primary);
}

.params-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.param-card {
  background: var(--app-surface-2);
  padding: 16px;
  border-radius: 8px;
  border: 1px solid var(--app-border);
}

.param-card .label-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
}

.param-card .label {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-muted);
}

.form-label-container {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.help-icon {
  color: var(--app-muted-2);
  cursor: help;
  font-size: 14px;
}

.help-icon:hover {
  color: var(--app-primary);
}

.help-text {
  font-size: 12px;
  color: var(--app-muted-2);
  margin-top: 5px;
  line-height: 1.4;
}

/* 响应式适配 */
@media (max-width: 1200px) {
  .preset-manager {
    flex-direction: column;
    height: auto;
  }
  .preset-manager .sidebar {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--app-border);
    height: 300px;
  }
}
</style>
