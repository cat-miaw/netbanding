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
- UI redesign (2026-10-08, uncommitted): mockup-driven facelift — bottom
  nav is Beranda/Favorit/Bandingkan (type switch lives ONLY in the top
  segmented control, no dupe), new PackageCard (quota/speed info box,
  + Bandingkan / Detail paket row), detail sheet (Kuota+Masa aktif boxes,
  Bandingkan/Simpan buttons), full-screen Menu ("Mau cari apa?") replaces
  the drawer, grouped Settings ("Sesuai kebutuhanmu"). Provider filter
  kept (row 2, full width; + speed filter on broadband). Background Paper.
- Emulator top-strip gotcha (2026-10-08): taps at y<~130px never reach
  the app (SystemUI touch zone). NetTopBar carries 28dp extra top padding
  so the hamburger sits clear. Verify top-bar taps via uiautomator dump.
- Tabs v2 (2026-10-08, committed): Beranda/Favorit/Bandingkan are ONE
  HorizontalPager under a SINGLE Scaffold — bars never slide, only the
  highlight moves; `beyondViewportPageCount=1` + solid pager background
  kills the tap-blink. Menu is a ModalNavigationDrawer over the current
  tab (explicit BackHandler closes it; M3 doesn't by default). Empty
  search keeps the search bar + "Hapus pencarian" reset (never strand
  the user: the field lives inside the list, not in a branch that
  vanishes on empty results).
- Scale + compare overhaul (2026-10-08, committed): SQL LIMIT/OFFSET +
  COUNT paging, 15/page with « 1 … n » strip (page resets on any filter
  change, list jumps to top). Compare: card/sheet buttons show
  ✓ Dibandingkan (CompareViewModel.selectedIds), nav shows Banding (n);
  empty state has icon + Lihat paket; rows only appear when relevant
  (no speed rows for cellular); Kuota shows totals only, new Per GB row
  carries the ✓; Masa aktif longest wins (broadband ranks 30d); Total ✓
  only on equal billing cycles, else a "tidak sebanding" note.
- Drawer swipe (2026-10-08): rightward drag on Beranda opens the drawer.
  Passive detectors always lose the slop race to the pager, so a custom
  parent-first detector consumes the slop-crossing event itself. Two
  gotchas found by logcat: (1) `awaitFirstDown()` ignores presses that
  cards already consumed for ripple → `requireUnconsumed = false`;
  (2) touches starting at the system edge are skipped so back-gesture
  keeps working.
- Custom follow-finger drawer (2026-10-08, committed): stock
  ModalNavigationDrawer can't be driven mid-gesture (and this BOM's
  AnchoredDraggable API differs), so the drawer is a 320dp Surface over
  an `Animatable` offset: open-drag forwards deltas, close-drag on the
  panel, half-width settle both ways. Scrim tap + BackHandler close.
- Chrome auto-hide (2026-10-08): top + bottom bars hide on scroll down,
  return on scroll up (home list drives it). Asymmetric travel
  thresholds (hide after 48px down, show after 160px up, direction-flip
  resets) so flings can't flap the transition mid-flight; 150ms
  slide+fade so it finishes instead of lingering half-slid over content
  on weak GPUs. Instant toggle was tried and rejected (felt wrong).
- Polish: cards get 1dp outlineVariant borders; NetTopBar uses
  statusBarsPadding (notch-aware) instead of the 28dp hack; JAVA_ALL
  label is "Pulau Jawa"; menu rows are clean text rows (only the active
  Seluler keeps its tint), matching the mockup.
- Phone trial (2026-10-08): release APK 1.86 MB
  (`app/build/outputs/apk/release/app-release.apk`, debug-signed),
  verified on emulator incl. onboarding + seed. Debug builds will
  always animate worse than this on weak hardware (no R8, JIT cold).

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
