<template>
  <div class="cron-select">
    <el-form-item label="执行频率">
      <el-radio-group v-model="type" @change="onTypeChange">
        <el-radio value="interval">间隔执行</el-radio>
        <el-radio value="daily">每天</el-radio>
        <el-radio value="weekly">每周</el-radio>
        <el-radio value="monthly">每月</el-radio>
        <el-radio value="custom">自定义</el-radio>
      </el-radio-group>
    </el-form-item>

    <!-- 间隔执行 -->
    <template v-if="type === 'interval'">
      <el-form-item label="执行间隔">
        <el-input-number v-model="intervalValue" :min="1" :max="9999" />
        <el-select v-model="intervalUnit" style="width: 100px; margin-left: 10px">
          <el-option label="分钟" value="minute" />
          <el-option label="小时" value="hour" />
          <el-option label="天" value="day" />
        </el-select>
      </el-form-item>
    </template>

    <!-- 每天执行 -->
    <template v-if="type === 'daily'">
      <el-form-item label="执行时间">
        <el-time-picker
          v-model="dailyTime"
          format="HH:mm"
          value-format="HH:mm"
          placeholder="选择时间"
        />
      </el-form-item>
    </template>

    <!-- 每周执行 -->
    <template v-if="type === 'weekly'">
      <el-form-item label="执行星期">
        <el-checkbox-group v-model="weeklyDays">
          <el-checkbox :label="1">周一</el-checkbox>
          <el-checkbox :label="2">周二</el-checkbox>
          <el-checkbox :label="3">周三</el-checkbox>
          <el-checkbox :label="4">周四</el-checkbox>
          <el-checkbox :label="5">周五</el-checkbox>
          <el-checkbox :label="6">周六</el-checkbox>
          <el-checkbox :label="0">周日</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="执行时间">
        <el-time-picker
          v-model="weeklyTime"
          format="HH:mm"
          value-format="HH:mm"
          placeholder="选择时间"
        />
      </el-form-item>
    </template>

    <!-- 每月执行 -->
    <template v-if="type === 'monthly'">
      <el-form-item label="执行日期">
        <el-select v-model="monthlyDay" placeholder="选择日期">
          <el-option v-for="day in 31" :key="day" :label="day + '日'" :value="day" />
        </el-select>
      </el-form-item>
      <el-form-item label="执行时间">
        <el-time-picker
          v-model="monthlyTime"
          format="HH:mm"
          value-format="HH:mm"
          placeholder="选择时间"
        />
      </el-form-item>
    </template>

    <!-- 自定义 -->
    <template v-if="type === 'custom'">
      <el-form-item label="Cron表达式">
        <el-input v-model="customCron" placeholder="* * * * * ?" />
      </el-form-item>
      <el-form-item label="格式说明">
        <div class="cron-help">
          <p>格式：分 时 日 月 周</p>
          <p>示例：</p>
          <ul>
            <li>0 0 2 * * - 每天凌晨2点</li>
            <li>0 0 ? * MON - 每周一凌晨2点</li>
            <li>0 0/2 * * * - 每2小时</li>
          </ul>
        </div>
      </el-form-item>
    </template>

    <!-- 生成的cron表达式 -->
    <el-form-item label="Cron表达式">
      <el-input v-model="cronExpression" readonly>
        <template #append>
          <el-button @click="copyCron" icon="CopyDocument">复制</el-button>
        </template>
      </el-input>
    </el-form-item>

    <!-- 预览下次执行时间 -->
    <el-form-item label="下次执行">
      <div class="next-execution">{{ nextExecution || '计算中...' }}</div>
    </el-form-item>
  </div>
</template>

<script setup>
import { defineProps, defineEmits, ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { generateCronApi } from '@/api/ai/crawler'

const props = defineProps({
  modelValue: {
    type: String,
    default: '0 0 2 ? * MON'
  }
})

const emit = defineEmits(['update:modelValue'])

// 执行类型
const type = ref('weekly')

// 间隔执行
const intervalValue = ref(1)
const intervalUnit = ref('day')

// 每天
const dailyTime = ref('')

// 每周
const weeklyDays = ref([1])
const weeklyTime = ref('')

// 每月
const monthlyDay = ref(1)
const monthlyTime = ref('')

// 自定义
const customCron = ref('')

// 生成的cron表达式
const cronExpression = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// 下次执行时间
const nextExecution = ref('')

// 类型变更
const onTypeChange = () => {
  if (type.value !== 'custom') {
    generateCron()
  }
}

// 生成cron表达式（前端生成，简单直接）
const generateCron = () => {
  let cron = ''

  switch (type.value) {
    case 'interval':
      cron = generateIntervalCron()
      break
    case 'daily':
      cron = generateDailyCron()
      break
    case 'weekly':
      cron = generateWeeklyCron()
      break
    case 'monthly':
      cron = generateMonthlyCron()
      break
    case 'custom':
      cron = customCron.value
      break
  }

  cronExpression.value = cron
  calculateNextExecution(cron)
}

// 生成间隔执行cron
const generateIntervalCron = () => {
  const value = intervalValue.value
  switch (intervalUnit.value) {
    case 'minute':
      return `*/${value} * * * ?`
    case 'hour':
      return `0 */${value} * * ?`
    case 'day':
      return `0 0 */${value} * ?`
    default:
      return '0 0 2 ? * MON'
  }
}

// 生成每天执行cron
const generateDailyCron = () => {
  if (!dailyTime.value) return '0 0 2 ? * *'
  const [hour, minute] = dailyTime.value.split(':')
  return `${minute} ${hour} * * ?`
}

// 生成每周执行cron
const generateWeeklyCron = () => {
  if (!weeklyTime.value || weeklyDays.value.length === 0) return '0 0 2 ? * MON'
  const [hour, minute] = weeklyTime.value.split(':')
  const days = weeklyDays.value.sort().join(',')
  return `${minute} ${hour} ? * ${days}`
}

// 生成每月执行cron
const generateMonthlyCron = () => {
  if (!monthlyTime.value || !monthlyDay.value) return '0 0 2 1 * ?'
  const [hour, minute] = monthlyTime.value.split(':')
  return `${minute} ${hour} ${monthlyDay.value} * ?`
}

// 计算下次执行时间（简化版）
const calculateNextExecution = (cron) => {
  nextExecution.value = '下次执行时间：根据cron计算（仅供参考）'
}

// 复制cron表达式
const copyCron = () => {
  navigator.clipboard.writeText(cronExpression.value)
  ElMessage.success('已复制到剪贴板')
}

// 监听配置变化，调用后端API生成cron
const callBackendGenerate = async () => {
  if (type.value === 'custom') {
    // 自定义模式，不调用后端
    cronExpression.value = customCron.value
    return
  }

  try {
    const params = { type: type.value }

    switch (type.value) {
      case 'interval':
        params.value = intervalValue.value
        params.unit = intervalUnit.value
        break
      case 'daily':
        params.time = dailyTime.value || '02:00'
        break
      case 'weekly':
        params.time = weeklyTime.value || '02:00'
        params.days = weeklyDays.value
        break
      case 'monthly':
        params.time = monthlyTime.value || '02:00'
        params.day = monthlyDay.value
        break
    }

    const res = await generateCronApi(params)
    if (res.code === 200) {
      cronExpression.value = res.data
    }
  } catch (e) {
    console.error('生成cron表达式失败', e)
  }
}

// 初始化
watch(() => [type, intervalValue, intervalUnit, dailyTime, weeklyDays, weeklyTime, monthlyDay, monthlyTime, customCron], () => {
  callBackendGenerate()
}, { deep: true })

// 从传入的cron表达式解析初始类型（可选）
watch(() => props.modelValue, (newVal) => {
  if (newVal && newVal !== cronExpression.value) {
    // 这里可以解析传入的cron，设置到对应的表单
    cronExpression.value = newVal
  }
}, { immediate: true })
</script>

<style scoped>
.cron-select {
  padding: 10px;
}

.cron-help {
  font-size: 12px;
  color: #666;
  line-height: 1.6;
}

.cron-help p {
  margin: 5px 0;
}

.cron-help ul {
  margin: 5px 0;
  padding-left: 20px;
}

.next-execution {
  color: #409eff;
  font-weight: bold;
}
</style>
