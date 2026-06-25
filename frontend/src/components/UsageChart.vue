<template>
  <div class="chart-container glass-panel">
    <div class="chart-header">
      <div class="header-left">
        <h3 class="chart-title">历史趋势分析</h3>
        <span class="chart-subtitle">最近 30 天用量与资费监测</span>
      </div>
      <div class="header-right">
        <div class="toggle-buttons" v-if="showViewToggle">
          <button
            v-for="opt in viewOptions"
            :key="opt.value"
            class="toggle-btn"
            :class="{ 'active': activeView === opt.value }"
            @click="activeView = opt.value"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>
    </div>

    <div class="chart-body">
      <div ref="chartRef" class="echarts-dom"></div>

      <div class="no-data-overlay" v-if="!canRenderChart">
        <div class="overlay-content">
          <div class="overlay-icon">ℹ</div>
          <h4>该服务暂无历史详单记录</h4>
          <p v-if="serviceId === 'claude_api' || serviceId === 'kimi_api' || serviceId === 'glm_api'">
            已成功校验 API Key。当前版本暂不抓取网页历史账单，仅记录接口授权有效性。
          </p>
          <p v-else-if="serviceId === 'deepseek_api' && (activeView === 'tokens' || activeView === 'requests')">
            请先在「设置」中上传 DeepSeek 平台导出的用量 CSV/ZIP，以查看 Token 和请求次数趋势。
          </p>
          <p v-else-if="!hasKey">
            请先在"设置"中配置该服务的 API 密钥以启用监控。
          </p>
          <p v-else>
            暂未抓取到有效历史指标，请点击右上角进行数据同步。
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import * as echarts from 'echarts'
import { aggregateByDate, formatChartDate, hasDeepSeekCsvData, getBalanceHistory } from '../utils/chartData'

const props = defineProps({
  serviceId: { type: String, required: true },
  historyData: { type: Array, default: () => [] },
  hasKey: { type: Boolean, default: false }
})

const chartRef = ref(null)
let chartInstance = null
const activeView = ref('tokens_cost')

const aggregatedData = computed(() => aggregateByDate(props.historyData))

const deepseekHasCsv = computed(() => hasDeepSeekCsvData(props.historyData))

const hasData = computed(() => {
  if (props.serviceId === 'claude_api' || props.serviceId === 'kimi_api' || props.serviceId === 'glm_api') {
    return false
  }
  return props.historyData && props.historyData.length > 0
})

const canRenderChart = computed(() => {
  if (!hasData.value) return false
  if (props.serviceId === 'deepseek_api') {
    if (activeView.value === 'tokens' || activeView.value === 'requests') {
      return deepseekHasCsv.value
    }
    return true
  }
  return true
})

const showViewToggle = computed(() => hasData.value)

const viewOptions = computed(() => {
  if (props.serviceId === 'deepseek_api') {
    return [
      { label: '账户余额', value: 'balance' },
      { label: '消费趋势', value: 'consumption' },
      { label: 'Token 趋势', value: 'tokens' },
      { label: '请求次数', value: 'requests' }
    ]
  }
  return [
    { label: 'Token & 费用', value: 'tokens_cost' },
    { label: '请求次数', value: 'requests' }
  ]
})

watch(() => props.serviceId, (newId) => {
  activeView.value = newId === 'deepseek_api' ? 'balance' : 'tokens_cost'
})

watch([() => props.historyData, activeView, () => props.serviceId], () => {
  renderChart()
}, { deep: true })

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
  }
})

const initChart = () => {
  if (chartRef.value) {
    chartInstance = echarts.init(chartRef.value, 'dark')
    renderChart()
  }
}

const handleResize = () => {
  if (chartInstance) {
    chartInstance.resize()
  }
}

const renderChart = () => {
  if (!chartInstance || !canRenderChart.value) return

  const data = aggregatedData.value
  const dates = data.map(item => formatChartDate(item.dateStr))

  let option = {}

  const baseGrid = {
    left: '4%',
    right: '4%',
    bottom: '8%',
    top: '16%',
    containLabel: true
  }

  const baseTooltip = {
    trigger: 'axis',
    backgroundColor: 'rgba(18, 16, 30, 0.9)',
    borderColor: 'rgba(99, 102, 241, 0.3)',
    textStyle: { color: '#f8fafc' },
    axisPointer: { type: 'line' }
  }

  if (props.serviceId === 'deepseek_api') {
    let currency = 'CNY'
    if (props.historyData.length > 0) {
      try {
        const extra = JSON.parse(props.historyData[0].extraJson || '{}')
        currency = extra.currency || 'CNY'
      } catch (e) {}
    }

    if (activeView.value === 'balance') {
      const balanceRecords = getBalanceHistory(props.historyData)
      const balanceDates = balanceRecords.map(item => formatChartDate(item.dateStr))
      const balances = balanceRecords.map(item => {
        const extra = JSON.parse(item.extraJson || '{}')
        return extra.total_balance
      })

      option = {
        backgroundColor: 'transparent',
        tooltip: {
          ...baseTooltip,
          formatter: (params) => {
            const p = params[0]
            return `${p.name}<br/><span style="display:inline-block;margin-right:4px;border-radius:10px;width:10px;height:10px;background-color:#10b981;"></span>余额: <b>${p.value.toFixed(2)} ${currency}</b>`
          }
        },
        grid: baseGrid,
        xAxis: { type: 'category', data: balanceDates, axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } }, axisLabel: { color: '#94a3b8' } },
        yAxis: { type: 'value', name: `余额 (${currency})`, nameTextStyle: { color: '#94a3b8' }, axisLine: { show: false }, splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } }, axisLabel: { color: '#94a3b8' } },
        series: [{
          name: '账户余额',
          type: 'line',
          data: balances,
          smooth: true,
          showSymbol: false,
          lineStyle: { width: 3, color: '#10b981' },
          itemStyle: { color: '#10b981' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(16, 185, 129, 0.25)' },
              { offset: 1, color: 'rgba(16, 185, 129, 0.00)' }
            ])
          }
        }]
      }
    } else if (activeView.value === 'consumption') {
      const consumed = data.map(item => {
        try {
          const extra = JSON.parse(item.extraJson || '{}')
          if (extra.daily_consumed !== undefined) return extra.daily_consumed
        } catch (e) {}
        return item.costUsd || 0
      })

      option = {
        backgroundColor: 'transparent',
        tooltip: {
          ...baseTooltip,
          formatter: (params) => `${params[0].name}<br/>消费: <b>${params[0].value.toFixed(2)} ${currency}</b>`
        },
        grid: baseGrid,
        xAxis: { type: 'category', data: dates, axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } }, axisLabel: { color: '#94a3b8' } },
        yAxis: { type: 'value', name: `消费 (${currency})`, splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } }, axisLabel: { color: '#94a3b8' } },
        series: [{
          name: '每日消费',
          type: 'line',
          data: consumed,
          smooth: true,
          showSymbol: false,
          lineStyle: { width: 3, color: '#f59e0b' },
          itemStyle: { color: '#f59e0b' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(245, 158, 11, 0.25)' },
              { offset: 1, color: 'rgba(245, 158, 11, 0.00)' }
            ])
          }
        }]
      }
    } else if (activeView.value === 'tokens') {
      const tokensIn = data.map(d => d.tokensIn)
      const tokensOut = data.map(d => d.tokensOut)
      const totalTokens = data.map(d => d.tokensIn + d.tokensOut)

      option = {
        backgroundColor: 'transparent',
        tooltip: baseTooltip,
        legend: { data: ['输入 Token', '输出 Token', '总 Token'], textStyle: { color: '#94a3b8' }, right: '4%' },
        grid: baseGrid,
        xAxis: { type: 'category', data: dates, axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } } },
        yAxis: { type: 'value', name: 'Tokens', splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } } },
        series: [
          { name: '输入 Token', type: 'line', smooth: true, showSymbol: false, data: tokensIn, lineStyle: { width: 2, color: '#6366f1' }, itemStyle: { color: '#6366f1' } },
          { name: '输出 Token', type: 'line', smooth: true, showSymbol: false, data: tokensOut, lineStyle: { width: 2, color: '#8b5cf6' }, itemStyle: { color: '#8b5cf6' } },
          { name: '总 Token', type: 'line', smooth: true, showSymbol: false, data: totalTokens, lineStyle: { width: 3, color: '#10b981' }, itemStyle: { color: '#10b981' } }
        ]
      }
    } else {
      const requests = data.map(d => d.requests)
      option = {
        backgroundColor: 'transparent',
        tooltip: baseTooltip,
        grid: baseGrid,
        xAxis: { type: 'category', data: dates, axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } } },
        yAxis: { type: 'value', name: '请求次数', splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } } },
        series: [{
          name: '请求次数',
          type: 'line',
          data: requests,
          smooth: true,
          showSymbol: false,
          lineStyle: { width: 3, color: '#10b981' },
          itemStyle: { color: '#10b981' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(16, 185, 129, 0.2)' },
              { offset: 1, color: 'rgba(16, 185, 129, 0.0)' }
            ])
          }
        }]
      }
    }
  } else if (props.serviceId === 'claude_code' || props.serviceId === 'codex') {
    if (activeView.value === 'tokens_cost') {
      const tokensIn = data.map(d => d.tokensIn)
      const tokensOut = data.map(d => d.tokensOut)
      const totalTokens = data.map(d => d.tokensIn + d.tokensOut)

      option = {
        backgroundColor: 'transparent',
        tooltip: baseTooltip,
        legend: { data: ['输入 Token', '输出 Token', '总 Token'], textStyle: { color: '#94a3b8' }, right: '4%' },
        grid: baseGrid,
        xAxis: { type: 'category', data: dates, axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } } },
        yAxis: { type: 'value', name: 'Tokens 数量', splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } } },
        series: [
          { name: '输入 Token', type: 'line', smooth: true, showSymbol: false, data: tokensIn, lineStyle: { width: 2, color: '#6366f1' }, itemStyle: { color: '#6366f1' } },
          { name: '输出 Token', type: 'line', smooth: true, showSymbol: false, data: tokensOut, lineStyle: { width: 2, color: '#8b5cf6' }, itemStyle: { color: '#8b5cf6' } },
          { name: '总 Token', type: 'line', smooth: true, showSymbol: false, data: totalTokens, lineStyle: { width: 3, color: '#10b981' }, itemStyle: { color: '#10b981' } }
        ]
      }
    } else {
      const requests = data.map(d => d.requests)
      const sessions = data.map(item => {
        try {
          const extra = JSON.parse(item.extraJson || '{}')
          return extra.sessions || 0
        } catch (e) {
          return 0
        }
      })

      option = {
        backgroundColor: 'transparent',
        tooltip: baseTooltip,
        legend: { data: ['会话次数', '请求次数'], textStyle: { color: '#94a3b8' }, right: '4%' },
        grid: baseGrid,
        xAxis: { type: 'category', data: dates, axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } } },
        yAxis: [
          { type: 'value', name: '会话数', splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } } },
          { type: 'value', name: '请求数', splitLine: { show: false } }
        ],
        series: [
          { name: '会话次数', type: 'line', smooth: true, showSymbol: false, data: sessions, lineStyle: { width: 3, color: '#f59e0b' }, itemStyle: { color: '#f59e0b' } },
          { name: '请求次数', type: 'line', smooth: true, showSymbol: false, yAxisIndex: 1, data: requests, lineStyle: { width: 2, color: '#6366f1' }, itemStyle: { color: '#6366f1' } }
        ]
      }
    }
  } else {
    if (activeView.value === 'tokens_cost') {
      const tokensIn = data.map(d => d.tokensIn)
      const tokensOut = data.map(d => d.tokensOut)
      const totalTokens = data.map(d => d.tokensIn + d.tokensOut)
      const costUsd = data.map(d => d.costUsd)

      option = {
        backgroundColor: 'transparent',
        tooltip: {
          ...baseTooltip,
          formatter: (params) => {
            let res = `${params[0].name}<br/>`
            params.forEach(p => {
              if (p.seriesName.includes('Token')) {
                res += `${p.marker}${p.seriesName}: <b>${p.value.toLocaleString()}</b><br/>`
              } else {
                res += `${p.marker}${p.seriesName}: <b>$${p.value.toFixed(4)} USD</b><br/>`
              }
            })
            return res
          }
        },
        legend: { data: ['输入 Token', '输出 Token', '总 Token', '预估费用 (USD)'], textStyle: { color: '#94a3b8' }, right: '4%' },
        grid: baseGrid,
        xAxis: { type: 'category', data: dates, axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } } },
        yAxis: [
          { type: 'value', name: 'Tokens', splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } } },
          { type: 'value', name: '费用 (USD)', splitLine: { show: false }, axisLabel: { formatter: '${value}' } }
        ],
        series: [
          { name: '输入 Token', type: 'line', smooth: true, showSymbol: false, data: tokensIn, lineStyle: { width: 2, color: '#6366f1' }, itemStyle: { color: '#6366f1' } },
          { name: '输出 Token', type: 'line', smooth: true, showSymbol: false, data: tokensOut, lineStyle: { width: 2, color: '#8b5cf6' }, itemStyle: { color: '#8b5cf6' } },
          { name: '总 Token', type: 'line', smooth: true, showSymbol: false, data: totalTokens, lineStyle: { width: 3, color: '#10b981' }, itemStyle: { color: '#10b981' } },
          { name: '预估费用 (USD)', type: 'line', yAxisIndex: 1, smooth: true, showSymbol: false, data: costUsd, lineStyle: { width: 3, color: '#f59e0b' }, itemStyle: { color: '#f59e0b' } }
        ]
      }
    } else {
      const requests = data.map(d => d.requests)
      option = {
        backgroundColor: 'transparent',
        tooltip: baseTooltip,
        grid: baseGrid,
        xAxis: { type: 'category', data: dates, axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } } },
        yAxis: { type: 'value', name: '请求次数', splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } } },
        series: [{
          name: '请求次数',
          type: 'line',
          smooth: true,
          showSymbol: false,
          data: requests,
          lineStyle: { width: 3, color: '#10b981' },
          itemStyle: { color: '#10b981' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(16, 185, 129, 0.2)' },
              { offset: 1, color: 'rgba(16, 185, 129, 0.0)' }
            ])
          }
        }]
      }
    }
  }

  chartInstance.setOption(option, true)
}
</script>

<style scoped>
.chart-container {
  padding: 24px;
  display: flex;
  flex-direction: column;
  height: 380px;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.chart-title {
  font-size: 18px;
  color: var(--text-primary);
}

.chart-subtitle {
  font-size: 12px;
  color: var(--text-muted);
  display: block;
  margin-top: 4px;
}

.toggle-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  background: rgba(255, 255, 255, 0.03);
  padding: 4px;
  border-radius: 8px;
  border: 1px solid var(--border-color);
}

.toggle-btn {
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}

.toggle-btn:hover {
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.05);
}

.toggle-btn.active {
  background: var(--color-primary-gradient);
  color: #ffffff;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.2);
}

.chart-body {
  flex: 1;
  position: relative;
}

.echarts-dom {
  width: 100%;
  height: 100%;
}

.no-data-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(10, 9, 16, 0.7);
  display: flex;
  justify-content: center;
  align-items: center;
  text-align: center;
  border-radius: 12px;
}

.overlay-content {
  max-width: 400px;
  padding: 20px;
}

.overlay-icon {
  font-size: 32px;
  color: var(--text-muted);
  margin-bottom: 12px;
}

.overlay-content h4 {
  font-size: 16px;
  margin-bottom: 8px;
  color: var(--text-primary);
}

.overlay-content p {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.6;
}
</style>