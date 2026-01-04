<template>
  <component :is="layoutComponent">
    <router-view />
  </component>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from './layouts/AdminLayout.vue'

const route = useRoute()
const layoutComponent = computed(() => {
  return route.meta?.layout === 'admin' ? AdminLayout : 'div'
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: var(--app-font-family);
  background-color: var(--app-bg);
  color: var(--app-text);
}

#app {
  height: 100%;
}

/* 禁用Element Plus对话框的飞出动画效果 */
.el-dialog {
  animation: none !important;
  transform: none !important;
}

.el-dialog__wrapper {
  animation: none !important;
}

.el-overlay-dialog {
  animation: none !important;
}

/* 禁用对话框进入和离开动画 */
.el-dialog-fade-enter-active,
.el-dialog-fade-leave-active {
  animation: none !important;
}

.el-dialog-fade-enter-from,
.el-dialog-fade-enter-to,
.el-dialog-fade-leave-from,
.el-dialog-fade-leave-to {
  animation: none !important;
  transform: none !important;
}
</style>
