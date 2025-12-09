// Only configure devServer in development mode
if (config.mode === 'development') {
    config.devServer = config.devServer || {};
    config.devServer.allowedHosts = 'all';
}
