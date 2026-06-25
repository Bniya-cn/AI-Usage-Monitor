<template>
  <Transition name="modal-fade">
    <div class="modal-backdrop" v-if="isOpen" @click.self="close">
      <div class="modal-container glass-panel animate-fade-in">

        <div class="modal-header">
          <h2 class="modal-title">系统参数配置</h2>
          <button class="close-btn" @click="close">&times;</button>
        </div>


        <div class="modal-tabs">
          <button
            class="tab-btn"
            :class="{ 'active': activeTab === 'keys' }"
            @click="activeTab = 'keys'"
          >
            🔑 API 密钥管理
          </button>
          <button
            class="tab-btn"
            :class="{ 'active': activeTab === 'sync' }"
            @click="activeTab = 'sync'"
          >
            ⚙️ 同步与偏好
          </button>
        </div>


        <div class="modal-body">

          <div v-if="activeTab === 'keys'" class="tab-content">
            <p class="tab-desc">密钥保存在本地数据库中。校验动作会向官方发起轻量验证以核对 Key 的有效性。</p>

            <input
              type="file"
              ref="csvFileInput"
              accept=".csv,.zip"
              class="csv-file-input"
              @change="handleCsvSelect"
            />

            <div class="keys-list">
              <div v-for="svc in filterApiServices" :key="svc.id" class="key-item">
                <div class="key-info">
                  <span class="key-name">{{ svc.name }}</span>
                  <span class="key-status-badge" :class="getKeyStatusClass(svc.id)">
                    {{ getKeyStatusText(svc.id) }}
                  </span>
                </div>

                <div class="key-input-group">
                  <input
                    :type="showKeys[svc.id] ? 'text' : 'password'"
                    class="key-input"
                    :placeholder="getKeyPlaceholder(svc.id)"
                    v-model="keyInputs[svc.id]"
                    :disabled="verifying[svc.id]"
                  />

                  <button class="icon-btn" @click="showKeys[svc.id] = !showKeys[svc.id]">
                    {{ showKeys[svc.id] ? '👁️' : '🙈' }}
                  </button>

                  <button
                    class="save-key-btn"
                    @click="saveKey(svc.id)"
                    :disabled="verifying[svc.id]"
                  >
                    {{ verifying[svc.id] ? '校验中...' : '校验并保存' }}
                  </button>

                  <button
                    v-if="authStates[svc.id]?.has_key"
                    class="clear-key-btn"
                    @click="clearKey(svc.id)"
                    :disabled="verifying[svc.id]"
                  >
                    清除
                  </button>
                </div>
                <div class="error-msg-detail" v-if="authStates[svc.id]?.error_msg && authStates[svc.id].error_msg !== 'missing_credentials'">
                  校验失败: {{ authStates[svc.id].error_msg }}
                </div>

                <div v-if="svc.id === 'deepseek_api'" class="csv-import-area">
                  <p class="csv-import-desc">
                    DeepSeek 官方 API 不提供历史 Token/请求统计。请从
                    <a href="https://platform.deepseek.com/usage" target="_blank" rel="noopener">平台 Usage 页面</a>
                    导出月度 CSV/ZIP 后上传。
                  </p>
                  <div class="csv-upload-row">
                    <button
                      class="csv-upload-btn"
                      @click="triggerCsvUpload"
                      :disabled="importingCsv"
                    >
                      {{ importingCsv ? '导入中...' : '上传用量 CSV/ZIP' }}
                    </button>
                  </div>
                  <p class="csv-import-hint" v-if="csvImportMsg" :class="csvImportSuccess ? 'success' : 'error'">
                    {{ csvImportMsg }}
                  </p>
                </div>
              </div>
            </div>
          </div>


          <div v-if="activeTab === 'sync'" class="tab-content">
            <div class="setting-item">
              <div class="setting-info">
                <label class="setting-label">API 定时同步间隔</label>
                <span class="setting-desc">包括 ChatGPT, Claude, Kimi, GLM, DeepSeek API 用量的定时刷新周期。</span>
              </div>
              <select v-model="syncConfig.sync_interval_minutes" class="setting-select">
                <option value="5">5 分钟</option>
                <option value="10">10 分钟</option>
                <option value="15">15 分钟</option>
                <option value="30">30 分钟</option>
                <option value="60">1 小时</option>
                <option value="120">2 小时</option>
              </select>
            </div>

            <div class="setting-item">
              <div class="setting-info">
                <label class="setting-label">Claude Code 同步间隔</label>
                <span class="setting-desc">本地日志文件分析频率，建议设置较短时间以保证及时响应。</span>
              </div>
              <select v-model="syncConfig.claude_code_sync_interval_minutes" class="setting-select">
                <option value="1">1 分钟</option>
                <option value="2">2 分钟</option>
                <option value="5">5 分钟</option>
                <option value="10">10 分钟</option>
                <option value="30">30 分钟</option>
              </select>
            </div>

            <div class="setting-item">
              <div class="setting-info">
                <label class="setting-label">显示主题</label>
                <span class="setting-desc">系统主视觉模式。当前已针对暗黑色彩做深度极客风渲染。</span>
              </div>
              <select v-model="syncConfig.theme" class="setting-select">
                <option value="dark">极客暗黑 (Glassmorphism)</option>
              </select>
            </div>

            <div class="save-config-area">
              <button class="save-config-btn" @click="saveGlobalConfig">
                {{ savingConfig ? '正在保存...' : '保存偏好配置' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { api } from '../utils/api'

const props = defineProps({
  isOpen: { type: Boolean, required: true },
  authStates: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['close', 'refresh'])

const activeTab = ref('keys')


const showKeys = ref({
  chatgpt_api: false,
  claude_api: false,
  kimi_api: false,
  glm_api: false,
  deepseek_api: false
})


const verifying = ref({
  chatgpt_api: false,
  claude_api: false,
  kimi_api: false,
  glm_api: false,
  deepseek_api: false
})

const keyInputs = ref({
  chatgpt_api: '',
  claude_api: '',
  kimi_api: '',
  glm_api: '',
  deepseek_api: ''
})

const syncConfig = ref({
  sync_interval_minutes: '30',
  claude_code_sync_interval_minutes: '5',
  theme: 'dark'
})

const savingConfig = ref(false)
const csvFileInput = ref(null)
const importingCsv = ref(false)
const csvImportMsg = ref('')
const csvImportSuccess = ref(false)

const SERVICES = [
  { id: 'chatgpt_api', name: 'ChatGPT API' },
  { id: 'claude_api', name: 'Claude API' },
  { id: 'kimi_api', name: 'KIMI API' },
  { id: 'glm_api', name: 'GLM API' },
  { id: 'deepseek_api', name: 'DeepSeek API' }
]

const filterApiServices = computed(() => SERVICES)


watch(() => props.isOpen, (newVal) => {
  if (newVal) {

    SERVICES.forEach(s => {
      keyInputs.value[s.id] = ''
    })

    api.getConfig().then(data => {
      syncConfig.value = { ...syncConfig.value, ...data }
    })
  }
})

const close = () => {
  emit('close')
}


const getKeyPlaceholder = (id) => {
  const state = props.authStates[id]
  if (state && state.has_key) {
    return '•••••••••••••••••••••••••••• (已配置)'
  }
  return `请输入 ${id === 'chatgpt_api' ? 'sk-...' : 'API Key'}`
}


const getKeyStatusText = (id) => {
  const state = props.authStates[id]
  if (!state || !state.has_key) return '未配置'
  return state.is_auth === 1 ? '已验证' : '验证失败'
}

const getKeyStatusClass = (id) => {
  const state = props.authStates[id]
  if (!state || !state.has_key) return 'badge-muted'
  return state.is_auth === 1 ? 'badge-success' : 'badge-danger'
}


const saveKey = async (serviceId) => {
  const inputKey = keyInputs.value[serviceId]
  if (!inputKey || !inputKey.trim()) {
    alert('请输入有效的 API Key')
    return
  }

  verifying.value[serviceId] = true
  try {
    const res = await api.saveApiKey(serviceId, inputKey)
    if (res.success) {
      alert(`${serviceId} 密钥验证通过并保存成功！`)
      keyInputs.value[serviceId] = ''
      emit('refresh')
    } else {
      alert(`验证失败：${res.error || '无法通过官方连接校验。'}`)
      emit('refresh')
    }
  } catch (e) {
    alert(`通信异常，校验失败: ${e.response?.data?.error || e.message}`)
    emit('refresh')
  } finally {
    verifying.value[serviceId] = false
  }
}


const clearKey = async (serviceId) => {
  if (!confirm(`确定要清除 ${serviceId} 的 API 密钥吗？`)) return
  try {
    await api.clearApiKey(serviceId)
    emit('refresh')
  } catch (e) {
    alert('清除失败: ' + e.message)
  }
}


const triggerCsvUpload = () => {
  csvFileInput.value?.click()
}

const handleCsvSelect = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return

  importingCsv.value = true
  csvImportMsg.value = ''
  try {
    const res = await api.importDeepSeekCsv(file)
    if (res.success) {
      csvImportSuccess.value = true
      csvImportMsg.value = `导入成功：${res.days_imported} 天，${res.total_requests} 次请求，${res.total_tokens} Token，消费 ${res.total_cost.toFixed(2)} CNY`
      emit('refresh')
    } else {
      csvImportSuccess.value = false
      csvImportMsg.value = res.error || '导入失败'
    }
  } catch (e) {
    csvImportSuccess.value = false
    csvImportMsg.value = e.response?.data?.error || e.message || '导入失败'
  } finally {
    importingCsv.value = false
    if (csvFileInput.value) csvFileInput.value.value = ''
  }
}

const saveGlobalConfig = async () => {
  savingConfig.value = true
  try {
    await api.saveConfig(syncConfig.value)
    alert('偏好配置保存成功！定时抓取将立即按新频率重排。')
    emit('refresh')
    close()
  } catch (e) {
    alert('配置保存失败: ' + e.message)
  } finally {
    savingConfig.value = false
  }
}
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(4, 3, 8, 0.7);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
  backdrop-filter: blur(8px);
}

.modal-container {
  width: 90%;
  max-width: 600px;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-color: rgba(255, 255, 255, 0.08);
}

.modal-header {
  padding: 20px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--border-color);
}

.modal-title {
  font-size: 18px;
  color: var(--text-primary);
}

.close-btn {
  font-size: 28px;
  color: var(--text-muted);
  cursor: pointer;
  line-height: 1;
}

.close-btn:hover {
  color: var(--text-primary);
}

.modal-tabs {
  display: flex;
  border-bottom: 1px solid var(--border-color);
  background: rgba(0, 0, 0, 0.2);
}

.tab-btn {
  flex: 1;
  padding: 14px;
  text-align: center;
  font-weight: 500;
  color: var(--text-secondary);
  border-bottom: 2px solid transparent;
}

.tab-btn:hover {
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.02);
}

.tab-btn.active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
  background: rgba(99, 102, 241, 0.03);
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

.tab-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.tab-desc {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
}

.keys-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.key-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.key-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.key-name {
  font-weight: 500;
  font-size: 14px;
}

.key-status-badge {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 99px;
  font-weight: 600;
}

.key-input-group {
  display: flex;
  gap: 8px;
  align-items: center;
}

.key-input {
  flex: 1;
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 8px 12px;
  color: var(--text-primary);
  font-size: 13px;
}

.key-input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.icon-btn {
  font-size: 16px;
  padding: 0 8px;
}

.save-key-btn {
  background: var(--color-primary-gradient);
  color: white;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
}

.save-key-btn:hover {
  filter: brightness(1.1);
}

.clear-key-btn {
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-danger);
  border: 1px solid rgba(239, 68, 68, 0.2);
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 12px;
}

.clear-key-btn:hover {
  background: rgba(239, 68, 68, 0.2);
}

.error-msg-detail {
  font-size: 11px;
  color: var(--color-danger);
  margin-top: 2px;
}


.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-color);
}

.setting-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 70%;
}

.setting-label {
  font-size: 14px;
  font-weight: 500;
}

.setting-desc {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.4;
}

.setting-select {
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
  padding: 8px 12px;
  border-radius: 8px;
  outline: none;
  cursor: pointer;
}

.setting-select:focus {
  border-color: var(--color-primary);
}

.save-config-area {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}

.save-config-btn {
  background: var(--color-primary-gradient);
  color: white;
  padding: 10px 24px;
  border-radius: 8px;
  font-weight: 600;
}

.save-config-btn:hover {
  filter: brightness(1.1);
}


.csv-import-area {
  margin-top: 8px;
  padding: 12px;
  border-radius: 8px;
  background: rgba(99, 102, 241, 0.05);
  border: 1px dashed rgba(99, 102, 241, 0.2);
}

.csv-import-desc {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.5;
  margin-bottom: 8px;
}

.csv-import-desc a {
  color: var(--color-primary);
}

.csv-file-input {
  display: none;
}

.csv-upload-row {
  display: flex;
  gap: 8px;
}

.csv-upload-btn {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
  border: 1px solid rgba(16, 185, 129, 0.3);
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
}

.csv-upload-btn:hover:not(:disabled) {
  background: rgba(16, 185, 129, 0.25);
}

.csv-upload-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.csv-import-hint {
  font-size: 11px;
  margin-top: 6px;
}

.csv-import-hint.success {
  color: #10b981;
}

.csv-import-hint.error {
  color: var(--color-danger);
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.3s ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}
</style>
