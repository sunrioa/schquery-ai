const { defineConfig } = require('@vue/cli-service')
module.exports = defineConfig({
  transpileDependencies: true,
  // 添加devServer配置，指定端口为80
  devServer: {
    port: 80
  }
})