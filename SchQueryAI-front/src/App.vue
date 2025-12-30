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
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
  background-color: #f0f2f5;
}

[data-theme="dark"] body {
  background-color: #1a1a1a;
}

#app {
  height: 100vh;
}

[data-theme="dark"] #app {
  background-color: #1a1a1a;
}

/* 全局表格暗夜模式 */
[data-theme="dark"] .el-table,
[data-theme="dark"] .el-table__header,
[data-theme="dark"] .el-table__body,
[data-theme="dark"] .el-table__header-wrapper,
[data-theme="dark"] .el-table__body-wrapper {
  background-color: #2a2a2a !important;
}

[data-theme="dark"] .el-table__header th,
[data-theme="dark"] .el-table__cell,
[data-theme="dark"] table th,
[data-theme="dark"] table td {
  background-color: #2a2a2a !important;
  color: #e0e0e0 !important;
  border-color: #444 !important;
}

[data-theme="dark"] .el-table__header th {
  background-color: #333 !important;
}

[data-theme="dark"] .el-table__body tr {
  background-color: #2a2a2a !important;
}

[data-theme="dark"] .el-table__body tr:hover > td {
  background-color: #333 !important;
}

[data-theme="dark"] .el-table--striped .el-table__body tr.el-table__row--striped {
  background-color: #242424 !important;
}

[data-theme="dark"] .el-scrollbar__view {
  background-color: #2a2a2a !important;
}

/* 全局对话框暗夜模式 */
[data-theme="dark"] .el-overlay {
  background-color: rgba(0, 0, 0, 0.7) !important;
}

[data-theme="dark"] .el-dialog {
  background-color: #2a2a2a !important;
}

[data-theme="dark"] .el-dialog__header {
  background-color: #2a2a2a !important;
  border-bottom-color: #444 !important;
}

[data-theme="dark"] .el-dialog__title {
  color: #e0e0e0 !important;
}

[data-theme="dark"] .el-dialog__close {
  color: #999 !important;
}

[data-theme="dark"] .el-dialog__close:hover {
  color: #e0e0e0 !important;
}

[data-theme="dark"] .el-dialog__body {
  background-color: #2a2a2a !important;
  color: #e0e0e0 !important;
}

[data-theme="dark"] .el-descriptions {
  color: #e0e0e0 !important;
  background-color: #2a2a2a !important;
}

[data-theme="dark"] .el-descriptions__header {
  border-bottom-color: #444 !important;
}

[data-theme="dark"] .el-descriptions__body {
  border-color: #444 !important;
  background-color: #2a2a2a !important;
}

[data-theme="dark"] .el-descriptions__item {
  border-bottom-color: #444 !important;
}

[data-theme="dark"] .el-descriptions__label {
  color: #999 !important;
  border-right-color: #444 !important;
  background-color: #333 !important;
}

[data-theme="dark"] .el-descriptions__content {
  color: #e0e0e0 !important;
  background-color: #2a2a2a !important;
}

/* 全局输入框暗夜模式 */
[data-theme="dark"] .el-input,
[data-theme="dark"] .el-input__wrapper,
[data-theme="dark"] .el-input__inner,
[data-theme="dark"] .el-textarea,
[data-theme="dark"] .el-textarea__inner {
  background-color: #374151 !important;
  border-color: #4b5563 !important;
  color: #e5e7eb !important;
}

[data-theme="dark"] .el-input__inner::placeholder,
[data-theme="dark"] .el-textarea__inner::placeholder {
  color: #9ca3af !important;
  opacity: 1 !important;
}

[data-theme="dark"] .el-input__inner:hover,
[data-theme="dark"] .el-input__inner:focus,
[data-theme="dark"] .el-textarea__inner:hover,
[data-theme="dark"] .el-textarea__inner:focus {
  border-color: #60a5fa !important;
}

[data-theme="dark"] .el-input__inner:focus,
[data-theme="dark"] .el-textarea__inner:focus {
  box-shadow: 0 0 0 2px rgba(96, 165, 250, 0.2) !important;
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
