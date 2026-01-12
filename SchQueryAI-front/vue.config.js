const { defineConfig } = require('@vue/cli-service')
module.exports = defineConfig({
    transpileDependencies: true,
    parallel: false,
    // 添加devServer配置，指定端口为80
    devServer: {
        port: 80,
        client: {
            webSocketURL: {
                pathname: '/ws-hmr'
            },
            // 避免 ResizeObserver 警告被 webpack-dev-server overlay 当成致命错误遮罩页面
            overlay: {
                runtimeErrors: (error) => {
                    const message = error && error.message ? String(error.message) : ''
                    if (message.includes('ResizeObserver loop limit exceeded')) return false
                    if (message.includes('ResizeObserver loop completed with undelivered notifications')) return false
                    return true
                }
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
