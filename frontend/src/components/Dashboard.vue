<template>
  <div class="dashboard-wrapper">

    <header class="dashboard-header glass-panel">
      <div class="header-left">
        <div class="logo-group">
          <span class="logo-icon animate-pulse">⚡</span>
          <h1 class="logo-text">AI Usage Monitor</h1>
        </div>
        <p class="header-desc">多服务 API 用量与本地 Claude Code 日志监控中心</p>
      </div>

      <div class="header-right">
        <div class="sync-status" v-if="lastGlobalSync">
          <span class="status-label">最近同步:</span>
          <span class="status-time">{{ lastGlobalSync }}</span>
        </div>

        <button
          class="sync-btn"
          :disabled="isSyncing"
          @click="syncAll"
        >
          <span v-if="isSyncing" class="spinner">⏳</span>
          <span v-else>🔄</span>
          {{ isSyncing ? '同步中...' : '同步用量' }}
        </button>

        <button class="settings-btn" @click="isSettingsOpen = true">
          ⚙️ 系统设置
        </button>
      </div>
    </header>


    <section class="services-section">
      <div class="services-grid">
        <ServiceCard
          v-for="svc in SERVICES"
          :key="svc.id"
          :service-id="svc.id"
          :display-name="svc.name"
          :summary="summaries[svc.id]"
          :auth="authStates[svc.id]"
          :is-selected="selectedService === svc.id"
          :is-busy="isSyncing"
          @select="selectService"
        />
      </div>
    </section>


    <section class="chart-section">
      <UsageChart
        :service-id="selectedService"
        :history-data="historyData"
        :has-key="authStates[selectedService]?.has_key || selectedService === 'claude_code' || selectedService === 'grok_build' || selectedService === 'codex'"
      />
    </section>


    <SettingsModal
      :is-open="isSettingsOpen"
      :auth-states="authStates"
      @close="isSettingsOpen = false"
      @refresh="loadAllData"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../utils/api'
import ServiceCard from './ServiceCard.vue'
import UsageChart from './UsageChart.vue'
import SettingsModal from './SettingsModal.vue'

const SERVICES = [
  { id: 'chatgpt_api', name: 'ChatGPT API' },
  { id: 'claude_api', name: 'Claude API' },
  { id: 'kimi_api', name: 'KIMI API' },
  { id: 'glm_api', name: 'GLM API' },
  { id: 'deepseek_api', name: 'DeepSeek API' },
  { id: 'claude_code', name: 'Claude Code' },
  { id: 'grok_build', name: 'Grok Build' },
  { id: 'codex', name: 'Codex' }
]


const selectedService = ref('chatgpt_api')
const isSyncing = ref(false)
const isSettingsOpen = ref(false)
const lastGlobalSync = ref('')


const summaries = ref({})
const authStates = ref({})
const historyData = ref([])


SERVICES.forEach(s => {
  summaries.value[s.id] = { tokens_in: 0, tokens_out: 0, requests: 0, cost_usd: 0, last_sync: '', latest_extra: '' }
  authStates.value[s.id] = { is_auth: 0, has_key: false, error_msg: '', last_check: '' }
})

onMounted(() => {
  loadAllData()
})


const loadAllData = async () => {
  try {

    const sumData = await api.getSummary()
    if (sumData) {
      summaries.value = { ...summaries.value, ...sumData }


      let maxTime = ''
      SERVICES.forEach(s => {
        const t = sumData[s.id]?.last_sync
        if (t && t > maxTime) maxTime = t
      })
      if (maxTime) {
        const dt = new Date(maxTime)
        lastGlobalSync.value = `${dt.getHours().toString().padStart(2, '0')}:${dt.getMinutes().toString().padStart(2, '0')}`
      }
    }


    const authData = await api.getAuthStatus()
    if (authData) {
      authStates.value = { ...authStates.value, ...authData }
    }


    await loadHistory(selectedService.value)

  } catch (e) {
    console.error('加载系统数据失败', e)
  }
}


const selectService = async (serviceId) => {
  selectedService.value = serviceId
  await loadHistory(serviceId)
}


const loadHistory = async (serviceId) => {
  try {
    const history = await api.getHistory(serviceId, 30)
    historyData.value = history || []
  } catch (e) {
    console.error(`加载 ${serviceId} 历史数据失败`, e)
    historyData.value = []
  }
}


const syncAll = async () => {
  if (isSyncing.value) return
  isSyncing.value = true

  try {
    const res = await api.triggerSync()
    if (res.success) {
      await loadAllData()
    }
  } catch (e) {
    alert('手动同步接口失败: ' + (e.response?.data?.error || e.message))
  } finally {
    isSyncing.value = false
  }
}
</script>

<style scoped>
.dashboard-wrapper {
  max-width: 1200px;
  margin: 0 auto;
  padding: 30px 20px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}


.dashboard-header {
  padding: 24px 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-color: rgba(255, 255, 255, 0.08);
}

.logo-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  font-size: 24px;
}

.logo-text {
  font-size: 22px;
  font-family: var(--font-title);
  background: var(--color-primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  font-weight: 700;
}

.header-desc {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 6px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.sync-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.sync-status .status-label {
  color: var(--text-muted);
}

.sync-status .status-time {
  color: var(--text-secondary);
  font-weight: 600;
}

.sync-btn {
  background: var(--color-primary-gradient);
  color: #ffffff;
  padding: 10px 20px;
  border-radius: 10px;
  font-weight: 600;
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.25);
  display: flex;
  align-items: center;
  gap: 8px;
}

.sync-btn:hover {
  filter: brightness(1.1);
  box-shadow: 0 6px 20px rgba(99, 102, 241, 0.4);
}

.sync-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  box-shadow: none;
}

.settings-btn {
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  padding: 10px 18px;
  border-radius: 10px;
  font-weight: 500;
}

.settings-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
}


.spinner {
  display: inline-block;
  animation: spin 1.5s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}


.services-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}


@media (max-width: 768px) {
  .dashboard-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }
  .header-right {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
