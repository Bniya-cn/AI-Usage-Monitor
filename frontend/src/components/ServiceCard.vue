<template>
  <div 
    class="service-card glass-panel" 
    :class="{ 'active': isSelected, 'busy': isBusy }"
    @click="$emit('select', serviceId)"
  >
    <div class="card-header">
      <div class="title-area">
        <div class="icon-indicator" :class="statusClass"></div>
        <h3 class="card-title">{{ displayName }}</h3>
      </div>
      <span class="badge" :class="badgeClass">{{ statusText }}</span>
    </div>

    <div class="card-divider"></div>

    <div class="card-body">
      <template v-if="serviceId === 'deepseek_api'">
        <div class="stat-row">
          <span class="stat-label">账户余额</span>
          <span class="stat-value highlight-value">
            {{ formatBalance() }}
          </span>
        </div>
        <div class="stat-row">
          <span class="stat-label">本月消费</span>
          <span class="stat-value" :class="{ 'highlight-value': summary.cost_usd > 0 }">
            {{ (summary.cost_usd || 0).toFixed(2) }} {{ deepseekInfo.currency || 'CNY' }}
          </span>
        </div>
        <div class="stat-row">
          <span class="stat-label">请求次数</span>
          <span class="stat-value">{{ summary.requests > 0 ? summary.requests + ' 次' : '--' }}</span>
        </div>
        <div class="stat-row">
          <span class="stat-label">Token 消耗</span>
          <span class="stat-value">
            {{ (summary.tokens_in + summary.tokens_out) > 0 ? formatTokens(summary.tokens_in + summary.tokens_out) : '--' }}
          </span>
        </div>
      </template><template v-else-if="serviceId === 'claude_code' || serviceId === 'grok_build' || serviceId === 'codex'">
        <div class="stat-grid">
          <div class="mini-stat">
            <span class="stat-label">累计会话</span>
            <span class="stat-value">{{ ccInfo.sessions || 0 }}</span>
          </div>
          <div class="mini-stat" v-if="serviceId === 'claude_code'">
            <span class="stat-label">关联项目</span>
            <span class="stat-value">{{ ccInfo.projects || 0 }}</span>
          </div>
        </div>
        <div class="stat-row" style="margin-top: 12px;">
          <span class="stat-label">总请求次数</span>
          <span class="stat-value">{{ summary.requests || 0 }}</span>
        </div>
        <div class="stat-row">
          <span class="stat-label">总 Token 数</span>
          <span class="stat-value">{{ formatTokens(summary.tokens_in + summary.tokens_out) }}</span>
        </div>
      </template><template v-else>
        <div class="stat-row">
          <span class="stat-label">预估费用</span>
          <span class="stat-value" :class="{ 'highlight-value': summary.cost_usd > 0 }">
            ${{ (summary.cost_usd || 0).toFixed(4) }} USD
          </span>
        </div>
        <div class="stat-row">
          <span class="stat-label">请求次数</span>
          <span class="stat-value">{{ summary.requests || 0 }} 次</span>
        </div>
        <div class="stat-row">
          <span class="stat-label">Token 消耗</span>
          <span class="stat-value">
            {{ formatTokens(summary.tokens_in + summary.tokens_out) }}
          </span>
        </div>
      </template>
    </div>


    <div class="card-footer">
      <span class="sync-time" v-if="summary.last_sync">
        同步时间: {{ formatTime(summary.last_sync) }}
      </span>
      <span class="sync-time" v-else>未同步过数据</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  serviceId: { type: String, required: true },
  displayName: { type: String, required: true },
  summary: { type: Object, default: () => ({ tokens_in: 0, tokens_out: 0, requests: 0, cost_usd: 0, last_sync: '', latest_extra: '' }) },
  auth: { type: Object, default: () => ({ is_auth: 0, has_key: false, error_msg: '', last_check: '' }) },
  isSelected: { type: Boolean, default: false },
  isBusy: { type: Boolean, default: false }
})

defineEmits(['select'])


const deepseekInfo = computed(() => {
  if (props.serviceId === 'deepseek_api' && props.summary.latest_extra) {
    try {
      return JSON.parse(props.summary.latest_extra)
    } catch (e) {
      return {}
    }
  }
  return {}
})


const ccInfo = computed(() => {
  if ((props.serviceId === 'claude_code' || props.serviceId === 'grok_build' || props.serviceId === 'codex') && props.summary.latest_extra) {
    try {
      return JSON.parse(props.summary.latest_extra)
    } catch (e) {
      return {}
    }
  }
  return {}
})


const statusClass = computed(() => {
  if (props.serviceId === 'claude_code' || props.serviceId === 'grok_build' || props.serviceId === 'codex') {
    return 'status-success'
  }
  if (!props.auth.has_key) {
    return 'status-muted'
  }
  return props.auth.is_auth === 1 ? 'status-success' : 'status-danger'
})


const badgeClass = computed(() => {
  if (props.serviceId === 'claude_code' || props.serviceId === 'grok_build' || props.serviceId === 'codex') return 'badge-success'
  if (!props.auth.has_key) return 'badge-muted'
  return props.auth.is_auth === 1 ? 'badge-success' : 'badge-danger'
})


const statusText = computed(() => {
  if (props.serviceId === 'claude_code' || props.serviceId === 'grok_build' || props.serviceId === 'codex') return '已就绪'
  if (!props.auth.has_key) return '未配置'
  return props.auth.is_auth === 1 ? '已授权' : '无效Key'
})


const formatTokens = (val) => {
  if (!val) return '0'
  if (val >= 1000000) return (val / 1000000).toFixed(2) + ' M'
  if (val >= 1000) return (val / 1000).toFixed(1) + ' k'
  return val.toString()
}


const formatBalance = () => {
  const info = deepseekInfo.value
  if (info.total_balance !== undefined) {
    return `${info.total_balance.toFixed(2)} ${info.currency || 'CNY'}`
  }
  return '0.00 CNY'
}


const formatTime = (timeStr) => {
  if (!timeStr) return '--:--'
  try {
    const dt = new Date(timeStr)
    const hh = String(dt.getHours()).padStart(2, '0')
    const mm = String(dt.getMinutes()).padStart(2, '0')
    const mo = String(dt.getMonth() + 1).padStart(2, '0')
    const da = String(dt.getDate()).padStart(2, '0')
    return `${mo}-${da} ${hh}:${mm}`
  } catch (e) {
    return timeStr
  }
}
</script>

<style scoped>
.service-card {
  padding: 20px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  min-height: 200px;
  height: auto;
}

.service-card.active {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-glow), var(--shadow-md);
  background: var(--bg-card-hover);
}

.service-card.busy {
  pointer-events: none;
  opacity: 0.7;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title-area {
  display: flex;
  align-items: center;
  gap: 10px;
}

.icon-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  position: relative;
}

.status-success {
  background-color: var(--color-success);
  box-shadow: 0 0 10px var(--color-success);
}

.status-danger {
  background-color: var(--color-danger);
  box-shadow: 0 0 10px var(--color-danger);
}

.status-muted {
  background-color: var(--text-muted);
}


.status-success, .status-danger {
  animation: pulse-dot 2.5s infinite ease-in-out;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.9); }
}

.card-title {
  font-size: 16px;
  color: var(--text-primary);
}

.card-divider {
  height: 1px;
  background: var(--border-color);
  margin: 14px 0;
}

.card-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.stat-label {
  color: var(--text-secondary);
}

.stat-value {
  color: var(--text-primary);
  font-weight: 500;
}

.highlight-value {
  background: var(--color-primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  font-size: 15px;
  font-weight: 700;
  font-family: var(--font-title);
}

.stat-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.mini-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mini-stat .stat-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  font-family: var(--font-title);
}

.card-footer {
  margin-top: auto;
  font-size: 11px;
  color: var(--text-muted);
}
</style>
