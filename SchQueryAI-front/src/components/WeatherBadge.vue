<template>
  <div class="weather-badge" :class="{ 'is-loading': loading }">
    <el-tooltip
      v-if="!loading && info"
      placement="bottom"
      :content="tooltipText"
      effect="dark"
    >
      <div class="weather-content" role="status" aria-live="polite">
        <el-icon class="weather-icon">
          <component :is="weatherIcon" />
        </el-icon>
        <span class="weather-city" :title="info.city">{{ info.city }}</span>
        <span class="weather-sep">·</span>
        <span class="weather-temp">{{ temperatureText }}</span>
        <span class="weather-sep">·</span>
        <span class="weather-desc">{{ weatherText }}</span>
      </div>
    </el-tooltip>

    <div v-else class="weather-content" role="status" aria-live="polite">
      <el-icon class="weather-icon">
        <Cloudy />
      </el-icon>
      <span class="weather-fallback">{{ loading ? '天气加载中' : '天气不可用' }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Cloudy, Lightning, MostlyCloudy, PartlyCloudy, Pouring, Sunny, Umbrella } from '@element-plus/icons-vue'

// Free APIs:
// - IP geolocation: https://ipwho.is/ (no key)
// - Weather: https://open-meteo.com/ (no key)
const CACHE_KEY = 'schqueryai:weather:v3'
const CACHE_TTL_MS = 30 * 60 * 1000

const BEIJING = {
  city: '北京',
  latitude: 39.9042,
  longitude: 116.4074,
  countryCode: 'CN'
}

const ENGLISH_NATIVE_COUNTRIES = new Set(['US', 'GB', 'CA', 'AU', 'NZ', 'IE'])

const loading = ref(true)
const info = ref(null)

const temperatureText = computed(() => {
  const t = info.value?.temperature
  if (t == null || !Number.isFinite(Number(t))) return '--°C'
  return `${Math.round(Number(t))}°C`
})

const temperatureRangeText = computed(() => {
  const tMin = info.value?.tempMin
  const tMax = info.value?.tempMax
  const min = Number(tMin)
  const max = Number(tMax)
  if (Number.isFinite(min) && Number.isFinite(max)) {
    const a = Math.round(Math.min(min, max))
    const b = Math.round(Math.max(min, max))
    if (a === b) return `${a}°C`
    return `${a}°C-${b}°C`
  }
  return temperatureText.value
})

const weatherMeta = computed(() => resolveWeatherMeta(info.value?.weatherCode))
const weatherText = computed(() => weatherMeta.value.text)
const weatherIcon = computed(() => weatherMeta.value.icon)

const tooltipText = computed(() => {
  if (!info.value) return ''
  const dateKey = getLocalDateKey()
  const seedBase = `${dateKey}|${info.value.city}|${info.value.countryCode || ''}|${info.value.weatherCode || ''}|${temperatureRangeText.value}`
  const seed = hashString(seedBase)

  const group = resolveWeatherGroup(info.value.weatherCode)
  const tempTag = resolveTemperatureTag(info.value.tempMin, info.value.tempMax, info.value.temperature)
  const activity = pickOne(resolveActivities(group, tempTag), seed, 7)
  const advice = pickOne(resolveAdvices(group, tempTag), seed, 13)
  const closing = pickOne(CLOSINGS, seed, 17)

  const templates = [
    () => `今日${info.value.city}天气${weatherText.value}，气温${temperatureRangeText.value}，宜${activity}，${closing}。`,
    () => `${info.value.city}今天${weatherText.value}，${temperatureRangeText.value}，${advice}，${closing}。`,
    () => `今天${info.value.city}${weatherText.value}，温度${temperatureRangeText.value}，${advice}。${closing}！`
  ]
  const tip = templates[seed % templates.length]?.() || ''

  const updatedText = formatClock(info.value.updatedAt)
  const suffixParts = []
  if (updatedText) suffixParts.push(`更新于 ${updatedText}`)
  if (info.value.isFallback) suffixParts.push('定位失败，已切换北京')
  const suffix = suffixParts.length ? `（${suffixParts.join('，')}）` : ''

  return `${tip}${suffix}`
})

const readCache = () => {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!parsed?.ts || !parsed?.data) return null
    if (Date.now() - Number(parsed.ts) > CACHE_TTL_MS) return null
    return parsed.data
  } catch (e) {
    return null
  }
}

const writeCache = (data) => {
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify({ ts: Date.now(), data }))
  } catch (e) {
    // ignore
  }
}

const fetchJson = async (url, timeoutMs = 8000) => {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const res = await fetch(url, {
      method: 'GET',
      signal: controller.signal,
      cache: 'no-store'
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } finally {
    clearTimeout(timer)
  }
}

const resolveLocation = async () => {
  const data = await fetchJson('https://ipwho.is/?lang=en', 6000)
  if (!data || data.success !== true) throw new Error('ipwho.is failed')
  const latitude = Number(data.latitude)
  const longitude = Number(data.longitude)
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) throw new Error('invalid coords')

  const countryCode = String(data.country_code || '').trim().toUpperCase()
  const city = String(data.city || data.region || data.country || '').trim()
  return { city: city || '当地', latitude, longitude, countryCode }
}

const resolveCityFromIp = async (language) => {
  const lang = String(language || 'en').trim() || 'en'
  const data = await fetchJson(`https://ipwho.is/?lang=${encodeURIComponent(lang)}`, 6000)
  if (!data || data.success !== true) throw new Error('ipwho.is failed')
  return String(data.city || data.region || data.country || '').trim()
}

const resolveDisplayLanguage = (countryCode) => {
  if (String(countryCode || '').toUpperCase() === 'CN') return 'zh'
  if (ENGLISH_NATIVE_COUNTRIES.has(String(countryCode || '').toUpperCase())) return 'en'
  return 'en'
}

const containsCjk = (text) => {
  // Basic CJK Unified Ideographs range; sufficient for "中文 vs 英文" judgement here.
  return /[\u4E00-\u9FFF]/.test(String(text || ''))
}

const reverseGeocode = async (latitude, longitude, language) => {
  const url = [
    'https://geocoding-api.open-meteo.com/v1/reverse',
    `?latitude=${encodeURIComponent(latitude)}`,
    `&longitude=${encodeURIComponent(longitude)}`,
    `&language=${encodeURIComponent(language || 'en')}`,
    '&format=json'
  ].join('')
  const data = await fetchJson(url, 6000)
  const first = data?.results?.[0]
  if (!first) throw new Error('missing reverse results')
  const name = String(first.name || '').trim()
  const admin1 = String(first.admin1 || '').trim()
  const country = String(first.country || '').trim()
  const countryCode = String(first.country_code || '').trim().toUpperCase()
  return { name, admin1, country, countryCode }
}

const resolveDisplayCity = async (latitude, longitude, language, fallbackCity) => {
  try {
    const geo = await reverseGeocode(latitude, longitude, language)
    const name = String(geo?.name || '').trim()
    return name || fallbackCity
  } catch (e) {
    return fallbackCity
  }
}

const fetchWeather = async (latitude, longitude) => {
  const url = [
    'https://api.open-meteo.com/v1/forecast',
    `?latitude=${encodeURIComponent(latitude)}`,
    `&longitude=${encodeURIComponent(longitude)}`,
    '&current_weather=true',
    '&daily=temperature_2m_max,temperature_2m_min',
    '&forecast_days=1',
    '&timezone=auto'
  ].join('')
  const data = await fetchJson(url, 8000)
  const cw = data?.current_weather
  if (!cw) throw new Error('missing current_weather')
  const daily = data?.daily
  const tempMax = Array.isArray(daily?.temperature_2m_max) ? daily.temperature_2m_max[0] : null
  const tempMin = Array.isArray(daily?.temperature_2m_min) ? daily.temperature_2m_min[0] : null
  return {
    temperature: cw.temperature,
    weatherCode: cw.weathercode,
    tempMax,
    tempMin,
    weatherTime: cw.time
  }
}

const resolveWeatherMeta = (codeRaw) => {
  const code = Number(codeRaw)
  if (!Number.isFinite(code)) return { text: '未知', icon: Cloudy }

  if (code === 0) return { text: '晴', icon: Sunny }
  if (code === 1) return { text: '晴间多云', icon: PartlyCloudy }
  if (code === 2) return { text: '多云', icon: MostlyCloudy }
  if (code === 3) return { text: '阴', icon: Cloudy }

  if (code === 45 || code === 48) return { text: '雾', icon: Cloudy }

  if (code >= 51 && code <= 57) return { text: '毛毛雨', icon: Umbrella }

  if (code >= 61 && code <= 67) return { text: '下雨', icon: Pouring }
  if (code >= 80 && code <= 82) return { text: '阵雨', icon: Pouring }

  if (code >= 71 && code <= 77) return { text: '下雪', icon: Cloudy }
  if (code >= 85 && code <= 86) return { text: '阵雪', icon: Cloudy }

  if (code >= 95 && code <= 99) return { text: '雷暴', icon: Lightning }

  return { text: '天气', icon: Cloudy }
}

const resolveWeatherGroup = (codeRaw) => {
  const code = Number(codeRaw)
  if (!Number.isFinite(code)) return 'unknown'
  if (code === 0) return 'sunny'
  if (code === 1 || code === 2) return 'cloudy'
  if (code === 3) return 'overcast'
  if (code === 45 || code === 48) return 'fog'
  if (code >= 51 && code <= 57) return 'drizzle'
  if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return 'rain'
  if ((code >= 71 && code <= 77) || (code >= 85 && code <= 86)) return 'snow'
  if (code >= 95 && code <= 99) return 'thunder'
  return 'unknown'
}

const resolveTemperatureTag = (tempMinRaw, tempMaxRaw, tempNowRaw) => {
  const min = Number(tempMinRaw)
  const max = Number(tempMaxRaw)
  const now = Number(tempNowRaw)
  const base = Number.isFinite(max) ? max : (Number.isFinite(now) ? now : NaN)
  if (!Number.isFinite(base)) return 'unknown'
  if (base <= 0) return 'very_cold'
  if (base <= 10) return 'cold'
  if (base <= 20) return 'mild'
  if (base <= 28) return 'warm'
  return 'hot'
}

const CLOSINGS = [
  '祝你有美好的一天',
  '愿你今天顺顺利利',
  '祝你工作顺利，心情愉快',
  '愿你今天元气满满',
  '祝你今天一路好心情'
]

const resolveActivities = (group, tempTag) => {
  const baseByWeather = {
    sunny: ['出游', '散步', '晒晒太阳', '安排一次轻松的行程', '户外运动'],
    cloudy: ['散步', '轻松出行', '安排通勤与行程', '喝杯咖啡放松一下', '随手拍点风景'],
    overcast: ['慢慢走走', '室内外结合的安排', '听听音乐放松一下', '整理一下计划', '轻松出行'],
    fog: ['慢行出门', '早点出发避免赶路', '室内安排', '注意能见度', '轻松通勤'],
    drizzle: ['带伞出门', '室内安排', '喝杯热饮', '带上雨具', '轻松通勤'],
    rain: ['带伞出门', '室内安排', '喝杯热饮', '避开积水路段', '轻松通勤'],
    snow: ['保暖出行', '来杯热饮', '欣赏雪景', '早点回家', '慢行出门'],
    thunder: ['室内安排', '减少户外停留', '早点回家', '在家放松一下', '整理一下待办'],
    unknown: ['安排通勤与行程', '轻松出行', '保持好心情']
  }

  const addByTemp = {
    very_cold: ['注意保暖', '穿上外套', '来杯热饮'],
    cold: ['加件外套', '注意保暖'],
    mild: ['轻装出行', '散步'],
    warm: ['户外运动', '出游'],
    hot: ['防晒补水', '避开午后高温'],
    unknown: []
  }

  const base = baseByWeather[group] || baseByWeather.unknown
  const add = addByTemp[tempTag] || []
  return [...base, ...add]
}

const resolveAdvices = (group, tempTag) => {
  const byWeather = {
    sunny: ['适合出门走走', '可以晒晒太阳', '注意补水保持舒适'],
    cloudy: ['注意早晚温差', '外出带件薄外套更舒适', '出行安排更从容'],
    overcast: ['适合放慢节奏', '外出注意保暖', '通勤路上注意安全'],
    fog: ['出行注意能见度', '驾车注意减速慢行', '早出晚归注意保暖'],
    drizzle: ['出门记得带伞', '路面湿滑注意安全', '适合室内外结合安排'],
    rain: ['出门记得带伞', '路面湿滑注意脚下', '更适合安排室内行程'],
    snow: ['注意保暖，小心路滑', '路面可能结冰，慢行更安全', '适合来杯热饮暖一暖'],
    thunder: ['尽量减少户外活动', '注意防雷与安全', '更适合室内安排'],
    unknown: ['出行注意安全', '保持好心情']
  }

  const byTemp = {
    very_cold: ['注意保暖', '记得戴好围巾手套'],
    cold: ['注意保暖', '早晚温差大注意添衣'],
    mild: ['体感舒适', '适合散步放松'],
    warm: ['体感舒适', '适合户外活动'],
    hot: ['注意防晒补水', '尽量避开正午暴晒'],
    unknown: []
  }

  const base = byWeather[group] || byWeather.unknown
  const add = byTemp[tempTag] || []
  return [...base, ...add]
}

const pickOne = (arr, seed, salt = 0) => {
  const list = Array.isArray(arr) ? arr.filter(Boolean) : []
  if (list.length === 0) return ''
  const idx = (Number(seed) + Number(salt)) % list.length
  return list[idx]
}

const hashString = (raw) => {
  const s = String(raw || '')
  let h = 0
  for (let i = 0; i < s.length; i += 1) {
    h = (h * 31 + s.charCodeAt(i)) >>> 0
  }
  return h
}

const getLocalDateKey = () => {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const formatClock = (raw) => {
  const ts = Number(raw)
  if (!Number.isFinite(ts) || ts <= 0) return ''
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

onMounted(async () => {
  const cached = readCache()
  if (cached) {
    info.value = cached
    loading.value = false
    return
  }

  loading.value = true
  try {
    let loc
    let isFallback = false
    try {
      loc = await resolveLocation()
    } catch (e) {
      loc = BEIJING
      isFallback = true
    }

    let weather
    try {
      weather = await fetchWeather(loc.latitude, loc.longitude)
    } catch (e) {
      // If weather fetch fails for IP location, fall back to Beijing once.
      loc = BEIJING
      isFallback = true
      weather = await fetchWeather(loc.latitude, loc.longitude)
    }

    const language = resolveDisplayLanguage(loc.countryCode)
    const displayCity = await resolveDisplayCity(loc.latitude, loc.longitude, language, loc.city || BEIJING.city)
    let cityFinal = displayCity || loc.city || BEIJING.city
    // Open-Meteo's place names are sometimes Latin even with `language=zh` (e.g. Lhasa).
    // For China, try getting the Chinese city name from ipwho.is as a fallback.
    if (language === 'zh' && !containsCjk(cityFinal)) {
      try {
        const ipCityZh = await resolveCityFromIp('zh')
        if (containsCjk(ipCityZh)) cityFinal = ipCityZh
      } catch (e) {
        // ignore
      }
    }
    info.value = {
      city: cityFinal,
      countryCode: loc.countryCode,
      language,
      temperature: weather.temperature,
      weatherCode: weather.weatherCode,
      tempMin: weather.tempMin,
      tempMax: weather.tempMax,
      weatherTime: weather.weatherTime,
      updatedAt: Date.now(),
      isFallback
    }
    writeCache(info.value)
  } catch (e) {
    info.value = null
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.weather-badge {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--app-border);
  background: var(--app-surface-2);
  border-radius: 999px;
  padding: 6px 10px;
  color: var(--app-text);
  user-select: none;
}

.weather-content {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  line-height: 1;
}

.weather-icon {
  font-size: 16px;
  color: var(--app-primary);
}

.weather-city {
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  font-size: 13px;
}

.weather-temp {
  font-weight: 600;
  font-size: 13px;
}

.weather-desc {
  color: var(--app-muted);
  font-size: 12px;
}

.weather-sep {
  color: var(--app-muted-2);
  font-size: 12px;
}

.weather-fallback {
  color: var(--app-muted);
  font-size: 12px;
}

.weather-badge.is-loading {
  opacity: 0.9;
}

@media (max-width: 768px) {
  .weather-city {
    max-width: 64px;
  }
  .weather-desc {
    display: none;
  }
}
</style>
