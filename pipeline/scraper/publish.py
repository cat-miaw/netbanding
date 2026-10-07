"""Publish: safe changes commit directly, needs-review writes a review file for a PR.

Inactive rows are KEPT (is_active=false), never dropped, so the app's
price history survives via FK and the 3-miss rule in run.py stays auditable.
"""
import hashlib
import json
from datetime import datetime, timezone

from scraper.schema import Catalog

DATA_DIR = "data"


def _now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def publish(
    old_catalog: Catalog,
    isp_id: str,
    new_packages: list,
    history: dict,
    now: str | None = None,
    keep_ids: set | None = None,
) -> dict:
    now = now or _now()
    keep_ids = keep_ids or set()
    new_ids = {p.id for p in new_packages}
    old_pkgs = [p for p in old_catalog.packages
                if p.isp_id != isp_id or (p.id in keep_ids and p.id not in new_ids)]
    kept_ids = {p.id for p in old_pkgs}
    for pkg in new_packages:
        d = pkg.model_dump()
        d["last_verified_at"] = now
        if d["id"] in kept_ids:
            raise ValueError(f"duplicate id across ISPs: {d['id']}")
        old_pkgs.append(type(pkg)(**d))
    old_pkgs.sort(key=lambda p: (p.isp_id, p.id))
    catalog = Catalog(isps=old_catalog.isps, packages=old_pkgs)

    for pkg in new_packages:
        pts = history.setdefault(pkg.id, [])
        if not pts or pts[-1]["price"] != pkg.base_price or pts[-1]["tax_inclusive"] != pkg.tax_inclusive:
            pts.append({"price": pkg.base_price, "tax_inclusive": pkg.tax_inclusive, "recorded_at": now})

    manifest = json.load(open(f"{DATA_DIR}/manifest.json"))
    manifest["data_version"] += 1
    manifest["generated_at"] = now
    # Canonical bytes: Pydantic model_dump_json(indent=2) verbatim. The app
    # verifies sha256 against these exact bytes (see .gitattributes), so any
    # reformatting (key sorting, compact separators) breaks sync.
    cat_bytes = catalog.model_dump_json(indent=2).encode()
    hist_bytes = json.dumps(history, indent=2).encode()
    manifest["files"] = {
        "catalog": {
            "path": "catalog.json",
            "sha256": hashlib.sha256(cat_bytes).hexdigest(),
            "bytes": len(cat_bytes),
        },
        "history": {
            "path": "history.json",
            "sha256": hashlib.sha256(hist_bytes).hexdigest(),
            "bytes": len(hist_bytes),
        },
    }
    open(f"{DATA_DIR}/catalog.json", "wb").write(cat_bytes)
    open(f"{DATA_DIR}/history.json", "wb").write(hist_bytes)
    json.dump(manifest, open(f"{DATA_DIR}/manifest.json", "w", newline=""), indent=2)
    return {"data_version": manifest["data_version"], "generated_at": now}
