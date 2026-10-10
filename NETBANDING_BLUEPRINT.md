# NETBANDING — Project Blueprint (v2)

Native Android app that aggregates and compares internet package prices in Indonesia.
Data comes from web scraping + LLM extraction (ISPs offer no public APIs). Everything runs at ~$0/month.

---

## 0. How to use this document

- **Humans:** use Section 9 (Roadmap) as the working checklist.
- **AI agents:** read Sections 1-5 and 12 before every task. Section 4 (Data Contract) is law: if code and contract disagree, the contract wins, or the contract is updated first.
- Language convention: **docs, code, comments, commits in English. User-facing app strings in Indonesian** (via `strings.xml`).

---

## 1. Product definition

### 1.1 Scope (MVP)
| Item | Decision |
|---|---|
| Platform | Android 8.0+ (API 26+), optimized for 2-4 GB RAM devices |
| Geography | **Java island only** (6 provinces, see 4.4) |
| Category | **Broadband (home internet) only** for MVP. Cellular = v1.1 |
| Price | Free app, no ads, no tracking, no accounts |
| Connectivity | **Offline-first**: 100% usable with no signal after first launch |
| Languages | Indonesian UI |

### 1.2 Non-goals (MVP)
Cellular data packages, user accounts, reviews, push notifications (FCM), in-app purchase/checkout, coverage-by-address lookup, ads/affiliate.

### 1.3 Success criteria
- Cold start < 2 s on a 2 GB device; AAB < 8 MB.
- App works fully in airplane mode after first launch (seed data bundled).
- Weekly pipeline runs unattended; bad extractions never reach users silently.
- Monthly infra cost ≈ $0 (LLM extraction runs on the Agnes AI free tier; DeepSeek is a paid fallback only).

---

## 2. Decision log

| # | Decision | Status |
|---|---|---|
| D1 | Java island only; region granularity = province | Locked |
| D2 | Broadband first, cellular in v1.1 | Locked |
| D3 | Free, no monetization, no analytics | Locked |
| D4 | **Backend = static JSON files on GitHub Pages** (replaces Supabase) | **Recommended, confirm** |
| D5 | LLM extraction via a provider chain (Agnes AI primary, DeepSeek fallback), only when page content changed | Locked (amended 2026-10-11: DeepSeek credit exhausted; Agnes AI added as primary, DeepSeek kept as fallback) |
| D6 | Retrofit + OkHttp + kotlinx.serialization, manual DI (template default, replaces Hilt for cold start), Room, Compose M3 | Locked (amended 2026-10-07: Hilt dropped for <2s start on 2 GB devices) |
| D7 | ISP logos bundled as local vector/webp assets, no Coil | Locked |
| D8 | Money stored as `Long` IDR (never Float/Double) | Locked |
| D9 | Filtering/sorting done in Room SQL, not in memory | Locked |

### Why D4 (static JSON instead of Supabase)
- Dataset is tiny (a few hundred rows, well under 200 KB gzipped) and fully public/read-only.
- Supabase free projects can pause after a period of inactivity, need RLS + keys, and add a server to babysit. Static files have none of that.
- Git history = free audit trail of every price change. Pull requests = free "staging/review" step.
- Fallback: if you later need server features (user data, alerts via server), migrate to Supabase. The data contract (Section 4) stays identical, only the transport changes (see Appendix A).

---

## 3. Architecture

```
[PIPELINE: GitHub Actions, weekly cron, public repo]
  sources.yaml (ISP pages)
    -> fetch (httpx; Playwright only if JS-rendered)
    -> clean to text + content hash  (unchanged hash = skip, no LLM call)
    -> LLM extraction, provider chain Agnes -> DeepSeek (temperature 0, strict JSON)
    -> Pydantic validation + sanity rules
    -> diff vs data/catalog.json
    -> small/safe change: auto-commit   |   suspicious change: open PR for manual review
    -> data/ published via GitHub Pages (HTTPS, CDN, gzip, ETag)

[ANDROID APP]
  assets/seed (bundled snapshot of data/)  -> Room on first launch
  GitHub Pages manifest.json -> (version changed?) -> catalog.json / history.json
    -> Retrofit/OkHttp -> SyncRepository -> Room (SINGLE SOURCE OF TRUTH)
    -> DAO Flow -> ViewModel (UiState) -> Compose UI
```

Repo layout (monorepo, **public** so Actions + Pages are free):

```
netbanding/
  app/                  # Android project
  pipeline/             # Python scraper
    sources.yaml
    scraper/ (fetch.py, clean.py, extract.py, schema.py, validate.py, diff.py, publish.py)
    tests/fixtures/     # saved HTML snapshot per ISP
  data/                 # published output (served by GitHub Pages)
    manifest.json
    catalog.json
    history.json
  docs/                 # privacy policy, this blueprint
  .github/workflows/    # scrape.yml, pages.yml, app-ci.yml
```

---

## 4. Data contract (source of truth between pipeline and app)

### 4.1 `manifest.json`
```json
{
  "schema_version": 1,
  "data_version": 42,
  "generated_at": "2026-10-05T02:00:00Z",
  "ppn_rate": 0.11,
  "files": {
    "catalog": { "path": "catalog.json", "sha256": "…", "bytes": 48213 },
    "history": { "path": "history.json", "sha256": "…", "bytes": 9120 }
  }
}
```
- `data_version`: integer, +1 on every publish that changes data.
- `ppn_rate` lives here so a tax change needs **no app update**.
- App supports a range of `schema_version`. If the server's version is higher than the app supports, keep old data and show an "Update the app" banner.

### 4.2 `catalog.json` (illustrative values)
```json
{
  "isps": [
    { "id": "biznet", "name": "Biznet Home", "category": "broadband",
      "logo_asset": "ic_isp_biznet", "website_url": "https://…" }
  ],
  "packages": [
    {
      "id": "biznet-home-100",
      "isp_id": "biznet",
      "name": "Home 100 Mbps",
      "type": "broadband",
      "speed_mbps": 100,
      "quota_mb": null,
      "validity_days": null,
      "contract_months": null,
      "base_price": 375000,
      "tax_inclusive": false,
      "device_rental_fee": 0,
      "install_fee": 0,
      "fup_note": null,
      "promo_note": null,
      "regions": ["JAVA_ALL"],
      "source_url": "https://…",
      "is_active": true,
      "last_verified_at": "2026-10-05T02:00:00Z",
      "updated_at": "2026-09-28T02:00:00Z"
    }
  ]
}
```

### 4.3 Field rules
| Field | Rule |
|---|---|
| `id` | **Deterministic slug** (`{isp}-{product}-{speed}`), never random. History is keyed on it. If an ISP renames a plan, keep the id when it's clearly the same plan. |
| `type` | `broadband` or `cellular` |
| `speed_mbps` | Int, nullable (cellular may not state it) |
| `quota_mb` | Int in **MB**, `null` = unlimited / not applicable |
| `base_price` | IDR as the ISP lists it (may be pre- or post-tax, see `tax_inclusive`) |
| `tax_inclusive` | **Required boolean.** If the LLM cannot determine it, extraction FAILS and goes to review. Never guess. |
| `device_rental_fee` | IDR/month, `0` if none |
| `install_fee` | IDR one-time. `0` = free installation. `null` = not stated (UI shows "Tanya provider") |
| `regions` | Region codes (4.4). `["JAVA_ALL"]` = all six Java provinces |
| `is_active` | `false` after the plan is missing for 3 consecutive runs (not after 1) |
| `last_verified_at` | Last time the pipeline successfully confirmed this package on the ISP's page |

### 4.4 Regions (MVP)
`ID-JK` DKI Jakarta, `ID-BT` Banten, `ID-JB` Jawa Barat, `ID-JT` Jawa Tengah, `ID-YO` DI Yogyakarta, `ID-JI` Jawa Timur. Alias `JAVA_ALL` expands to all six.
User picks a province during onboarding (changeable in settings). If an ISP page doesn't specify regions, default to `JAVA_ALL` and set `promo_note` only if the page mentions area limits.

### 4.5 `history.json`
```json
{ "biznet-home-100": [
    { "price": 350000, "tax_inclusive": false, "recorded_at": "2026-07-01T02:00:00Z" },
    { "price": 375000, "tax_inclusive": false, "recorded_at": "2026-09-28T02:00:00Z" } ] }
```
Append **only when `base_price` or `tax_inclusive` changes**. The first scrape writes the initial point. The chart is a step line.

### 4.6 Pipeline-only data (NOT shipped to the app)
`pipeline/runs/YYYY-MM-DD.json`: per-ISP status, tokens used, changed rows, and the LLM's **evidence quotes** (e.g. the exact text that proved "belum termasuk PPN"). Used for debugging and review PRs.

---

## 5. Business logic: True Cost

Single implementation in Kotlin (`CalculateTrueCost` use case) + mirrored in Python tests. Use `ppn_rate` from the manifest.

```
taxed_base      = tax_inclusive ? base_price : round(base_price * (1 + ppn_rate))
monthly_total   = taxed_base + device_rental_fee
one_time        = install_fee                      (null -> "unknown")
first_month     = monthly_total + (install_fee ?: 0)
contract_total  = contract_months != null ? monthly_total * contract_months + (install_fee ?: 0) : null
price_per_mbps  = monthly_total / speed_mbps       (only if speed known)
```

- **Assumption (verify per ISP):** `device_rental_fee` is treated as already final. If some ISP lists rental excluding PPN, add `rental_tax_inclusive` (nullable) to the contract.
- `monthly_total` is **precomputed at import time into a Room column** (using this same function) so budget filters and sorting run in SQL and can be indexed. Recompute all rows whenever `ppn_rate` changes.
- Detail sheet shows: base, PPN line (only if not inclusive), device rental, **monthly total**; then one-time install fee, first-month total, contract total (if known); then source link + "last verified" date + FUP note.
- Always show a disclaimer: *"Harga dapat berbeda. Pastikan ke provider."*

---

## 6. Pipeline spec (Python + GitHub Actions)

### 6.1 Stages
1. **Fetch** each URL in `sources.yaml` (`mode: static | playwright`). Polite: custom User-Agent, 2-5 s delay, retries with backoff. Respect robots.txt and review each ISP's ToS.
2. **Clean**: strip scripts/nav/footer, convert to compact text, compute SHA-256. If hash equals last run's, **skip LLM** (saves cost, avoids drift).
3. **Extract** with the LLM provider chain (Agnes AI primary, DeepSeek fallback; see `extract.py`): `temperature=0`, JSON output, schema from `schema.py`.
4. **Validate** with Pydantic + sanity rules:
   - broadband `base_price` 100,000-2,000,000; cellular `base_price` 5,000-500,000; `speed_mbps` 5-2000 (broadband only); `install_fee` 0-2,000,000
   - `tax_inclusive` must be non-null; `id` must match slug pattern
   - Reject output that includes fields not in the schema
5. **Diff** vs `data/catalog.json`. Mark **needs-review** if: price change > 30%, `tax_inclusive` flipped, speed changed, package count for an ISP drops > 30%, or any validation failure.
6. **Publish**: safe changes -> commit directly to `main`; needs-review -> open a PR (the PR body shows old vs new + evidence quotes). Bump `data_version`, regenerate `manifest.json` hashes, append `history.json` on price changes.
7. **Report**: write `pipeline/runs/…json`. On any failure, GitHub Actions emails you; optionally auto-open a GitHub Issue.

### 6.2 LLM prompt rules (put in `extract.py`)
- Input: cleaned page text + the JSON schema. Output: JSON only.
- "Return `null` for anything not explicitly stated. Never guess or infer."
- Give Indonesian cues explicitly: `belum termasuk PPN` / `exclude PPN` -> `tax_inclusive=false`; `sudah termasuk PPN` / `include PPN` -> `true`; `gratis instalasi` / `free pemasangan` -> `install_fee=0`; `biaya pemasangan Rp X` -> `install_fee=X`; `sewa perangkat/modem/router` -> `device_rental_fee`.
- Require an `evidence` object with short quotes backing `tax_inclusive`, `install_fee`, and `base_price`.
- Extract **only** what's on the page; the slug `id` is generated deterministically in code, not by the LLM.

### 6.3 Resilience rules
- A failed ISP never blocks others (isolate per ISP).
- A failed/empty extraction **keeps the previous data**; it only stops updating `last_verified_at` (the app then shows it as stale).
- Packages are deactivated only after 3 consecutive misses.
- Keep saved HTML fixtures + expected JSON per ISP; run them in CI so prompt/parser changes don't regress.
- Extractor sits behind an interface (`Extractor.extract(text) -> dict`) so the LLM provider can be swapped (or chained with fallback) or replaced by a regex/selector parser for very stable pages.

### 6.4 Candidate ISPs (verify scrapability first)
IndiHome (Telkom), Biznet Home, MyRepublic, First Media, Iconnet (PLN), CBN, Oxygen.id, Megavision. Start with 3 easiest, then expand. Expected size: ~10 ISPs x 3-10 plans = ~50-100 packages.

### 6.5 Workflows
- `scrape.yml`: cron weekly + manual `workflow_dispatch`. Secrets: `AGNES_API_KEY` (primary), `DEEPSEEK_API_KEY` (fallback).
- `pages.yml`: publish `data/` to GitHub Pages on change.
- `app-ci.yml`: build + unit tests on PR.
- Note: GitHub disables scheduled workflows in public repos after ~60 days without repo activity. The weekly data commit normally counts, but set a reminder to check the run history monthly.

---

## 7. Android app spec

### 7.1 Stack (locked)
Kotlin, Jetpack Compose + Material 3, MVVM (single module for MVP, feature packages), Hilt, Room, Retrofit + OkHttp + kotlinx.serialization, Coroutines + Flow, DataStore (preferences), WorkManager, custom Canvas step-line chart (Phase 5 decision: no chart dependency for ≤100 points), Baseline Profile.
**Not used:** Coil (logos are local), Firebase Analytics, ads SDKs, multi-module setup.

### 7.2 Package structure
```
com.netbanding.app
  data/local    (entities, dao, db, mappers)
  data/remote   (api, dto)
  data/repo     (PackageRepository, SyncRepository)
  data/sync     (SyncWorker, SeedImporter)
  domain/model  (Package, Isp, TrueCost)
  domain/usecase(CalculateTrueCost)
  ui/home | ui/detail | ui/history | ui/onboarding | ui/settings | ui/theme
  di/
```

### 7.3 Room schema
- `isps(id PK, name, category, logo_asset, website_url)`
- `packages(id PK, isp_id FK idx, type idx, name, speed_mbps, quota_mb, validity_days, contract_months, base_price, tax_inclusive, device_rental_fee, install_fee, fup_note, promo_note, source_url, is_active, last_verified_at, updated_at, monthly_total idx)`
- `package_regions(package_id FK, region_code idx)` composite PK
- `price_history(package_id FK idx, price, tax_inclusive, recorded_at)`
- `favorites(package_id PK, added_at)`  **user data, never touched by sync**
- DataStore: `selected_region`, `last_data_version`, `last_check_at`, `last_generated_at`, `selected_tab`

### 7.4 Sync engine (simple full-replace; dataset is tiny)
1. **First launch:** `SeedImporter` loads `assets/seed/*` into Room (blocking splash <500 ms or lazy). The seed is a snapshot of `data/`, regenerated by a Gradle/CI step before each release.
2. **Check:** GET `manifest.json` (OkHttp handles ETag/gzip). If `data_version` <= stored -> done.
3. **Download** only files whose `sha256` changed; verify hash.
4. **Apply in ONE Room transaction:** upsert isps/packages/regions/history, delete rows missing from the new payload, recompute `monthly_total`. On any error, roll back (old data stays).
5. **Triggers:** app start (if last check > 24 h), pull-to-refresh, WorkManager periodic (weekly, network required).
6. **Errors:** never replace the screen with an error if Room has data. Show a small banner/snackbar ("Gagal memperbarui, menampilkan data terakhir").
7. **Stale warning:** if `generated_at` is older than 14 days, show "Data mungkin sudah usang".

### 7.5 UI state model
- Because the UI reads Room, **`Loading` only appears before the first DB emission, `Error` only when there is no data at all.** Sync status is a separate `StateFlow<SyncStatus>` (Idle, Syncing, Failed(reason), Stale).
- `HomeUiState`: `Loading | Success(items, filters, sort, region) | Empty(reason) | Error`.
- Filter + sort changes -> new SQL query via `flatMapLatest`.

### 7.6 Screens (MVP)
1. **Onboarding:** pick province (one screen, skippable -> defaults to JAVA_ALL view).
2. **Home:** type tab (Broadband active; Cellular tab shows "Segera hadir" or is hidden until v1.1), filter chips (budget on `monthly_total`: <300rb, <500rb, custom; min speed: 50/100 Mbps; ISP multi-select), sort (cheapest, best price/Mbps, fastest), search, list of `PackageCard`, last-updated line, pull-to-refresh.
3. **Detail (BottomSheet):** True Cost breakdown (Section 5), FUP/promo notes, "Buka situs provider" button, favorite toggle.
4. **Price history:** step-line chart + empty state ("Belum cukup data riwayat").
5. **Settings:** region, data version/last updated, privacy policy, about/disclaimer.
Later: compare 2-3 packages, favorites list screen.

### 7.7 Performance (realistic for low-end)
With a few hundred rows, list scrolling is rarely the bottleneck. The real low-end costs are **cold start, APK size, and memory**. So:
- Baseline Profile + R8 + resource shrinking; target AAB < 8 MB
- `LazyColumn` with `key` + `contentType`; `@Immutable` UI models
- Room indexes: `isp_id`, `type`, `monthly_total`, `region_code`
- Hilt initialization kept light; no heavy libs; defer WorkManager scheduling off the startup path
- Logos: VectorDrawable/WebP, ~10-20 small assets
- `derivedStateOf`/`remember` only where measured to help
- Test on a real or emulated 2 GB device (API 26 and a recent API)

---

## 8. Cost model

| Item | Cost |
|---|---|
| GitHub (public repo): Actions, Pages, storage | $0 |
| LLM API (hash-skip; ~100 pages/week) | $0 on Agnes AI free tier; DeepSeek fallback = cents/month (check current pricing) |
| Hosting/CDN | $0 (GitHub Pages) |
| Crash reporting | $0 (Firebase Crashlytics) or skip; prefer Play Console's built-in crash reports (no extra SDK) |
| Domain | Not needed (use `github.io` URL) |
| Google Play developer account | One-time ~$25 (verify current fee) |

Cost levers: hash-skip, only extract changed pages, strip page text before sending, use selector/regex parsers for stable sites, keep model swappable.
If you want to avoid even the Play fee at first: distribute APK via GitHub Releases for early testers.

---

## 9. Roadmap with acceptance criteria

> Rule: the app and pipeline are built **in parallel against the data contract**, not sequentially. Phase 4 can start any time after Phase 0.

### Phase 0: Contract & skeleton (S, 1-2 days)
- Finalize Section 4 (JSON contract). Create repo layout. Hand-write **20-30 real packages** from 3-5 ISPs as seed `catalog.json` + `manifest.json`. Set up GitHub Pages.
- **Done when:** the seed JSON validates against the Pydantic schema and is reachable at the Pages URL.

### Phase 1: Android data layer (M)
- Project init (Compose + M3, minSdk 26), Hilt, Room entities/DAO/DB, `SeedImporter`, `CalculateTrueCost` + unit tests, `PackageRepository` exposing Flow.
- **Done when:** app lists seeded packages with no network; True Cost unit tests pass (tax-inclusive, exclusive, rental, null install).

### Phase 2: Android UI (M)
- `HomeViewModel` (+UiState), `PackageCard`, filter chips, sort, search, onboarding region picker, detail BottomSheet, empty/error states, Indonesian strings.
- **Done when:** all filters/sorts run via SQL; smooth on a 2 GB device; rotation/process death don't lose filters.

### Phase 3: Network sync (M)
- Retrofit service, `SyncRepository` (manifest -> hash check -> transactional replace), `SyncWorker` weekly, pull-to-refresh, last-updated + stale banner, schema_version guard.
- **Done when:** airplane mode works; editing the Pages data and bumping `data_version` updates the app; a corrupted/partial download leaves old data intact.

### Phase 4: Pipeline automation (L, parallel from Phase 0/1)
- Fetch/clean/extract/validate/diff/publish, fixtures + tests, per-ISP config, review-PR flow, run logs, cron workflow. Start with 3 ISPs.
- **Done when:** one cron run updates `data/` with no manual edits; a simulated 40% price change opens a review PR instead of auto-publishing; a broken ISP page doesn't break the run.
- **Start recording price history here** so Phase 5 has data.

### Phase 5: History, hardening, release prep (M)
- Price-history chart (step line; Vico or custom Canvas), Baseline Profile, R8, accessibility pass, privacy policy page, disclaimer, Play Store listing, data-safety form (no data collected), signed release build.
- Release checklist: new personal Play accounts have had a closed-testing requirement (a number of testers for ~2 weeks); **verify current rules** and recruit testers early.
- **Done when:** release AAB < 8 MB, cold start < 2 s on test device, no crashes in closed test.

### Phase 6: v1.1
- Cellular (prices include PPN so `tax_inclusive=true`, but still extract it; note some cellular prices can vary by area), compare 2-3 packages, favorites screen, **local price-drop notifications** (computed on-device during sync for favorited packages, no FCM, $0), expand to more regions/islands.

---

## 10. Testing & QA
- **Kotlin unit tests:** TrueCost, filters/sort queries (in-memory Room), sync transaction (success, rollback), manifest/schema-version handling.
- **Python tests:** fixture HTML -> expected JSON per ISP, validators, diff classifier, history append logic.
- **Manual:** airplane-mode run, first launch with no network, low-RAM emulator, dark mode, large font scale, one real-device run on API 26-28.
- **Data QA (weekly, 10 min):** spot-check 3 random packages against the live ISP site; review any open PRs.

---

## 11. Risks & mitigations

| Risk | Mitigation |
|---|---|
| ISP page layout changes -> scraper breaks | Per-ISP isolation, fixtures, failure emails, stale warning in app, previous data retained |
| LLM misreads PPN/install fee | Required evidence quotes, nullable-not-guess, `tax_inclusive` required, review PR on flips/big changes |
| Wrong prices shown to users | "Verify with provider" disclaimer, last-verified date, source link in every detail sheet |
| Scraping ToS / legal | Check robots.txt + ToS per ISP, low request rate, link back to source; drop an ISP if requested |
| Regional price differences | Province-level regions in contract; default `JAVA_ALL` only when page implies uniform pricing |
| ISP logos/trademark | Use logos nominatively and small, or fall back to text/initial avatars if challenged |
| Scheduled workflow disabled by GitHub inactivity | Monthly check of run history |
| Solo-maintainer burnout | Keep MVP to broadband + 3-10 ISPs, automate review via PRs |
| Plan renames break history | Deterministic ids + manual alias map in `sources.yaml` |

---

## 12. AI agent working rules

1. Work on **one roadmap task at a time**; end each with its acceptance criteria checked.
2. The **data contract (Section 4)** is the interface. Change it only by editing this doc first and bumping `schema_version` if it breaks compatibility.
3. **No new dependencies** outside the Section 7.1 list without asking.
4. Money = `Long` IDR everywhere. No Float/Double.
5. UI reads **only from Room** via Flow. UI/ViewModels never call the network.
6. Filtering/sorting in SQL. No `filter {}` on full lists in the UI layer.
7. Never guess ambiguous scraped fields; return `null` and fail validation.
8. Every public function with business logic gets a unit test (TrueCost, validators, diff, sync).
9. User-visible strings go in `strings.xml` (Indonesian). Code/comments in English.
10. Small commits, descriptive messages. Do not commit secrets. Keys live in GitHub Secrets / `local.properties`.
11. If a requirement is ambiguous or conflicts with this doc, **ask before building**.

### Prompt templates

**Phase 1:**
> Read BLUEPRINT sections 1-5, 7, 12. Implement Phase 1: Android project (Compose M3, minSdk 26, Hilt), Room entities/DAO matching 7.3, SeedImporter reading `assets/seed/catalog.json`, `CalculateTrueCost` per Section 5 with unit tests. Do not add networking yet. Stop when Phase 1 acceptance criteria are met and summarize what you built.

**Phase 3:**
> Read BLUEPRINT sections 4, 7.4, 7.5, 12. Implement SyncRepository and SyncWorker exactly per 7.4 (manifest check, sha256 verify, single-transaction replace, favorites preserved, schema_version guard). Add tests for success, rollback on corrupt payload, and "no change" paths.

**Phase 4:**
> Read BLUEPRINT sections 4, 5, 6, 12. Implement the pipeline for ISP `<name>` first: fetch, clean+hash, LLM extractor (provider chain) behind an `Extractor` interface, Pydantic validation, diff classifier, publish step, fixture tests. Evidence quotes must be logged to `pipeline/runs/`, not shipped in `data/`.

---

## 13. Backlog (post-MVP ideas)
Compare mode, favorites screen, local price-drop alerts, cellular, more islands, "best value" score, share a package, widget, English UI option, community "report wrong price" button (opens a prefilled GitHub Issue, $0).

---

## 14. Open questions
1. Confirm **D4** (static JSON on GitHub Pages instead of Supabase).
2. Is `device_rental_fee` listed with or without PPN on your target ISPs? (Decides whether `rental_tax_inclusive` is needed.)
3. Which 3 ISPs to start with in Phase 4?
4. Distribution plan: Play Store from the start, or GitHub Releases APK first?

---

## Appendix A: If you choose Supabase instead (D4 alternative)
- Tables mirror Section 4 (`isps`, `packages`, `package_regions`, `price_history`, `scrape_runs`, `staging_changes`). Pipeline upserts with the service key (GitHub Secret); app uses the anon key with RLS **read-only**.
- Sync becomes delta sync (`updated_at > last_sync`) + soft deletes (`deleted_at`). Review step = `staging_changes` table instead of PRs.
- Free-tier caveat: free projects can pause after a period of inactivity, so verify current limits and keep a scheduled ping/insert alive.
- Everything else (Room, UI, True Cost, roadmap) is unchanged.
