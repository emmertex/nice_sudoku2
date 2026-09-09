#!/usr/bin/env bash
# Run an isolated foreground nginx with the same route contract as production.
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
frontend_port="${1:-8081}"
backend_port="${2:-8181}"
[[ "$frontend_port" =~ ^[0-9]+$ && "$backend_port" =~ ^[0-9]+$ ]] || exit 1
runtime_dir="$(mktemp -d /tmp/nice-sudoku-nginx.XXXXXX)"
cleanup() {
    nginx -p "$runtime_dir/" -c "$runtime_dir/nginx.conf" -s quit 2>/dev/null || true
    rm -rf "$runtime_dir"
}
trap cleanup EXIT HUP INT TERM
cat > "$runtime_dir/nginx.conf" <<NGINX
pid $runtime_dir/nginx.pid;
error_log stderr;
events {}
http {
    types {
        text/html html;
        text/css css;
        application/javascript js;
        application/json json;
        image/svg+xml svg;
        image/png png;
        image/x-icon ico;
        text/plain md;
    }
    access_log off;
    client_body_temp_path $runtime_dir/body;
    proxy_temp_path $runtime_dir/proxy;
    server {
        listen $frontend_port;
        root "$project_dir/web/build/distributions";
        index index.html;
        client_max_body_size 64k;
        location /api/ {
            proxy_pass http://127.0.0.1:$backend_port;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_read_timeout 120s;
        }
        location = /health { proxy_pass http://127.0.0.1:$backend_port/health; }
        location ~* \\.(json|md|svg|png|ico|js|map)$ { try_files \$uri =404; expires -1; }
        location / { try_files \$uri \$uri/ /index.html; }
    }
}
NGINX
nginx -p "$runtime_dir/" -c "$runtime_dir/nginx.conf" -g 'daemon off;' &
wait $!
