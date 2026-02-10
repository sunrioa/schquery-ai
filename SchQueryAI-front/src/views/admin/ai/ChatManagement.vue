<template>
  <div class="admin-page chat-management-container">
    <div class="page-header">
      <div class="header-info">
        <h2 class="title">对话管理</h2>
        <p class="subtitle">配置对话欢迎消息与追问建议功能</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="config-tabs">
      <!-- 欢迎消息配置 -->
      <el-tab-pane label="欢迎消息" name="welcome">
        <div class="tab-content">
          <div class="config-section">
            <div class="section-header">
              <div class="section-title">
                <el-icon><ChatDotRound /></el-icon>
                <span>新会话欢迎消息</span>
              </div>
              <el-button type="primary" :loading="saving" @click="saveWelcomeMessage">
                <el-icon><Check /></el-icon>保存配置
              </el-button>
            </div>
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="配置说明"
              description="此消息将在用户创建新对话时自动发送，支持 Markdown 格式。留空则使用系统默认欢迎消息。"
              style="margin-bottom: 16px;"
            />
            <el-form label-position="top">
              <el-form-item label="欢迎消息内容">
                <el-input
                  v-model="welcomeMessage"
                  type="textarea"
                  :rows="12"
                  placeholder="请输入欢迎消息内容，支持 Markdown 格式..."
                />
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-tab-pane>

      <!-- 追问建议配置 -->
      <el-tab-pane label="追问建议" name="followup">
        <div class="tab-content">
          <div class="config-section">
            <div class="section-header">
              <div class="section-title">
                <el-icon><MagicStick /></el-icon>
                <span>追问建议功能</span>
              </div>
              <el-switch v-model="followupConfig.enabled" active-text="启用" inactive-text="禁用" />
            </div>
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="功能说明"
              description="基于当前对话上下文，智能预测用户可能关心的问题，生成快捷追问选项，减少用户输入成本，引导用户获取核心信息。启用后将在AI回复后显示3条建议问题。"
              style="margin-bottom: 20px;"
            />

            <el-form label-position="top">
              <el-row :gutter="24">
                <el-col :span="12">
                  <el-form-item>
                    <template #label>
                      <div class="form-label-container">
                        <span>上下文对话轮次</span>
                        <el-tooltip content="用于生成追问建议的历史对话轮数，一轮等于一次用户输入+AI回复" placement="top">
                          <el-icon class="help-icon"><QuestionFilled /></el-icon>
                        </el-tooltip>
                      </div>
                    </template>
                    <el-select v-model="followupConfig.chatTurn" style="width: 100%">
                      <el-option v-for="n in 3" :key="n" :label="`${n} 轮`" :value="n" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item>
                    <template #label>
                      <div class="form-label-container">
                        <span>使用角色</span>
                        <el-tooltip content="用于生成追问建议的AI角色预设" placement="top">
                          <el-icon class="help-icon"><QuestionFilled /></el-icon>
                        </el-tooltip>
                      </div>
                    </template>
                    <el-select v-model="followupConfig.roleId" filterable style="width: 100%" placeholder="请选择角色">
                      <el-option
                        v-for="preset in presets"
                        :key="preset.id"
                        :label="preset.presetName"
                        :value="String(preset.id)"
                      />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>

              <el-form-item>
                <div class="info-text">
                  <el-icon><InfoFilled /></el-icon>
                  <span>系统将使用所选角色的系统提示词，根据对话上下文生成 3 条追问建议，显示在AI回复下方。</span>
                </div>
              </el-form-item>
            </el-form>

            <div class="section-actions">
              <el-button type="primary" :loading="saving" @click="saveFollowupConfig">
                <el-icon><Check /></el-icon>保存配置
              </el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound,
  MagicStick,
  Check,
  QuestionFilled,
  InfoFilled
} from '@element-plus/icons-vue'
import { getChatPresetList } from '../../../api/ai/chatPreset'
import { getChatDefaultConfig, saveChatDefaultConfig } from '../../../api/ai/chatConfig'

const activeTab = ref('welcome')
const saving = ref(false)

// 欢迎消息
const welcomeMessage = ref('')

// 追问建议配置
const followupConfig = ref({
  enabled: false,
  chatTurn: 2,
  roleId: ''
})

// 预设列表（用于角色选择）
const presets = ref([])

// 加载欢迎消息配置
const loadWelcomeMessage = async () => {
  try {
    const res = await getChatDefaultConfig()
    welcomeMessage.value = res?.data?.welcomeMessage || ''
  } catch (e) {
    console.error('加载欢迎消息失败:', e)
  }
}

// 加载追问建议配置
const loadFollowupConfig = async () => {
  try {
    const res = await getChatDefaultConfig()
    const suggestConfig = res?.data?.suggestConfig || {}
    followupConfig.value = {
      enabled: suggestConfig.enabled === 'true' || suggestConfig.enabled === true,
      chatTurn: parseInt(suggestConfig.chatTurn) || 2,
      roleId: suggestConfig.roleId || ''
    }
  } catch (e) {
    console.error('加载追问建议配置失败:', e)
  }
}

// 加载预设列表
const loadPresets = async () => {
  try {
    const res = await getChatPresetList({
      pageNum: 1,
      pageSize: 200,
      status: 1
    })
    presets.value = res?.data?.records || []

    // 默认选择第一个启用的角色
    if (presets.value.length > 0 && !followupConfig.value.roleId) {
      followupConfig.value.roleId = String(presets.value[0].id)
    }
  } catch (e) {
    console.error('加载预设列表失败:', e)
  }
}

// 保存欢迎消息
const saveWelcomeMessage = async () => {
  saving.value = true
  try {
    await saveChatDefaultConfig({
      welcomeMessage: welcomeMessage.value
    })
    ElMessage.success('欢迎消息保存成功')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 保存追问建议配置
const saveFollowupConfig = async () => {
  saving.value = true
  try {
    await saveChatDefaultConfig({
      suggestConfig: {
        enabled: followupConfig.value.enabled,
        chatTurn: followupConfig.value.chatTurn,
        roleId: followupConfig.value.roleId
      }
    })
    ElMessage.success('追问建议配置保存成功')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadWelcomeMessage(), loadFollowupConfig(), loadPresets()])
})
</script>

<style scoped>
.chat-management-container {
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

.config-tabs {
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: 8px;
  padding: 16px;
}

.config-tabs :deep(.el-tabs__content) {
  padding-top: 16px;
}

.tab-content {
  min-height: 400px;
}

.config-section {
  background: var(--app-surface-2);
  border-radius: 8px;
  padding: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--app-border);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: var(--app-text);
}

.form-label-container {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.help-icon {
  color: var(--app-muted-2);
  cursor: help;
  font-size: 14px;
}

.help-icon:hover {
  color: var(--app-primary);
}

.info-text {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--app-primary-soft);
  border-radius: 6px;
  color: var(--app-text);
  font-size: 14px;
}

.section-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--app-border);
}
</style>
