// Allow access via nginx proxy or any host
config.devServer = config.devServer || {};
config.devServer.allowedHosts = 'all';

