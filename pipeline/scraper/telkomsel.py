"""Deterministic extractor for Telkomsel /simpati recommendation cards.

Card blocks are rigidly structured (name, quota, validity, price, "Beli").
Curated id map: only the 6 recommendation packs are tracked; anything else
(Hot Promo, banners) is ignored by design. The number-gated
shops/express-purchase flow is unscrapable (skeleton loaders without a
SIMPATI number) and stays out of scope.
"""
import re

CARD = re.compile(
    r"(?m)^(.+)\n([\d\.,]+)\s*GB\nMasa aktif\s*(\d+)\s*[Hh]ari\nRp\s*([\d\.]+)\nBeli\s*$"
)

IDS = {
    ("Pasti SIMPATI 70K - Rollover", 8192): "telkomsel-pasti-70k-8gb",
    ("Pasti SIMPATI 100K - Rollover", 13312): "telkomsel-pasti-100k-13gb",
    ("Super Seru Internet", 13312): "telkomsel-super-seru-13gb",
    ("Super Seru Promo", 25600): "telkomsel-super-seru-promo-25gb",
    ("Seru 5G", 6144): "telkomsel-seru-5g-6gb",
    ("Seru 5G", 15360): "telkomsel-seru-5g-15gb",
}

NAMES = {
    "telkomsel-pasti-70k-8gb": "Pasti SIMPATI 70K",
    "telkomsel-pasti-100k-13gb": "Pasti SIMPATI 100K",
    "telkomsel-super-seru-13gb": "Super Seru Internet",
    "telkomsel-super-seru-promo-25gb": "Super Seru Promo",
    "telkomsel-seru-5g-6gb": "Seru 5G 6GB",
    "telkomsel-seru-5g-15gb": "Seru 5G 15GB",
}


def parse_gb(s: str) -> int:
    s = s.strip().replace(".", "")
    gb = float(s.replace(",", ".")) if "," in s else int(s)
    return round(gb * 1024)


def parse_idr(s: str) -> int:
    return int(s.replace(".", "").replace(",", ""))


class TelkomselExtractor:
    """Same interface as DeepSeekExtractor; ids are curated, never guessed."""

    def extract(self, text: str) -> dict:
        pkgs, evidence = [], {}
        for m in CARD.finditer(text):
            name, gb_raw, days_raw, price_raw = m.groups()
            quota = parse_gb(gb_raw)
            pkg_id = IDS.get((name.strip(), quota))
            if pkg_id is None:
                continue
            validity = int(days_raw)
            price = parse_idr(price_raw)
            pkgs.append({
                "id": pkg_id,
                "name": NAMES[pkg_id],
                "type": "cellular",
                "speed_mbps": None,
                "quota_mb": quota,
                "validity_days": validity,
                "base_price": price,
                "tax_inclusive": True,
                "device_rental_fee": 0,
                "install_fee": None,
                "contract_months": None,
                "fup_note": None,
                "promo_note": None,
            })
            evidence[str(len(pkgs) - 1)] = {
                "base_price": f"Rp {price_raw.strip()}",
                "tax_inclusive": "Harga sudah termasuk PPN.",
            }
        return {"packages": pkgs, "evidence": evidence, "tokens": 0}
