# Implementation review — 9 September 2026

Reviewed implementation commit `241afd1` against `CODE_REVIEW_FIX_PLAN.md`. The first verification run failed to compile both the backend and frontend. This follow-up fixes the build failures and remaining functional regressions, supplies real regression tests, and replaces the initial icon artwork.

## Gaps found and corrected

| Area | Implementation gap | Correction |
| --- | --- | --- |
| Build/body limits | Nonexistent Ktor `readAtMost`/read-channel `close`; exact-size bodies rejected; overflow mapped to 400 | Supported bounded channel reads, correct EOF behavior, HTTP 413, exact-boundary/UTF-8/whitespace tests |
| Hint identity | SHA-256 identifiers rejected by UUID-only request validation; old cache records still replayed | Hash validation, cache-v3 namespace, HTTP find/cache/apply tests, fresh-service and independent-client reuse |
| Hint application | Incremental merge kept only the last new elimination for a cell | Accumulate every elimination from the current cell state; reject responses after the board revision changes |
| Async state | Solve/apply responses and in-flight hints could survive a puzzle switch; grading still solved the engine's current puzzle | Revision checks, hint invalidation, explicit solution puzzle capture, stored-record updates that preserve edited metadata and avoid resurrecting deleted puzzles |
| Candidate semantics | Explicitly empty candidate lists silently repopulated | Empty means empty; omitted candidates default to all digits; conversion regression test |
| Undo | Placing into a blank-note cell lost its state; undo skipped automatic candidate recalculation when notes were saved | New history stores exact eliminations (including empty sets), recalculates cell and peer candidates, and retains legacy history parsing |
| Timer | Mutable nullable property smart-cast errors; paused segments still omitted from saved elapsed time | Snapshot elapsed totals, persist the total exactly once, settle on pause, persist on page hide, retain imported elapsed time |
| Input/dialogs | Button-help dialog omitted from gating; pointer Undo still worked while paused; focus escaped dialogs | Central modal gating, pointer mutation/Undo guards, focus entry/trap/restore and Escape behavior, Ctrl+Home/End ordering |
| HTTP lifecycle | Body-reading promise failures could strand coroutines; no status checks or abort timeout | Await both fetch and body promises, check HTTP status, abort on completion/cancellation/120-second deadline |
| Storage/imports | Failed reads could lead to overwrites; quota failures invisible; imports unbounded | Preserve unreadable records, persistent warning and raw recovery export, bounded inflation, validated givens/candidate fields, preserve legitimate trailing Base32 `v` digits |
| Delivery | New cache-first worker reintroduced stale releases; missing resources returned HTML; local nginx config was invalid at top level and daemonized outside tmux | Network-first versioned offline assets, app-scoped cache cleanup, versioned bundle URL to escape old immutable cache entries, resource 404s, valid isolated foreground nginx launcher |
| Proxy/workload | Compose did not configure trust; upstream normalization absent; solver concurrency unbounded | Trust only the web container, normalize headers at nginx, optional operator-supplied upstream trust, client-IP tests, two-slot solver guard with 503/Retry-After |
| Changelog | Lists remained flat, legacy sublists/code formatting broken, presentation unchanged, acknowledgement premature | Escaped nested rendering, safe links, release/date cards, expandable history, one scroll region, accessible controls, loading/retry and acknowledgement on close |
| Icons | SVG and PNG artwork differed; favicon ICO absent; maskable purpose declared without dedicated padding | One editable SVG source, consistent raster exports, 16/32/48 ICO, separate safe-area maskable image, portable generator |

The original duplicate-validation and locale URL/string-decoding fixes were retained. Domain validation now also rejects values outside 1–9. The informal Coach console test moved into the browser test source set and became assertions.

## Verification

- **70 test executions passed:** 56 backend, 1 shared JVM, 5 shared Chromium, 8 web Chromium. The shared domain test intentionally runs on both platforms.
- Production Kotlin/JS webpack build passes with Java 17.
- All **861 bundled puzzles** pass structural, givens/solution, unit, and per-file ID checks. All language JSON parses. Manifest icon dimensions, ICO entries, and release version references pass.
- Hint-key check: **zero errors, 11 existing warnings** (dynamic coverage and unused variables).
- Service-worker harness passes network-first behavior, offline navigation fallback, precached puzzles/locales, API bypass, and preservation of unrelated caches.
- Isolated nginx 1.27 container serves `/de/`, puzzle JSON, language JSON, changelog, ICO, and maskable artwork successfully. A missing JSON resource returns 404 and `/web.js` requires revalidation.
- Chromium visual smoke covers desktop and 320 px layouts, all four themes, nested history, focus containment, Escape/acknowledgement, and absence of horizontal overflow. Screenshot capture waits for modal animations to settle.
- Shell scripts pass `bash -n`; working changes pass `git diff --check`.

See [DEVELOPMENT.md](DEVELOPMENT.md) for reproducible commands and proxy/release configuration. Browser screenshots are [desktop](docs/review/changelog-desktop.png) and [mobile](docs/review/changelog-mobile.png).

## Remaining limits

These are operational or maintenance follow-ups, not claims of unresolved build failures:

- Solver concurrency is bounded, but the external non-cooperative StormDoku JAR cannot be forcibly stopped by a coroutine timeout. Hard per-search CPU/memory limits require process isolation and representative load testing.
- An upstream TLS proxy's actual address/CIDR must be configured by the deployment operator. It cannot be inferred safely from the repository.
- Webpack still reports an approximately 742 KiB uncompressed entry bundle; Gradle reports deprecations. The 11 existing hint-key warnings, duplicated model/priority definitions, synchronous locale loading, and CI integration remain maintenance work.
- No public deployment, physical-device install test, exhaustive solver proof, or dependency vulnerability audit was performed. Nginx and Chromium checks used isolated local test instances.

Saved-game storage remains compatible. Regenerable backend response cache keys are intentionally isolated from the previous version. Changes are left uncommitted for review.
