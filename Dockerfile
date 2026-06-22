# syntax=docker/dockerfile:1

##############################################################################
# Builder — compiles the Ktor backend distribution and the Kotlin/JS web bundle
##############################################################################
FROM gradle:8.14-jdk17 AS builder
WORKDIR /app

# Copy the whole project (see .dockerignore for what is excluded).
COPY . .

# Build both artifacts in a single Gradle invocation so the configuration
# cache / dependency downloads are shared between them.
#   - :backend:installDist            -> build/install/backend  (bin/ + lib/)
#   - :web:jsBrowserProductionWebpack -> minified web.js bundle
RUN gradle --no-daemon --console=plain \
        :backend:installDist \
        :web:jsBrowserProductionWebpack

# Assemble the static web distribution exactly like prod.sh does: processed
# resources (index.html, manifest, puzzles, languages, CHANGELOG) + the bundle.
RUN mkdir -p /dist \
    && cp -r web/build/processedResources/js/main/. /dist/ \
    && cp web/build/kotlin-webpack/js/productionExecutable/web.js /dist/ \
    && (cp web/build/kotlin-webpack/js/productionExecutable/web.js.map /dist/ || true)

##############################################################################
# Backend runtime — JRE running the Netty server
##############################################################################
FROM eclipse-temurin:17-jre AS backend
WORKDIR /app

COPY --from=builder /app/backend/build/install/backend/ /app/

# SQLite response cache lives here (CacheDatabase.DEFAULT_DB_PATH = ./data/...).
RUN mkdir -p /app/data
VOLUME ["/app/data"]

ENV PORT=8181
EXPOSE 8181

# The generated launcher runs from WORKDIR, so ./data resolves to /app/data.
ENTRYPOINT ["/app/bin/backend"]

##############################################################################
# Web runtime — nginx serves the static bundle and reverse-proxies the API
##############################################################################
FROM nginx:1.27-alpine AS web
COPY --from=builder /dist/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
