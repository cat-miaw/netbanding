"""Deterministic extractor for XL family pages (here: xl-ultra-5g).

These Next.js pages embed their entire catalog as JSON
(`primeCardData` tabs: Flexmax/Flexmini) with integer prices and typed
benefit rows — far more reliable than text scraping. Evidence quotes still
come from the cleaned page text so the verbatim gate keeps working.
"""
import json
import re

JSON_SCRIPT = re.compile(
    r'<script[^>]*type="application/json"[^>]*>(.*?)</script>', re.S)
GB = re.compile(r"([\d\.,]+)\s*GB", re.IGNORECASE)
DAYS = re.compile(r"(\d+)\s*Hari", re.IGNORECASE)
PPN_LINE = "Harga Produk yang tertera sudah termasuk PPN"

wants_raw = True


def _gb(value: str) -> int:
    m = GB.search(value or "")
    if not m:
        raise ValueError(f"no GB in {value!r}")
    s = m.group(1).replace(".", "")
    gb = float(s.replace(",", ".")) if "," in s else int(s)
    return round(gb * 1024)


def _days(value: str) -> int:
    m = DAYS.search(value or "")
    if not m:
        raise ValueError(f"no Hari in {value!r}")
    return int(m.group(1))


def _slug(title: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")


class XlUltraExtractor:
    """Same interface as DeepSeekExtractor; takes RAW html (wants_raw)."""

    wants_raw = True

    def extract(self, html: str) -> dict:
        m = JSON_SCRIPT.search(html)
        if not m:
            return {"packages": [], "evidence": {}, "tokens": 0}
        data = json.loads(m.group(1))
        pkgs, evidence = [], {}
        for tab in data["props"]["pageProps"].get("primeCardData", []):
            for card in tab.get("data", []) or []:
                ben = {b.get("benefitType", ""): b.get("benefitValue", "")
                       for b in card.get("benefit", []) or []}
                quota_raw = next((v for t, v in ben.items() if "Utama" in t), None)
                valid_raw = next((v for t, v in ben.items() if "Aktif" in t), None)
                if not quota_raw or not valid_raw:
                    continue
                quota, validity = _gb(quota_raw), _days(valid_raw)
                title = (card.get("title") or "").strip()
                pid = f"xl-{_slug(title)}-{validity}d"
                bonus = "Bonus kuota 5G." if "+" in title else None
                pkgs.append({
                    "id": pid,
                    "name": title,
                    "type": "cellular",
                    "speed_mbps": None,
                    "quota_mb": quota,
                    "validity_days": validity,
                    "base_price": int(card["Price"]),
                    "tax_inclusive": True,
                    "device_rental_fee": 0,
                    "install_fee": None,
                    "contract_months": None,
                    "fup_note": None,
                    "promo_note": bonus,
                })
                evidence[str(len(pkgs) - 1)] = {
                    "base_price": title,
                    "tax_inclusive": PPN_LINE,
                }
        return {"packages": pkgs, "evidence": evidence, "tokens": 0}
