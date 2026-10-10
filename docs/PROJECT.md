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
   pipeline skips it. Biznet = LLM (provider chain, see #9). FirstMedia,
   IndiHome, Telkomsel, XL = deterministic regex/JSON parsers (no LLM, $0).
   The IndiHome **official** site is server-rendered (never the telkomsel
   LP — see quirks). Telkomsel `keep`s its SERU
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
9. **LLM provider chain with fallback** (2026-10-11, blueprint D5 amended):
   DeepSeek credit ran out, so Agnes AI (free tier, OpenAI-compatible,
   `https://apihub.agnes-ai.com/v1`, model **`agnes-2.5-flash`**) became the
   primary and DeepSeek the fallback. `extract.py` has an `LLMExtractor` base
   + `AgnesExtractor` / `DeepSeekExtractor`, wrapped by `FallbackExtractor`
   (tries providers in order, first usable success wins, keyless providers
   skipped, a 0-package reply counts as failure and falls through). Order
   comes from `EXTRACTOR_CHAIN` (default `agnes,deepseek`); a single provider
   can be pinned per ISP in `sources.yaml` (`extractor: agnes|deepseek`).
   Local keys live in gitignored `.env` (loaded by `run.py`); CI uses the
   `AGNES_API_KEY` + `DEEPSEEK_API_KEY` secrets. `run.py` logs `provider` per
   ISP in the run log.

   Free-tier gotchas (all probed live 2026-10-11, 4 trials each):
   - **Model choice matters**: `agnes-2.5-flash` returned all 4 Biznet plans
     4/4; `agnes-3.0-flash` returned an **empty** array 3/4; `agnes-2.5-pro`
     and `agnes-3.0-flash-max` are **HTTP 403 "Insufficient user quota"** (not
     in the free tier). Don't "upgrade" the model without re-probing.
   - Backends are **non-deterministic even at temperature 0**: replies vary
     between full/empty/duplicated across identical calls. `LLMExtractor.extract`
     retries a 0-package reply (`LLM_TRIES`, default 2).
   - Under `response_format: json_object` Agnes may return a **single bare
     package object** instead of the wrapper array — `parse_extraction`
     normalizes wrapper / bare-array / bare-object shapes (regression-tested).
     Without json_object mode it wraps replies in ```json fences instead, so
     keep json_object on.
   - It can also emit **duplicate plans**; run.py collapses same-id rows after
     validation and routes disagreeing duplicates to review (publish() only
     guards cross-ISP dups). A null `tax_inclusive` fails validation → review,
     never publishes.

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
  allowed to create PRs (review flow); `AGNES_API_KEY` (primary) +
  `DEEPSEEK_API_KEY` (fallback) secrets for real extraction.
- **`gh` CLI lives at `C:\Program Files\GitHub CLI\gh.exe`**, not on PATH.
  Authenticated as cat-miaw. Use it for runs/PRs/logs (no browser needed).
- Emulator `api34_low` (1080x2340): taps need device pixels (screenshot
  pixels ×1.2). `uiautomator dump` gives exact bounds.
- Seed names: FirstMedia "Internet Only Starter" has NO speed in name
  (speed lives in subtitle) — don't "fix" this, it's the seed.

## Current state (2026-10-11)

- **LLM extraction moved off DeepSeek** (credit exhausted) onto Agnes AI free
  tier, DeepSeek kept as automatic fallback (see decision #9). Verified live:
  Agnes extracted Biznet's 4 plans with verbatim evidence. 29 pytest green.
- Data v10+, 47 packages: Biznet 4, MyRepublic 5, FirstMedia 9 broadband;
  Telkomsel 14 + XL 15 cellular. App + pipeline + Pages deploy all live.
- v1.1 shipped: favorites, compare (max 3, best-value highlights),
  price-drop alerts, cellular tab. 22 JVM tests + pytest, all green.
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
  Compact (2026-10-08): row `12x4dp` padding, `titleMedium`, 40dp icons.
- **Bottom nav** (`NetBottomBar`): Beranda/Favorit/Bandingkan(+count via
  `compare_open`). Type switch lives ONLY in the top segmented control.
  Compact: 22dp icons, `labelSmall`, 32dp horizontal inset (icons grouped
  tighter); outer `Column` carries the surface background edge-to-edge so
  list cards never show through the inset sides. Full-width hairline
  divider above.
- **Overlay chrome, X-style**: NEITHER bar lives in a Scaffold slot.
  Both float over the full-bleed pager (`Box` overlays, top/bottom
  aligned) and ride the finger 1:1 via `graphicsLayer.translationY`
  driven by list scroll `dy` (`topHide`/`bottomHide` Animatables, travel
  = the bar's OWN measured height, 250ms-debounce settle to the nearer
  end at 50% of travel).
  NO fade/snap (the old AnimatedVisibility pop died per feedback).
  Travel MUST equal the bar's height (2026-10-09): the old fixed
  200dp/120dp budgets gave the 94dp top bar a ~290px dead zone, so on an
  up-scroll it lagged the finger by ~1.5s and then popped in, while the
  84dp bottom bar tracked 1:1. Frame-measured with the column-probe
  below; both bars now start and finish together.
  Lists carry CONSTANT insets measured once via `onGloballyPositioned`
  (`listTopPad`/`listBottomPad` passed down to all three tabs); hiding
  bars reveals already-laid-out content, zero snap. (2026-10-08 lesson:
  collapsing insets with the bars reintroduced the scroll jump via the
  near-top snap teleport + per-frame remeasure — reverted same day.)
  Since the overlays own every inset, the MAIN `Scaffold` sets
  `contentWindowInsets = WindowInsets(0,0,0,0)` and ignores its padding
  lambda — otherwise the status bar is insetted twice (big blank gap).
  Lint flags that as `UnusedMaterial3ScaffoldPaddingParameter` (a hard
  error, fails `:app:lintRelease`); it is suppressed on `TemplateNavHost`
  with an explanatory comment. Do NOT "fix" it by consuming the padding —
  that reintroduces the double inset.
  The top bar's solid background MUST sit INSIDE its translation layer
  (2026-10-09 fix — see gotchas).
- **Chrome auto-hide**: home list drives it via `snapshotFlow` on
  (index, offset): down-travel pushes both bars out pixel-for-pixel,
  up-travel pulls them back (single collector + 250ms-debounce settle to
  half of each bar's travel;
  the old split `isScrollInProgress` settle never fired reliably).
  Random `index` jump = synthetic dy of ±10000 (saturates = instant),
  which is intended for pagination/jump-to-top slams. Always shown near
  top (eased in over 200ms — a `snapTo(0f)` there was a visible pop) and
  on tabs 1–2. Bars only — insets constant.
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
  Compact (2026-10-08): `titleMedium` header, compact headline
  (`titleLarge`/`bodySmall`), 10dp rows, 20dp icons, 8dp spacing.
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

- **Overlay background draw order (2026-10-09)**: `background()` placed
  BEFORE `graphicsLayer{}` draws OUTSIDE the layer, so it stays parked at
  y0 while the bar's content translates away — a leftover `Paper` strip
  over the list (same rule as `background().clip()` not clipping but
  `clip().background()` doing so). Background goes AFTER the layer.
  Diagnosed by pixel-scanning a screenshot for a uniform `#FBFAF7` band
  (PIL; colors from `theme/Color.kt`) — the band's bottom edge matched
  the measured bar height. Fixed + verified on `api34_low`.
- **Chrome-motion probe (2026-10-09)**: to judge bar motion, don't eyeball
  it — `adb shell screenrecord` the gesture, `ffmpeg -fps_mode passthrough`
  to frames, then probe ONE COLUMN per bar: top bar = walk y down from 0
  while `px[540][y]` is Paper (x540 is the title row's empty centre) →
  edge = bar height − `topHide`; bottom bar = walk y up from the bottom
  while `px[10][y]`/`px[1070][y]` are white → edge = bar top + `bottomHide`.
  Compare per-frame deltas of the two bars: unequal start frames or
  deltas = the bars aren't tracking the finger together. MP4 shifts
  colours a few units and drops frames (~10-16fps), so match with a ±5
  tolerance, never exact equality. Video beats a slow screencap loop
  (screencap+pull is ~0.4s/frame, so it misses fast gestures entirely).
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
   (`firstmedia.py`, `telkomsel.py`). Else → LLM last resort (Agnes primary,
   DeepSeek fallback)
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
- **IndiHome — use the OFFICIAL site, not the Telkomsel LP (2026-10-09)**:
  two different hosts, one is a trap.
  *Wall*: `telkomsel.com/landingpage/regular/nasional` is a Next.js
  **order funnel**. Static fetch works (5.8k chars) but `clean()` yields
  only the `__NEXT_DATA__` shell — six tab configs (`promo`, `internet`,
  `internetmovie`, `internetgamer`, `internettv`, `bayarsekaligus`) with
  `id_packages` (66/49/52/51/74/53) + a *plaintext* `api_key`
  (`AO_ML2_1P_IH`…) + `from_api`, and **zero prices**. Rows come from
  `POST landingpage/api/lp/fmc/v1/get-package`,
  `{token, code, data:{channel, packageCategory, installDetails,
  locDetails, feasibleType, pid, cpid}}`, key in header **`x-api-key`**
  (`apiKeyQuick`) — a CryptoJS AES blob (`U2FsdGVkX1…` = `Salted__`,
  passphrase in the bundle). Plaintext `api_key`, `apiKeyExt` and the raw
  blob ALL return `{code:999,"API key not accessible"}`, and it needs an
  install-location payload (`fmc/v1/install-loc`). Do NOT add AES-key
  extraction: brittle, and it defeats an access gate (blueprint 11).
  *Way through*: **`indihome.co.id` is server-rendered** — same price
  cards, 34.6k cleaned chars, no JS. `pipeline/scraper/indihome.py`
  (`regex-indihome`) parses the rigid block `title / <speed> Mbps /
  Mulai dari / Rp<price> / /bulan / Pilih Paket` → 21 unique plans
  (24 card occurrences; repeats across "Best Deal" and its own tab are
  deduped, conflicting prices raise).
  Gotchas: card titles are promo tier names ("Double Speed - 500 Mbps")
  and do NOT match the speed beneath → **the numbered line is the
  speed**, and the id includes it (`indihome-<title>-<speed>`), so the
  same title at two speeds stays distinct. The page states **no PPN,
  install fee, rental or ONT anywhere**, so `tax_inclusive` is a curated
  constant in the extractor (False = ex-PPN, maintainer-confirmed) and
  `install_fee` stays `None` ("Tanya provider") — never derived.
  `device_rental_fee=0` assumes the ONT is bundled (stated on the LP,
  silent on the plan page): **watch item**.

## Everyday commands

```bash
./gradlew :app:installDebug :app:testDebugUnitTest   # build + all tests
./gradlew :app:bundleRelease                         # release AAB (+size check)
python -m pytest pipeline/tests/ -q                  # pipeline tests
python pipeline/validate.py                           # contract check
python pipeline/run.py --fetch-only [--isp biznet]   # baseline, $0
python pipeline/run.py [--isp biznet]                # real run (needs .env key)
gh workflow run scrape --ref main --repo cat-miaw/netbanding
gh run watch <id> --repo cat-miaw/netbanding
```

## Fresh-session checklist

1. `git pull`, `./gradlew :app:assembleDebug`, `pytest pipeline/tests/`.
2. Re-read blueprint §§4–5 + the Decisions list above before changing
   contract, sync, pipeline, or money math.
3. Commit + push BEFORE high-risk tasks (data/, publish path, DB).
