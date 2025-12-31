<template>
  <div class="admin-page ai-chat-config">
    <div class="page-header">
      <div class="header-left">
        <h2>默认对话参数</h2>
        <p class="sub">对齐 rin-admin：默认模型 / 默认知识库 / MCP 联网策略</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="success" :loading="refreshingMcp" @click="refreshMcp">刷新 MCP 客户端</el-button>
      </div>
    </div>

    <el-card class="card" shadow="never">
      <el-form :model="form" label-width="120px">
        <el-form-item label="默认模型">
          <el-select
            v-model="form.model"
            filterable
            allow-create
            default-first-option
            placeholder="例如：qwen-plus"
            style="width: 100%"
          >
            <el-option v-for="m in chatModelOptions" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>

        <el-form-item label="默认知识库">
          <el-select
            v-model="form.kid"
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

        <el-form-item label="系统提示词">
          <el-input v-model="form.systemMessage" type="textarea" :rows="4" placeholder="可选：覆盖默认系统提示词" />
        </el-form-item>

        <el-divider content-position="left">生成参数</el-divider>

        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-form-item label="max_tokens">
              <el-input-number
                v-model="form.max_tokens"
                :min="1"
                :max="32000"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="temperature">
              <el-input-number
                v-model="form.temperature"
                :min="0"
                :max="2"
                :step="0.1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>

          <el-col :xs="24" :md="12">
            <el-form-item label="top_p">
              <el-input-number
                v-model="form.top_p"
                :min="0"
                :max="1"
                :step="0.05"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="presence_penalty">
              <el-input-number
                v-model="form.presence_penalty"
                :min="-2"
                :max="2"
                :step="0.1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>

          <el-col :xs="24" :md="12">
            <el-form-item label="frequency_penalty">
              <el-input-number
                v-model="form.frequency_penalty"
                :min="-2"
                :max="2"
                :step="0.1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">MCP 配置</el-divider>

        <el-form-item label="mcpServers">
          <el-input
            v-model="form.mcpServers"
            type="textarea"
            :rows="3"
            placeholder="多个用逗号分隔，例如：http://localhost:3000,http://localhost:3001"
          />
        </el-form-item>

        <el-form-item label="mcpMode">
          <el-select v-model="form.mcpMode" placeholder="选择策略" style="width: 100%">
            <el-option label="off" value="off" />
            <el-option label="fallback" value="fallback" />
            <el-option label="merge" value="merge" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getChatDefaultConfig, saveChatDefaultConfig } from '../../../api/ai/chatConfig'
import { getChatModelList } from '../../../api/ai/chatModel'
import { getKnowledgeList } from '../../../api/ai/knowledge'
import { refreshMcpClients } from '../../../api/ai/mcp'

const loading = ref(false)
const saving = ref(false)
const refreshingMcp = ref(false)

const form = ref({
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
  form.value.kName = found?.kname || ''
}

const load = async () => {
  loading.value = true
  try {
    await Promise.all([loadKnowledgeOptions(), loadChatModelOptions()])
    const res = await getChatDefaultConfig()
    form.value = { ...form.value, ...(res?.data || {}) }
    if (form.value.kid) {
      onKidChange(form.value.kid)
    }
  } finally {
    loading.value = false
  }
}

const save = async () => {
  saving.value = true
  try {
    await saveChatDefaultConfig(form.value)
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
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

onMounted(() => load())
</script>
