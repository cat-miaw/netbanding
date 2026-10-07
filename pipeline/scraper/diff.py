"""Diff vs data/catalog.json. Big/suspicious changes need human review, never auto-publish."""
from scraper.schema import Package

PRICE_REVIEW_PCT = 30.0
COUNT_DROP_REVIEW_PCT = 30.0

# Money and contract terms: any change here needs eyes, no matter how small.
MONEY_FIELDS = ("base_price", "device_rental_fee", "install_fee",
                "contract_months", "quota_mb", "validity_days")


def classify(old: list[Package], new: list[Package]) -> dict:
    old_by = {p.id: p for p in old}
    new_by = {p.id: p for p in new}
    added = [i for i in new_by if i not in old_by]
    removed = [i for i in old_by if i not in new_by]
    changed: dict[str, list[str]] = {}
    reasons: list[str] = []
    for pid, np in new_by.items():
        op = old_by.get(pid)
        if op is None:
            continue
        flags: list[str] = []
        if op.base_price != np.base_price:
            pct = abs(np.base_price - op.base_price) / op.base_price * 100
            flags.append(f"price {op.base_price}->{np.base_price} ({pct:.0f}%)")
            if pct > PRICE_REVIEW_PCT:
                reasons.append(f"{pid}: price change {pct:.0f}% > {PRICE_REVIEW_PCT:.0f}%")
        if op.tax_inclusive != np.tax_inclusive:
            flags.append("tax_inclusive flipped")
            reasons.append(f"{pid}: tax_inclusive flipped")
        if op.speed_mbps != np.speed_mbps:
            flags.append(f"speed {op.speed_mbps}->{np.speed_mbps}")
            reasons.append(f"{pid}: speed changed")
        for field in MONEY_FIELDS:
            if getattr(op, field) != getattr(np, field):
                flags.append(f"{field} {getattr(op, field)}->{getattr(np, field)}")
                reasons.append(f"{pid}: {field} changed")
        if set(op.regions) != set(np.regions):
            flags.append("regions changed")
            reasons.append(f"{pid}: regions changed")
        if flags:
            changed[pid] = flags
    if old_by:
        drop_pct = len(removed) / len(old_by) * 100
        if drop_pct > COUNT_DROP_REVIEW_PCT:
            reasons.append(f"package count dropped {drop_pct:.0f}%")
    return {"added": added, "removed": removed, "changed": changed, "needs_review": bool(reasons), "reasons": reasons}
