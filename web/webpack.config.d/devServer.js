// Only configure devServer in development mode
if (config.mode === 'development') {
    config.devServer = config.devServer || {};
    config.devServer.allowedHosts = 'all';
    // Enable SPA routing for language-based URLs like /en/, /zh/, /de/, /es/
    config.devServer.historyApiFallback = true;
}
