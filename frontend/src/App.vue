<template>
  <div class="app-container">
    <main class="main-content">
      <Dashboard />
    </main>
    <footer class="app-footer">
      <div class="footer-content">
        <span>AI Usage Monitor &copy; 2026</span>
        <span class="divider">|</span>
        <span>由 Vue 3 + Servlet + MySQL + JDBC 驱动的 Web Dashboard</span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import Dashboard from './components/Dashboard.vue'
</script>

<style scoped>
.app-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
}

.app-footer {
  text-align: center;
  padding: 24px;
  color: var(--text-muted);
  font-size: 12px;
  border-top: 1px solid var(--border-color);
  background: rgba(10, 9, 16, 0.5);
  backdrop-filter: blur(10px);
}

.footer-content {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
}

.divider {
  opacity: 0.3;
}
</style>
