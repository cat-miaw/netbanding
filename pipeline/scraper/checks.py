"""Pydantic + sanity rules. tax_inclusive is required; ambiguous fields fail."""
from scraper.schema import Catalog, Package

PRICE_RANGE = (100_000, 2_000_000)
SPEED_RANGE = (5, 2000)
INSTALL_RANGE = (0, 2_000_000)


def validate_package(raw: dict, isp_id: str) -> tuple[Package | None, list[str]]:
    errors: list[str] = []
    try:
        pkg = Package(**{**raw, "isp_id": isp_id})
    except Exception as e:  # noqa: BLE001 - collected into review flow
        return None, [f"schema: {e}"]
    lo, hi = PRICE_RANGE
    if not (lo <= pkg.base_price <= hi):
        errors.append(f"base_price {pkg.base_price} outside {PRICE_RANGE}")
    if pkg.speed_mbps is not None and not (SPEED_RANGE[0] <= pkg.speed_mbps <= SPEED_RANGE[1]):
        errors.append(f"speed_mbps {pkg.speed_mbps} outside {SPEED_RANGE}")
    if pkg.install_fee is not None and not (INSTALL_RANGE[0] <= pkg.install_fee <= INSTALL_RANGE[1]):
        errors.append(f"install_fee {pkg.install_fee} outside {INSTALL_RANGE}")
    allowed = set(Package.model_fields)
    for key in raw:
        if key not in allowed and key not in {"product", "evidence"}:
            errors.append(f"unexpected field: {key}")
    return (pkg if not errors else None), errors


def validate_catalog(data: dict) -> Catalog:
    return Catalog(**data)
