# Release checklist (Phase 5)

## Build
- [ ] `DEFAULT_BASE_URL` in `data/remote/Api.kt` points at the real Pages URL (served by `pages.yml` from `data/`).
- [ ] Seed snapshot refreshed: copy `data/*` into `app/src/main/assets/seed/` before each release.
- [ ] `./gradlew clean :app:bundleRelease` green; AAB < 8 MB (check `app/build/outputs/bundle/release/*.aab`).
- [ ] Replace debug-key signing with a real `signingConfig` (keystore outside the repo).

## Performance
- [ ] Baseline Profile: `./gradlew :app:generateBaselineProfile` on a **physical** low-end device, commit `baseline-prof.txt`.
- [ ] Cold start < 2 s on a 2 GB device (API 26 + one recent API); verify with Macrobenchmark, not debug builds.

## QA (manual)
- [ ] Airplane mode after first launch: full list, filters, detail, history all work.
- [ ] First launch with no network: seed list shows.
- [ ] Dark mode, large font scale, rotation, process death (filters kept).
- [ ] Data-safety form: "no data collected". Privacy policy linked (in-app Settings → Kebijakan privasi).
- [ ] New Play accounts: check current closed-testing rules (testers + duration) and recruit early.

## Data
- [ ] Spot-check 3 random packages against live ISP sites; clear `pipeline/review.json` queue.
