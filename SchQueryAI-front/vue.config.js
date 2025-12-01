const { defineConfig } = require('@vue/cli-service')
module.exports = defineConfig({
    transpileDependencies: true,
    // 添加devServer配置，指定端口为80
    devServer: {
        port: 80,
        client: {
            webSocketURL: {
                pathname: '/ws-hmr'
            }
        },
        webSocketServer: {
            options: {
                path: '/ws-hmr'
            }
        },
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                pathRewrite: {
                    '^/api': ''
                }
            },
            '/ws/customer-service': {
                target: 'ws://localhost:8080',
                ws: true,
                changeOrigin: true
            }
        }
    }
})
