# Development and verification

Use Java 17 to run the Gradle wrapper. Node/Yarn used by the Kotlin build are managed by Gradle. A local Chrome/Chromium executable is needed for browser tests. For example on this workstation:

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export CHROME_BIN=/usr/bin/chromium
./gradlew :backend:test :shared:jvmTest :shared:jsBrowserTest :web:jsBrowserTest :web:jsBrowserProductionWebpack
python3 scripts/check_hint_keys.py
python3 scripts/check_resources.py
node scripts/test_service_worker.cjs
bash -n prod.sh deploy.sh scripts/serve_local.sh
```

The first run may download test dependencies; add `--offline` only after the required dependencies are cached. `scripts/check_syntax.py` is a legacy brace-counting helper, not a compiler or substitute for these tests.

## Running the application

`./dev.sh` starts backend and webpack in tmux within the Nix shell. Webpack proxies `/api` and `/health` to port 8181. `./prod.sh` assembles production assets and starts an isolated foreground nginx through `scripts/serve_local.sh`, rather than a static Python server. Nginx must be installed (included in `shell.nix`). Local production listens on 8081 and proxies the backend on 8181. Both modes support language routes such as `/de/`.

For the deployed stack, use the existing `docker compose up -d --build` workflow. This review did not deploy to a public service.

## Proxy trust and solver capacity

Compose configures `TRUSTED_PROXY_ADDRS=web`, resolving the nginx container's address. Direct backend connections do not trust forwarded headers by default. Nginx overwrites the client-IP headers sent to the backend.

If another trusted reverse proxy terminates TLS upstream, mount a configuration file containing `set_real_ip_from <actual proxy address or CIDR>;` into `/etc/nginx/trusted-proxies/` in the web container. Use only the addresses of proxies under your control. Nginx then resolves `X-Forwarded-For` before forwarding the normalized client IP. Without this operator configuration, clients behind that upstream proxy intentionally share its address/bucket.

Expensive cache-miss solver operations run outside the request dispatcher, with two concurrent slots by default. Configure `SOLVER_CONCURRENCY` (1–16) if measurements justify it. Saturation returns HTTP 503 with `Retry-After`. This bounds concurrent work but does not forcibly interrupt a non-cooperative StormDoku search; hard per-search resource isolation remains a separate operational improvement.

## Icons and releases

`web/src/jsMain/resources/favicon.svg` is the artwork source. Run `python3 scripts/generate_icons.py` with Pillow and `rsvg-convert` installed to regenerate the PNGs, ICO, and separately padded maskable icon. No machine-specific checkout paths are embedded in the generator.

For a release, update `APP_VERSION` in `AppUtils.kt`, the changelog header, the bundle query in `index.html`, and the service worker's cache/versioned resource URLs together. `scripts/check_resources.py` checks consistency. The query change gets returning browsers past older immutable `/web.js` cache entries; nginx and the service worker now revalidate resources.

The response cache moved from `cache-v2.db` to `cache-v3.db` because hint identifiers changed from process-local UUIDs to deterministic match hashes. Old cache data is left intact but is no longer replayed. Saved games retain their existing storage format; new placement history includes exact note state while legacy undo entries remain readable.
