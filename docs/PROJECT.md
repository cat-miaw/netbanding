# NetBanding — Project Handbook

> Read this first in a fresh session. Companion: `NETBANDING_BLUEPRINT.md`
> (product spec; data contract in §4 is law). This file is the *as-built*
> record: decisions, deviations, gotchas, and current state.

## What it is

Native Android app (Indonesian UI) comparing home-internet (broadband) and
cellular package prices across Java, Indonesia. Offline-first, free, no ads,
no accounts, no analytics. Data: static JSON on GitHub Pages
(`https://cat-miaw.github.io/netbanding/`), scraped weekly + LLM extraction.
Infra cost ≈ $0.

## Repo layout

```
app/                  Android app (single module, com.netbanding.app)
  src/main/assets/seed/   bundled snapshot of data/ (first-launch DB seed)
  src/main/java/.../data/local/   Room entities, DAO, mappers, seed DTOs
  data/remote/  Retrofit API + manifest DTOs
  data/repo/    PackageRepository (UI reads Room Flow ONLY, never network)
  data/sync/    SeedImporter, SyncRepository, SyncWorker
  data/prefs/   UserPrefs (DataStore) + FakeSyncStore (tests)
  data/notify/  PriceDropMonitor (local alerts, no FCM)
  domain/model/ Package, Isp, TrueCost, PricePoint
  domain/usecase/ CalculateTrueCost (blueprint §5; money = Long IDR)
  ui/home|detail|favorites|compare|onboarding|settings (+ components/, theme/)
  di/           Manual DI container (NO Hilt — see Decisions)
data/                 Published contract: manifest.json, catalog.json, history.json
pipeline/             Python scraper (fetch/clean/extract/checks/diff/publish/run.py)
  scraper/firstmedia.py   deterministic regex extractor (no LLM, $0)
  state/            hashes.json (hash-skip), misses.json (3-miss rule)
  runs/             per-run logs, GITIGNORED (workflow artifacts instead)
  tests/            pytest suite (fixtures per ISP)
docs/                 PRIVACY.md, RELEASE.md (checklist), this file
.github/workflows/   ci.yml (app), pages.yml (serve data/), scrape.yml (weekly)
```

## Key decisions & deviations from blueprint

1. **Manual DI, not Hilt** (blueprint D6 amended 2026-10-07): cold start <2s
   on 2 GB devices matters more than DI fashion. All deps `by lazy`.
2. **No chart dependency**: custom Canvas step-line for price history.
3. **MyRepublic = `manual` source** (JS shell): hand-curated seed,
   pipeline skips it. Biznet = DeepSeek LLM. FirstMedia, Telkomsel, XL =
   deterministic regex/JSON parsers (no LLM, $0). Telkomsel `keep`s its SERU
   packs (different page, still curated); XL `keep`s its Flex/VIP packs
   (ULTRA 5G+ family page is the automated one). Express-purchase flows are
   number-gated, out of scope. FirstMedia summary table is stale — trust
   numbered blocks 1A-3C.
4. **Evidence must be verbatim** (`verify_evidence`): LLM quotes are checked
   as substrings of page text. Fabricated evidence → validation failure →
   review PR. This caught real hallucinations (Biznet PPN/install quotes).
5. **Money/contract/region changes always need review**, however small.
   Small price moves (≤30%) auto-publish.
6. **Inactive rows are kept, never deleted** (history survives via FK).
7. **Needs `INTERNET` permission** (not just ACCESS_NETWORK_STATE): startup
   sync on a raw OkHttp thread kills the process without it. Found by crash.
8. **Cellular honesty rules**: period-aware prices (`/bln` >21d, `/mgg`
   7–21d, `/hr` <7d; broadband always `/bln`), no budget chips on Seluler,
   install rows hidden for cellular, per-GB sort instead of per-Mbps.
   Biznet `install_fee=null` (undisclosed — never guess). Rp 50rb modem
   rental still OPEN (unverified, see below).

## Gotchas (learned the hard way)

- **CRLF kills sync**: manifest sha256 must match served bytes exactly.
  `.gitattributes` pins `eol=lf` for contract JSON; `publish.py` and all
  seed scripts must write `newline=''` / binary. Python `open(...,'w')` on
  Windows writes CRLF by default.
- **Canonical catalog bytes = Pydantic `model_dump_json(indent=2)` verbatim**
  (field order, pretty). Never reformat (no key sorting, no compacting).
  `publish.py` also sorts packages by `(isp_id, id)` for stable diffs.
- **Onboarding nav**: `collectAsState(initial = null)`, navigate only on
  explicit `false`, else every cold start flashes onboarding.
- **Room `IN ()` crashes**: guard empty lists before `observeByIds`.
- **`java.lang.Package` vs domain `Package`**: import explicitly in files
  referencing both navigation and domain types.
- **Re-running an old Actions run pins the old commit** — always dispatch
  fresh (or `gh workflow run scrape --ref main`).
- **Repo settings needed**: Pages source = GitHub Actions; Actions must be
  allowed to create PRs (review flow); `DEEPSEEK_API_KEY` secret for real
  extraction.
- **`gh` CLI lives at `C:\Program Files\GitHub CLI\gh.exe`**, not on PATH.
  Authenticated as cat-miaw. Use it for runs/PRs/logs (no browser needed).
- Emulator `api34_low` (1080x2340): taps need device pixels (screenshot
  pixels ×1.2). `uiautomator dump` gives exact bounds.
- Seed names: FirstMedia "Internet Only Starter" has NO speed in name
  (speed lives in subtitle) — don't "fix" this, it's the seed.

## Current state (2026-10-07)

- Data v10+, 47 packages: Biznet 4, MyRepublic 5, FirstMedia 9 broadband;
  Telkomsel 14 + XL 15 cellular. App + pipeline + Pages deploy all live.
- v1.1 shipped: favorites, compare (max 3, best-value highlights),
  price-drop alerts, cellular tab. 22 JVM tests + 13 pytest, all green.
- Release AAB 3.74 MB (< 8 MB). Baseline Profile NOT yet generated
  (needs physical device). Play Console not yet created.
- OPEN: Biznet modem rental Rp 50rb (unverifiable on site); FirstMedia
  seed is reseller data (re-verify vs official site); more islands.
- OPEN: Biznet modem rental Rp 50rb (unverifiable on site); FirstMedia
  seed is reseller data (re-verify vs official site); more islands.

## UI architecture (as-built 2026-10-08 — read this before touching UI)

Single `Scaffold` in `TemplateNavHost` owns everything. One
`HorizontalPager` (3 pages: Beranda/Favorit/Bandingkan,
`beyondViewportPageCount=1`) holds tab contents that are Scaffold-free.
Bottom taps and left/right swipes both just change the page — tabs never
push routes, so the chrome never rebuilds (this killed the old tap-blink).
VMs are activity-scoped so tab state survives swipes/drawer jumps.

- **Top bar** (`NetTopBar`, `ui/components/AppChrome.kt`): burger/back box,
  title, optional search + filter icons, optional text action. Bare
  `IconButton`s (box backgrounds removed per feedback), hairline
  `HorizontalDivider` below (inset 16dp). The bar + divider MUST be
  wrapped in a `Column` — Scaffold's topBar slot overlays multiple
  children at the origin (a stray full-width strip at y0 taught us).
  Uses `statusBarsPadding()` (notch-aware; the old 28dp hack died).
- **Bottom nav** (`NetBottomBar`): Beranda/Favorit/Bandingkan(+count via
  `compare_open`). Type switch lives ONLY in the top segmented control.
- **Overlay chrome, X-style**: NEITHER bar lives in a Scaffold slot.
  Both float over the full-bleed pager (`Box` overlays, top/bottom
  aligned) and animate with pure draw-phase motion (slide+fade inside a
  fixed-size box). NOTHING EVER RESIZES — this is the entire fix for the
  scroll-jump saga below. Lists carry constant insets measured once via
  `onGloballyPositioned` (`listTopPad`/`listBottomPad` passed down to all
  three tabs); hiding bars reveals already-laid-out content, zero snap.
- **Chrome auto-hide**: home list drives it via `snapshotFlow` on
  (index, offset) with directional accumulation (`acc` resets on
  direction flip): hide after 48px down-travel, show after 160px up
  (flings can't flap it), always shown near top and on tabs 1–2.
- **Search**: lives INSIDE the list (header item) so empty results can
  never strand the user; empty state has "Hapus pencarian" reset.
  Header scrolls away; a top-bar search icon appears past 120px and
  scrolls-to-top + focuses + shows keyboard (`revealSearch`).
  Filters toggle via top-bar Tune button (default shown).
- **Drawer**: custom, NOT `ModalNavigationDrawer` (can't be driven
  mid-gesture; this BOM's AnchoredDraggable differs). 320dp `Surface`
  over an `Animatable` offset: pager-level drag detector forwards deltas
  (open), panel detector (close), half→35% travel + manual fling velocity
  settle both ways, scrim + BackHandler close. Detector rules carved in
  blood: parent-first must CONSUME the slop-crossing event (passive
  detectors always lose to the pager); `awaitFirstDown(
  requireUnconsumed=false)` because cards eat presses for ripple;
  touches starting <24dp from the left edge are ignored (system back).
  Drawer content (`MenuDrawerContent`) highlights the live tab+type.
- **Paging**: SQL `LIMIT/OFFSET` + `COUNT` (same WHERE), 15/page
  (`PAGE_SIZE`), `« 1 … n »` strip (`pageWindow`, unit-tested), page
  resets on any filter change, list jumps to top, footer always shows
  "Menampilkan A–B dari N paket" (strip hides on 1 page BY DESIGN).
- **Compare** (`CompareScreen.kt` pure helpers + `CompareLogicTest`):
  empty states have icon + "Lihat paket"; card/sheet/nav show
  ✓ Dibandingkan / Banding (n) via `selectedIds`; rows render only when
  relevant (no speed rows for cellular); Kuota = totals, Per GB row
  carries the ✓; Masa aktif longest wins (broadband ranks 30d);
  Total ✓ only on equal billing cycles, else "tidak sebanding" note.
- **Cards** (`PackageCard`): 18dp, 1dp outlineVariant border, quota box,
  + Bandingkan (✓ state) / Detail paket row.

## UI jump saga (why the chrome works this way — don't regress this)

1. Slide in Scaffold slots → per-frame LazyColumn remeasure → dropped
   frames on weak GPUs. 2. Fade-only → AnimatedVisibility snaps slot
   size at exit START → list jumped under the still-fading bar (caught
   on a video frame). 3. Two-phase (fade, collapse after) → the collapse
   snap is a glaring single-frame jump at 120Hz. 4. Expand/shrink →
   still resizes every frame. FINAL: overlay bars + full-bleed list =
   show/hide touches layout NEVER (the overlay top bar carries its own
   solid background — without it, list text scrolls visibly underneath).
   Verified against X frame-by-frame: X also floats its bottom bar over
   a full-bleed feed (X keeps its top logo row fixed; we hide ours, so
   our lists keep a measured top inset and hiding top leaves calm space).

## UI gotchas & verification toolkit

- Emulator top-strip: taps at y<~130px die in the SystemUI zone
  (statusBarsPadding fixed the hamburger; verify via dump bounds).
- `adb shell uiautomator dump /sdcard/x.xml` + pull + grep `text=` is
  the standard probe; `adb shell input tap/swipe` for interaction.
- `read` tool opens PNG screenshots (pull via
  `adb shell screencap -p /sdcard/x.png` first — NEVER `exec-out`
  redirect on Windows, it corrupts); ffmpeg exists for video frames;
  PIL installable for pixel scans (caught a y0 divider + a system
  dialog dimming the screen this way).
- Fresh `am start` re-triggers the notification-permission dialog
  (dismissed, not granted) — it dims screenshots and eats the first tap.
- Phone trial builds: `./gradlew :app:assembleRelease` (R8, debug key),
  1.88 MB APK; AAB 4.25 MB (< 8 MB). Emulator animates worse than any
  real phone — judge motion on device, logic on emulator.

## Scraping playbook & per-ISP quirks

New ISP, same drill every time:

1. **Probe readability**: fetch with a browser UA, run through
   `clean.clean`, check char count. <1k chars = JS shell or wall →
   `manual` source, hand seed, stop.
2. **Embedded JSON first** (`type="application/json"`, `__NEXT_DATA__`):
   integer prices + typed rows beat text scraping. Template: `xlultra.py`.
3. **Else rigid text blocks** → regex extractor + curated id map
   (`firstmedia.py`, `telkomsel.py`). Else → DeepSeek LLM last resort
   (costs money; needs PPN/install stated on-page or every run reviews).
4. **Ids deterministic** (`{isp}-{slug}-{speed|validity}`); renames in
   `aliases:`, other-page packs in `keep:` (never counted as removed).
   Trailing `+` is a tier marker (M vs M+) — `xlultra._slug` preserves it.
5. **Seed via `tmp_*.py`** (canonical pretty JSON, LF, manifest bump,
   history initials, assets copy, then DELETE the script). Validate, pytest,
   fix counts in `SeedAndDaoTest`, push, prove in CI via `gh`.
6. **Numbers**: `_gb` handles `2.5`, `2,5` (decimals) vs `1.500`
   (thousands) — a dot-strip once ate "2.5" into 25 GB. Regression-tested.

Quirks ledger:

- **XL**: hub-discovered families (`xlhub.discover`); unknown slugs trip
  review, never silent skip. `skip_families`: flexmax/flexmini (dupe the
  curated Flex line), bebas-puas (custom sachet structure, deferred),
  disneyplus (streaming, not data). VIP Plus 20/52GB dupes aliased to
  curated ids. Flex-mc is a separate edition lineup (own prices). Quotas
  vary by location picker — national defaults recorded.
- **Telkomsel**: only `/simpati` cards (regex); express-purchase is
  number-gated skeletons, out of scope. SERU packs live on another page,
  kept as curated. `keep:` guards them.
- **FirstMedia**: official domain JS-walled; reseller data retired
  2026-10-07 (wrong Superuser tier). 3 official plans curated; button
  opens official site, `source_url` keeps provenance.
- **Biznet**: page states no PPN/install/rental → evidence gate fails
  every run by design; PR #4 is the standing watch item. Curated seed
  stands. Cards repeat 50 Mbps for all tiers — true speeds come from the
  comparison table (prompt rule).

## Everyday commands

```bash
./gradlew :app:installDebug :app:testDebugUnitTest   # build + all tests
./gradlew :app:bundleRelease                         # release AAB (+size check)
python -m pytest pipeline/tests/ -q                  # pipeline tests
python pipeline/validate.py                           # contract check
python pipeline/run.py --fetch-only [--isp biznet]   # baseline, $0
gh workflow run scrape --ref main --repo cat-miaw/netbanding
gh run watch <id> --repo cat-miaw/netbanding
```

## Fresh-session checklist

1. `git pull`, `./gradlew :app:assembleDebug`, `pytest pipeline/tests/`.
2. Re-read blueprint §§4–5 + the Decisions list above before changing
   contract, sync, pipeline, or money math.
3. Commit + push BEFORE high-risk tasks (data/, publish path, DB).
