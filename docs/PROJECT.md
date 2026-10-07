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
3. **MyRepublic + XL = `manual` sources** (JS shells): hand-curated seed,
   pipeline skips them. Biznet = DeepSeek LLM. FirstMedia + Telkomsel =
   deterministic regex parsers (no LLM, $0). Telkomsel `keep`s its SERU packs
   (different page, still curated); express-purchase is number-gated, out of scope.
   FirstMedia summary table is stale — trust numbered blocks 1A-3C.
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

- Data v7, 33 packages: Biznet 4, MyRepublic 5, FirstMedia 9 broadband;
  Telkomsel 8 + XL 7 cellular. App + pipeline + Pages deploy all live.
- v1.1 shipped: favorites, compare (max 3, best-value highlights),
  price-drop alerts, cellular tab. 22 JVM tests + 13 pytest, all green.
- Release AAB 3.74 MB (< 8 MB). Baseline Profile NOT yet generated
  (needs physical device). Play Console not yet created.
- OPEN: Biznet modem rental Rp 50rb (unverifiable on site); FirstMedia
  seed is reseller data (re-verify vs official site); more islands.

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
