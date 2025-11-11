<template>
  <router-view />
</template>

<script>
export default {
  name: 'App'
}
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

#app {
  height: 100vh;
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
