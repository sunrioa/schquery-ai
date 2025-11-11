<template>
  <div class="not-found">
    <div class="content">
      <h1>404</h1>
      <h2>页面未找到</h2>
      <p>抱歉，您访问的页面不存在。</p>
      <el-button type="primary" @click="$router.push('/home')">
        返回首页
      </el-button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'NotFound'
}
</script>

<style scoped>
.not-found {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.content {
  text-align: center;
  color: white;
}

.content h1 {
  font-size: 120px;
  margin: 0;
  font-weight: bold;
}

.content h2 {
  font-size: 32px;
  margin: 20px 0;
}

.content p {
  font-size: 18px;
  margin-bottom: 30px;
  opacity: 0.9;
}
</style>