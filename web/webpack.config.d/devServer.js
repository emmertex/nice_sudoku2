// Only configure devServer in development mode
if (config.mode === 'development') {
    config.devServer = config.devServer || {};
    config.devServer.allowedHosts = 'all';
    // Enable SPA routing for language-based URLs like /en/, /zh/, /de/, /es/
    config.devServer.historyApiFallback = true;
    // Proxy API and health check endpoints to the local backend
    config.devServer.proxy = [{
        context: ['/api', '/health'],
        target: 'http://localhost:8181',
        changeOrigin: true
    }];
}
