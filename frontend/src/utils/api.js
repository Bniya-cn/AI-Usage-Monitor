import axios from 'axios'


const client = axios.create({
  baseURL: '',
  timeout: 45000
})

export const api = {

  getConfig() {
    return client.get('/api/config').then(res => res.data)
  },


  saveConfig(configs) {
    return client.post('/api/config', configs).then(res => res.data)
  },


  getAuthStatus() {
    return client.get('/api/auth').then(res => res.data)
  },


  saveApiKey(service, apiKey) {
    return client.post('/api/auth', { service, api_key: apiKey }).then(res => res.data)
  },


  clearApiKey(service) {
    return client.post('/api/auth', { service, action: 'clear' }).then(res => res.data)
  },


  getSummary() {
    return client.get('/api/usage?action=summary').then(res => res.data)
  },


  getHistory(service, days = 30) {
    return client.get(`/api/usage?action=history&service=${service}&days=${days}`).then(res => res.data)
  },


  triggerSync() {
    return client.post('/api/usage?action=sync').then(res => res.data)
  },


  importDeepSeekCsv(file) {
    const formData = new FormData()
    formData.append('file', file)
    return client.post('/api/usage?action=import_deepseek_csv', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    }).then(res => res.data)
  }
}
