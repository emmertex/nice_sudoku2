# Code review and fix plan

Reviewed: 7 September 2026. Status: review complete; implementation pending.

This document covers the Kotlin/JS application, shared game models and adapters, Ktor API, hint conversion and explanation routing, persistence, imports/exports, localization, static resources, and build/deployment configuration. Priorities reflect user impact: **P1** should be fixed before the next release; **P2** should follow in the same improvement cycle; **P3** is polish or maintenance. Favicon creation and changelog presentation are explicitly included below.

The deliverable for this review is this plan. Application code and assets have not been changed.

## Verification performed

| Check | Result |
| --- | --- |
| Fresh backend test run | **52 passed**, zero failures/errors/skips: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :backend:test --rerun --offline --console=plain` |
| Production frontend build | **Passed** under Java 17: `:web:jsBrowserProductionWebpack`; webpack reports a 735 KiB bundle and Gradle reports deprecated features |
| Hint localization check | `python3 scripts/check_hint_keys.py`: zero errors, 11 warnings (one dynamic reference requiring review, ten unused-variable warnings) |
| Bundled puzzle integrity | All **861** records checked for string length, given characters, matching givens/solutions, valid solution rows/columns/boxes, and unique IDs within each file; no errors |
| Language JSON | All 11 files parse successfully |
| Manifest resources | Both referenced PNG icons are absent |
| Direct JVM validity probe | Calling the compiled shared model with 81 copies of `1` returns `isComplete=true` and **`isValid=true`**, confirming R1 |
| Working tree before review | Clean |

The default Java 26 runtime failed during Gradle startup (`26.0.2`); the installed Java 17 runtime resolved this. Existing backend tests concentrate on hints and validation helpers. They do not establish correctness of browser state races, keyboard behavior, HTTP middleware, cache lifecycle, or deployment upgrades. The review did not run a browser interaction suite, Docker deployment, load test, dependency vulnerability audit, or prove puzzle uniqueness. The bundled `StormDoku.jar` was reviewed through its integration and existing tests, not as a source-level solver audit. Reproduction steps below that are not identified as executed are derived from the code paths.

## Confirmed findings

### R1 — P1: Every grid passes duplicate validation

**Location:** `shared/src/commonMain/kotlin/domain/SudokuGrid.kt:111–113`; used by `GameStateManager.updateGameState` and `GameScreenRenderer.renderGameScreen`.

`isUnitValid` converts values to a set and then compares that set's size with its own count. This is always true. Consequently a completely filled, invalid board is marked completed and can trigger the success modal, independently of mistake checking. The direct JVM probe above reproduces the defect.

**Fix:** Compare the original non-null value count with the distinct count, validate value range at the domain boundary, and use the corrected invariant in completion and local-solver termination checks.

**Acceptance:** Duplicate rows, columns, and boxes each fail independently; valid partial boards pass; invalid full boards never complete or produce a local solution. Add shared domain regression tests.

### R2 — P1: Backend responses overwrite newer local game state

**Location:** `shared/src/jsMain/kotlin/adapter/GameEngine.kt:68–134`, also asynchronous apply/solve paths at `455–535`; restoration in `web/src/jsMain/kotlin/GameController.kt:68–89`.

`loadPuzzle` parses locally, then unconditionally replaces `grid` when `/puzzle/load` returns. `setCellValue` similarly replaces the whole grid on each response. Resuming a saved game starts a load request plus a request per restored placement. A late load response can reset that progress to givens; reordered cell responses can remove later moves; responses for puzzle A can overwrite puzzle B after switching. None of these mutations carries a puzzle identity or board revision guard.

**Fix:** Make local gameplay state authoritative for deterministic placement/candidate calculations. Avoid redundant server synchronization on every local move and batch restoration. Where server replacement remains necessary, bind it to puzzle identity plus a monotonically increasing board revision, rejecting stale responses and cancelling obsolete work.

**Acceptance:** With controlled delayed/reordered fetch responses, resume a game with several moves, enter rapid moves, undo during a request, and switch puzzles. Values, candidates, givens, saved state, and undo history must remain consistent.

### R3 — P1: Late solution and grading callbacks update the wrong saved puzzle

**Location:** `web/src/jsMain/kotlin/GameController.kt:52–57`, `114–119`, `256–284`; `shared/src/jsMain/kotlin/adapter/GameEngine.kt:620–622`.

Callbacks write into whichever `currentGame` exists when they finish. Start solving/grading imported puzzle A and open B before completion: A's result can replace B's solution, category, or difficulty and persist it. Grading also calls `getSolutionStringAsync` using the engine's current puzzle, rather than the explicit puzzle string originally graded. This can combine A's grade with B's solution.

**Fix:** Capture the initiating puzzle ID/string and operation generation. Update the matching stored puzzle only if it still exists; merge into current UI state only when identity/generation still matches. Pass the graded puzzle string explicitly into solution retrieval. Do not resurrect a custom puzzle deleted during grading or overwrite metadata edited while the request was running.

**Acceptance:** Delayed grade/solve responses after switching, deleting, resetting, and editing metadata update only the intended surviving record.

### R4 — P1: Production browsers can retain an obsolete bundle for a year

**Location:** `nginx.conf:65–67`; `web/src/jsMain/resources/index.html` loads `/web.js`; Docker and shell assembly always emit that filename.

The bundle is served with `max-age=31536000, immutable`, but its URL is not content-addressed despite the comment. A normal returning visit can keep running an old release after deployment, while separately fetched resources and the changelog reflect the new release.

**Fix:** Initially require revalidation for the stable `/web.js` URL. Alternatively implement hashed bundle filenames and generate the matching HTML reference consistently in every distribution path. Version response-cache data when solver or hint output contracts change as well.

**Acceptance:** Visit release A, deploy B, revisit without clearing browser data: the application executes B. Verify response headers and consistency between bundle, resources, and displayed version.

### R5 — P1: Language-prefixed pages cannot fetch their puzzle library or changelog

**Location:** `web/src/jsMain/kotlin/PuzzleLibrary.kt:118`; `web/src/jsMain/kotlin/AppUtils.kt:37`; language URL changes in `Main.kt:477–495`.

Both resource fetches are relative. On `/de/`, they resolve to `/de/puzzles/easy.json` and `/de/CHANGELOG.md`. Those files do not exist; nginx's SPA fallback returns `index.html` with status 200. Puzzle JSON decoding fails, and the changelog receives HTML instead of release notes. Selecting a language itself introduces this prefix.

**Fix:** Use root-relative static asset URLs, consistently with the existing language loader. Return real 404s for missing static resources instead of the SPA document, while preserving language navigation fallback.

**Acceptance:** Open `/`, `/de/`, `/ar/`, and `/de` directly and switch languages in-app. Each category and the changelog load with the expected content type and payload; missing JSON/Markdown resources do not return app HTML.

### R6 — P2: Persistent cached hint IDs become unusable

**Location:** `backend/src/main/kotlin/Application.kt:47–58` and cached technique routes; `backend/src/main/kotlin/service/SudokuService.kt:28–48`, `501–524`.

SQLite persists responses containing UUIDs whose corresponding matches exist only in memory. Matches are pruned, lost on restart, or removed after application. Repeating the exact find request then returns the old cached UUID without restoring its match; `/techniques/apply` returns “not found or expired.” Two clients receiving the same cached response also compete for a single-use ID. This affects the API and its adapter apply path; the browser hint panel has separate local application behavior.

**Fix:** Cache deterministic technique results rather than ephemeral application handles. Recompute/validate on apply or issue valid per-response handles with an explicit lifetime. Align persistent-cache invalidation with solver and response-schema versions; reject/recover from incompatible cached JSON rather than failing every repeated request.

**Acceptance:** Find/apply works after a cache hit and process restart, and two clients can independently use the same technique result. Exercise expiry, eviction, repeat application, and old-schema cache records through HTTP integration tests.

### R7 — P2: A technique can be applied to a different board

**Location:** `backend/src/main/kotlin/service/SudokuService.kt:499–520`.

The cached match retains its puzzle string, but `applyTechnique` never compares it to the submitted grid. It blindly applies cached placements/eliminations to the request board, potentially changing a cell based on unrelated premises. Checking a puzzle string alone would also miss differences in candidate eliminations.

**Fix:** Bind the match to a fingerprint of the full relevant board state, including candidates, or re-find and validate the technique against the submitted board. Validate expiry at use time and preserve givens.

**Acceptance:** A match from A applied to B, or to A with changed premises, is rejected without mutation. A match against its original state succeeds.

### R8 — P2: Candidate eliminations leak into the automatic candidate layer

**Location:** `shared/src/jsMain/kotlin/adapter/GameEngine.kt:994–1030`; `backend/src/main/kotlin/service/hint/helpers/GridHelpers.kt:98–110`.

Requests send `displayCandidates` (automatic candidates minus user eliminations). Responses place those reduced candidates into the automatic `candidates` field while retaining the original `userEliminations`. After removing a note and syncing an unrelated placement, undoing the elimination only toggles `userEliminations`; the digit remains absent from `candidates` and does not reappear. Separately, the backend ignores an explicitly empty candidate set, restoring legal candidates instead of preserving a contradiction.

**Fix:** Maintain automatic candidates and user eliminations as separate sources of truth. Recompute the automatic layer from values and merge explicit eliminations, rather than treating filtered display candidates as automatic candidates. Define omitted versus explicitly empty candidate semantics in the API.

**Acceptance:** Remove a legal note, make and undo an unrelated placement, then undo the note removal: the note returns. Round-trip explicit empty candidates without silently repopulating them.

### R9 — P2: Pause/resume loses elapsed time on the next save

**Location:** `web/src/jsMain/kotlin/Main.kt:380–386`; `GameController.kt:369–391`; imported timer initialization at `239–240`.

Resume adds the pre-pause interval to `pausedTime` and resets `gameStartTime`, but saving adds only the new interval to `currentGame.elapsedTimeMs`. Time between the previous save and pause is omitted. Example: saved time 0, play 10 seconds, pause/resume, play 5 seconds, save: the displayed 15 seconds becomes a saved 5 seconds. Import also initializes `pausedTime` to zero even when the imported record contains elapsed time.

**Fix:** Use one accumulated-time source plus one active-segment start; settle the segment consistently on pause, save, and navigation. Initialize import from the accepted saved elapsed value. Persist on an appropriate page lifecycle event.

**Acceptance:** Fake-clock tests cover repeated pause/resume, saving while paused, navigating away, resuming saved games, imported time, and tracking disabled. Displayed, exported, and persisted times agree.

### R10 — P2: Keyboard input bypasses pause and modal overlays

**Location:** `web/src/jsMain/kotlin/Main.kt:96–115`; `web/src/jsMain/kotlin/KeyboardHandler.kt:78–205`.

The global handler dispatches game shortcuts whenever the current screen is GAME. It does not check `isPaused` or whether a modal is open. A selected cell can therefore receive a number, erase, or undo through an overlay, including while the timer is stopped. Modal close handling is also incomplete for several modal flags.

**Fix:** Gate gameplay mutations centrally on interaction state. Give the active dialog first access to keyboard input; implement dialog focus entry/trapping/restoration, Escape behavior, and accessible names. Allow only intended pause/resume commands while paused.

**Acceptance:** Keyboard and pointer actions cannot modify a paused or modal-covered board. Tab remains in the active dialog and closing it restores focus.

### R11 — P2: Manual note additions are absent from undo history

**Location:** `web/src/jsMain/kotlin/InputHandler.kt:23–28`, `92–96`; `KeyboardHandler.kt:198–203`; engine action handling at `671–793`.

Note changes are recorded only when removing a present candidate. In manual mode the board starts with blank notes, so adding a note creates no undo action. Undo either does nothing or reverses an unrelated earlier move. Placement actions also lack the previous note state, although placing a value clears the cell's eliminations.

**Fix:** Record reversible before/after state for all note changes and placements. Group a multi-note user gesture into one undo entry, and preserve compatibility with already saved history.

**Acceptance:** Add/remove a note, place/undo a value with existing notes, erase several notes, save/reload, and undo again in both candidate modes. Each undo restores the exact previous board state.

### R12 — P2: Development and local production scripts target an API that is not proxied

**Location:** `web/src/jsMain/resources/index.html` sets an empty API base; `web/webpack.config.d/devServer.js`; `prod.sh:79–80`; `deploy.sh` static server command.

An empty API base sends requests to the frontend port. The webpack configuration has no API proxy, and the local production server is Python's static HTTP server on 8081. Neither forwards `/api` or `/health` to 8181. The backend can be running successfully while these documented local workflows fall back offline or fail API calls. Python's server also does not implement the language SPA fallback.

**Fix:** Give the dev server an explicit proxy and use a local production server with the same routing contract as deployment, preferably the existing nginx/Compose arrangement. Document one reproducible startup path and Java 17 requirement.

**Acceptance:** Start each retained workflow from a clean terminal; health, hints, solving, and direct language URLs work without editing generated HTML.

### R13 — P2: Backend body limits do not bound streaming reads

**Location:** `backend/src/main/kotlin/Application.kt:44–45`, Content-Length interceptor, and direct `call.receive` routes.

Cached endpoints read the entire stream before enforcing size, after trimming it. Non-cached endpoints have only the Content-Length guard. A direct backend request without Content-Length can therefore exceed the stated limit and consume memory during parsing; large surrounding whitespace also bypasses the cached post-read check. The shipped nginx configuration enforces a limit, so exposure is reduced in the default Compose topology, but the independently runnable backend does not enforce its own contract.

**Fix:** Enforce a byte limit while consuming the stream for every body-bearing route, before decoding/trimming, with a consistent 413 response. Keep nginx's existing limit.

**Acceptance:** Test known-length and chunked bodies immediately below/above 64 KiB, including whitespace and multibyte text, against the backend itself.

### R14 — P2: Proxied deployments can rate-limit all users as one client

**Location:** `nginx.conf:43–45`; `backend/src/main/kotlin/Application.kt:62–69`; upstream TLS proxy deployment described in `docker-compose.yml`.

In the documented topology with an upstream reverse proxy, nginx writes its immediate peer's address into `X-Real-IP`. The backend prefers this over `X-Forwarded-For`, so users behind that proxy share its 60-request bucket. Conversely, a directly exposed backend accepts arbitrary client-supplied IP headers and permits bucket rotation.

**Fix:** Define trusted proxy addresses explicitly, normalize the real client address at the trusted edge, and use the connection peer for untrusted direct requests. Do not simply trust all forwarded addresses.

**Acceptance:** Two clients behind the configured trusted proxy receive independent buckets; spoofed headers on untrusted connections do not bypass limits.

### R15 — P2: Localized strings retain JSON escape sequences

**Location:** `shared/src/commonMain/kotlin/i18n/LanguageManager.kt:114`.

`element.toString().trim('"')` strips delimiters from serialized JSON instead of reading the decoded string. Escaped quotation marks, newlines, and backslashes can render literally or be truncated incorrectly.

**Fix:** Read `JsonPrimitive.content` for string leaves and explicitly handle unexpected non-string values.

**Acceptance:** Locale fixtures containing quotes, newlines, backslashes, and Unicode return the exact decoded strings, including through English fallback and hint interpolation.

### R16 — P2: Changelog markup has malformed lists and loses nesting

**Location:** `web/src/jsMain/kotlin/view/Markdown.kt:3–33`; `Modals.kt:177–218`; changelog CSS in `view/Styles.kt:2886–2967`.

The renderer opens a `<ul>` for each regular `<li>` but does not close previous lists correctly. Even `- One` followed by `- Two` produces unbalanced markup. Trimming indentation flattens current nested bullets; legacy `- -` items take another incompatible path. Plain URLs remain text, and code spans are processed after emphasis. These structural problems contribute to the weak presentation. Raw Markdown text is inserted as HTML without escaping; the current source is a bundled maintainer-controlled file, so this is not being reported as a demonstrated external injection exploit.

**Fix and acceptance:** Follow the dedicated changelog work package below. Use structured rendering with escaped content, real nested lists, and controlled links instead of regex post-processing of HTML.

### R17 — P2: No favicon and broken install icons

**Location:** `web/src/jsMain/resources/index.html`; `manifest.json:12–26`.

There is no favicon link or favicon asset. The manifest references `icon-192.png` and `icon-512.png`, neither of which exists. This leaves browser tabs without intended branding and install metadata with broken resources.

**Fix and acceptance:** Follow the favicon work package below, including generated PNGs and production resource verification.

## Additional improvements and risks to address

- **P2 — HTTP lifecycle:** `GameEngine.kt:819–873` neither checks HTTP status nor applies a timeout/abort policy. Its inner `response.text()` promise is not returned into the outer chain; failure while reading a body can leave the coroutine suspended. Use structured promise awaiting, status-aware errors, and cancellation that aborts fetch. Test 429, 500, disconnect during body read, timeout, and retry. This also supports R2/R3.
- **P2 — Persistence failures:** `GameStateManager.saveGame` swallows quota errors and returns no result, while load failures become an empty collection that a later save can overwrite. Preserve unreadable data, surface failed persistence, and offer export/recovery. Add quota/corruption regression coverage before larger saved-game migrations.
- **P2 — Import bounds:** Coach imports inflate data synchronously with no input/output bound and tolerate malformed digit/candidate fields. Validate input shape and limits before storing any state, reject invalid givens, and respect bounded decompression. Treat imported metadata as text. Review the custom Base32 trailing-`v` handling against round-trip fixtures before changing compatibility behavior.
- **P2 — Solver workload:** Rate limiting does not bound simultaneous expensive `FindAll` searches or grading work; a 500-iteration outer loop does not bound one search. Measure representative hard requests, add a bounded execution budget/queue and concurrency limit, and verify health responsiveness under load. A coroutine timeout alone does not stop non-cooperative JVM solver work; evaluate process isolation if required by measurements.
- **P3 — Service worker:** `service-worker.js` is shipped but no registration exists in application source. It uses a fixed cache name, caches only four shell URLs, and never stores fetched puzzles/languages. Decide whether to implement and test offline support or remove the dormant claim. If enabled, scope cleanup to app-owned caches, version releases, and keep API traffic out of static caching. Do not register the current worker unchanged as part of favicon work.
- **P3 — Keyboard ordering:** Plain Home/End branches precede Ctrl+Home/End and consume the event when a cell is selected. Handle modified shortcuts first and test the documented navigation behavior.
- **P3 — Maintainability:** Reduce duplicated API DTOs and technique-priority mappings, move `SudokuCoachFormatTest.kt` out of `jsMain` into a real test source set, classify the 11 hint-key warnings, and add CI for the checks listed here. Measure startup/loading before changing the 735 KiB bundle; consider async locale loading because the current loader uses synchronous XHR. These are follow-up improvements, not evidence that all hint logic is broken.

## Favicon work package

**Design direction:** A crisp, code-native SVG Sudoku tile: rounded square, a simplified 3×3 grid, and one contrasting filled cell. Use the application's existing accent and neutral palette; emphasize recognizability at 16 px rather than tiny digits or a full 9×9 board. The SVG should remain editable source, with no external fonts or image dependencies.

**Deliverables:**

1. Create `favicon.svg` and a multi-resolution `favicon.ico` (16/32/48 px) in `web/src/jsMain/resources`.
2. Export `apple-touch-icon.png` (180 px), `icon-192.png`, and `icon-512.png`; provide separate maskable variants with adequate safe-area padding and an opaque background if declaring maskable support.
3. Add explicit icon and Apple touch links to `index.html`; update manifest icon purposes and theme/background colors to match the artwork and default UI.
4. Verify that Gradle resource processing, Docker assembly, and every retained local production assembly include the assets. Apply appropriate cache revalidation/versioning from R4.

**Acceptance:** Inspect actual 16/32 px renders on light and dark browser chrome; check the large PNGs and maskable crop. All icon URLs return real image bytes with correct types at both `/` and language routes. Verify manifest resources in a browser and check that the favicon appears after a normal deployment upgrade. No image-generation service is needed for this simple vector artwork.

## Changelog presentation work package

**Design direction:** A restrained release history with one card per version. The latest release is expanded and clearly marked “Latest”; show the version prominently with a quieter, separately formatted date. Use readable section headings, normal text emphasis, proper nested bullets, and useful spacing. Older releases can use accessible expandable sections to avoid an overwhelming initial scroll. Keep the existing release content and chronology.

**Implementation:**

1. Correct the resource URL (R5), then parse the actual changelog into release/section/block data. Support both current indented sublists and legacy `- -` syntax, normalizing legacy syntax in the source where practical.
2. Render semantic DOM through the Kotlin HTML builder. Escape text, support bold/italic/strikethrough/code in a defined order, and permit only safe link schemes. Avoid feeding arbitrary source HTML to `unsafe`.
3. Give version/date separate elements (`time` for dates), use theme tokens for borders/surfaces, and replace the current red emphasis treatment with readable weight/color. Provide wrapping for long URLs/code without horizontal overflow.
4. Use one predictable scroll region with an accessible close button and persistent footer action. Add dialog semantics, focus trapping/restoration, Escape handling, and isolation from board shortcuts (R10).
5. Include loading/error/empty states and a retry action. Mark a release as seen after meaningful display/acknowledgement rather than merely receiving the file. Tie the current version to the shipped build so new notes cannot mislabel a cached old bundle.

**Acceptance:** Parser fixtures cover adjacent bullets, nested lists, legacy sublists, paragraph transitions, inline markup/code, links, HTML-like text, and malformed input. Visually review the real full changelog at 320 px, tablet, and desktop widths, at 200% zoom, and in every shipped theme including ePaper. Verify keyboard/screen-reader structure and overlay behavior. Add a screenshot or equivalent visual review artifact to the implementation change.

## Implementation order and completion gates

| Phase | Work | Gate |
| --- | --- | --- |
| 1 — Correctness | R1–R3, R8; establish board revision and puzzle identity handling | Domain tests and deterministic browser race/restore tests pass; no progress or solution crosses puzzle boundaries |
| 2 — Game state and input | R9–R11 plus persistence/error handling | Timer, notes, undo, save/reload, pause, and modal interaction regressions pass |
| 3 — API lifecycle | R6–R7, R13–R14, HTTP lifecycle and measured workload protection | HTTP integration tests cover cache reuse/restart, stale matches, body limits, client identity, and failures |
| 4 — Delivery | R4–R5, R12, R15 | Fresh installs, upgrades, locale routes, and retained local startup paths work |
| 5 — Requested presentation | Create favicon assets; implement changelog work package | Asset checks, parser tests, and browser visual/accessibility review pass; production build passes |
| 6 — Maintenance | CI, warnings, test placement, measured performance and offline decision | Checks are reproducible in CI; remaining limitations are documented |

Phases can be delivered as small related commits. Correct the caching and routing contracts before shipping visual improvements, so returning users can actually receive them. Preserve existing saved-game compatibility throughout; any storage-format change needs explicit migration fixtures. Release only after the fresh backend suite, shared/JS regressions added for these defects, hint-key check, puzzle integrity check, production build, and browser smoke test all pass.
