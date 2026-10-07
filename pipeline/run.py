"""Weekly orchestrator. Per-ISP isolation: one broken page never blocks others.
Failed/empty extractions keep previous data (only last_verified_at goes stale).

First run: use --fetch-only to baseline page hashes with zero LLM cost and
zero data changes. Real extraction needs DEEPSEEK_API_KEY.
"""
import argparse
import json
import os
from datetime import datetime, timezone

import yaml

from scraper.checks import validate_catalog, validate_package, verify_evidence
from scraper.clean import clean, content_hash
from scraper.diff import classify
from scraper.extract import DeepSeekExtractor, normalize_package
from scraper.firstmedia import FirstMediaExtractor
from scraper.telkomsel import TelkomselExtractor
from scraper.fetch import fetch
from scraper.publish import publish
from scraper.schema import Catalog

STATE_DIR = "pipeline/state"
RUNS_DIR = "pipeline/runs"
REVIEW_FILE = "pipeline/review.json"
DEACTIVATE_AFTER_MISSES = 3

EXTRACTORS = {
    "deepseek": DeepSeekExtractor,
    "regex-firstmedia": FirstMediaExtractor,
    "regex-telkomsel": TelkomselExtractor,
}


def _load(path: str, default):
    try:
        return json.load(open(path))
    except FileNotFoundError:
        return default


def _save(path: str, data) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(data, open(path, "w"), indent=2)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--fetch-only", action="store_true")
    ap.add_argument("--isp", default=None)
    args = ap.parse_args()

    sources = yaml.safe_load(open("pipeline/sources.yaml"))
    catalog = validate_catalog(json.load(open("data/catalog.json")))
    history = json.load(open("data/history.json"))
    hashes: dict = _load(f"{STATE_DIR}/hashes.json", {})
    misses: dict = _load(f"{STATE_DIR}/misses.json", {})
    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    run_log: dict = {"date": today, "fetch_only": args.fetch_only, "isps": {}}
    review: dict = {"needs_review": False, "items": []}

    for isp in sources["isps"]:
        if args.isp and isp["id"] != args.isp:
            continue
        log: dict = {"status": "ok", "pages": 0, "tokens": 0, "changed": []}
        if isp.get("extract") == "manual":
            # JS-walled or otherwise unscrapable: human-curated seed stays.
            log["status"] = "manual_seed"
            run_log["isps"][isp["id"]] = log
            continue
        extractor = EXTRACTORS.get(isp.get("extractor", "deepseek"), DeepSeekExtractor)()
        try:
            texts = []
            for page in isp["pages"]:
                texts.append(fetch(page["url"], page.get("mode", "static")))
                log["pages"] += 1
        except Exception as e:  # noqa: BLE001 - isolate per ISP
            log.update(status="fetch_failed", error=str(e))
            run_log["isps"][isp["id"]] = log
            continue
        text = clean("\n".join(texts))
        h = content_hash(text)
        os.makedirs(f"{RUNS_DIR}/texts", exist_ok=True)
        open(f"{RUNS_DIR}/texts/{isp['id']}.txt", "w", encoding="utf-8", newline="").write(text)
        if hashes.get(isp["id"]) == h:
            log["status"] = "unchanged_skip"
            run_log["isps"][isp["id"]] = log
            continue
        hashes[isp["id"]] = h
        needs_key = isinstance(extractor, DeepSeekExtractor)
        if args.fetch_only or (needs_key and not os.environ.get("DEEPSEEK_API_KEY")):
            log["status"] = "baseline_no_llm" if args.fetch_only else "skipped_no_key"
            run_log["isps"][isp["id"]] = log
            continue
        try:
            out = extractor.extract(text)
        except Exception as e:  # noqa: BLE001 - keep previous data
            log.update(status="extract_failed", error=str(e))
            run_log["isps"][isp["id"]] = log
            continue
        log["tokens"] = out.get("tokens", 0)
        log["evidence"] = out.get("evidence", {})
        log["chars"] = len(text)
        if not out.get("packages"):
            # Empty extraction keeps previous data and touches nothing
            # (blueprint 6.3); the stored hash avoids weekly LLM cost until
            # the page actually changes.
            log.update(status="extract_empty")
            run_log["isps"][isp["id"]] = log
            continue
        pkgs, errs = [], []
        for i, raw in enumerate(out.get("packages", [])):
            raw = normalize_package(
                isp["id"], raw, i, isp["pages"][0]["url"], today,
                regions=isp.get("default_regions"),
            )
            raw["id"] = isp.get("aliases", {}).get(raw["id"], raw["id"])
            pkg, e = validate_package(raw, isp["id"])
            e += verify_evidence(out.get("evidence", {}).get(str(i), {}), text)
            if pkg is None or e:
                errs.append({"item": i, "errors": e})
            else:
                pkgs.append(pkg)
        if errs:
            log.update(status="validation_failed", errors=errs)
            review["needs_review"] = True
            review["items"].append({"isp": isp["id"], "reason": "validation", "errors": errs})
            run_log["isps"][isp["id"]] = log
            continue
        old_isp = [p for p in catalog.packages if p.isp_id == isp["id"] and p.is_active]
        keep = set(isp.get("keep", []))
        seen = {p.id for p in pkgs}
        for p in old_isp:
            if p.id in keep:
                misses.pop(p.id, None)
            elif p.id not in seen:
                misses[p.id] = misses.get(p.id, 0) + 1
                if misses[p.id] >= DEACTIVATE_AFTER_MISSES:
                    d = p.model_dump()
                    d["is_active"] = False
                    pkgs.append(type(p)(**d))
            else:
                misses.pop(p.id, None)
        result = classify(old_isp, pkgs, keep)
        log["changed"] = result["changed"]
        log["extracted"] = sorted(p.id for p in pkgs)
        if result["needs_review"]:
            log.update(status="needs_review", reasons=result["reasons"])
            review["needs_review"] = True
            review["items"].append({"isp": isp["id"], "reasons": result["reasons"]})
        else:
            info = publish(catalog, isp["id"], pkgs, history, keep_ids=keep)
            catalog = validate_catalog(json.load(open("data/catalog.json")))
            log.update(status="published", **info)
        run_log["isps"][isp["id"]] = log

    _save(f"{STATE_DIR}/hashes.json", hashes)
    _save(f"{STATE_DIR}/misses.json", misses)
    _save(f"{RUNS_DIR}/{today}.json", run_log)
    if review["needs_review"]:
        _save(REVIEW_FILE, review)
    elif os.path.exists(REVIEW_FILE):
        os.remove(REVIEW_FILE)
    print(json.dumps({k: v.get("status") for k, v in run_log["isps"].items()}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
