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

# Packs whose titles carry no identity (e.g. Akrab Mini "7 GB") get curated
# ids/names keyed by (tab, title). Everything else slugs from the title.
AKRAB_IDS = {
    ("Reguler", "Akrab S"): ("xl-akrab-s", "Akrab S"),
    ("Reguler", "Akrab SM"): ("xl-akrab-sm", "Akrab SM"),
    ("Reguler", "Akrab M"): ("xl-akrab-m", "Akrab M"),
    ("Reguler", "Akrab L"): ("xl-akrab-l", "Akrab L"),
    ("Mini", "7 GB"): ("xl-akrab-mini-7gb", "Akrab Mini 7GB"),
    ("Mini", "17 GB"): ("xl-akrab-mini-17gb", "Akrab Mini 17GB"),
    ("Mini", "34 GB"): ("xl-akrab-mini-34gb", "Akrab Mini 34GB"),
}

QUOTA_KEYS = ("Utama", "Pribadi")

wants_raw = True


def _gb(value: str) -> int:
    m = GB.search(value or "")
    if not m:
        raise ValueError(f"no GB in {value!r}")
    num = m.group(1).strip()
    if "," in num:
        num = num.replace(".", "").replace(",", ".")  # Indonesian decimal
    elif re.fullmatch(r"\d{1,3}(\.\d{3})+", num):
        num = num.replace(".", "")  # thousands separator
    return round(float(num) * 1024)


def _days(value: str) -> int:
    m = DAYS.search(value or "")
    if not m:
        raise ValueError(f"no Hari in {value!r}")
    return int(m.group(1))


def _slug(title: str) -> str:
    # A trailing "+" is a meaningful tier marker (M vs M+); mid-string "+"
    # (400GB+400GB) is left alone so established ids never churn.
    title = re.sub(r"\s*\+\s*$", "-plus", title)
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
            tab_name = (tab.get("tabName") or "").strip()
            for card in tab.get("data", []) or []:
                ben = {b.get("benefitType", ""): b.get("benefitValue", "")
                       for b in card.get("benefit", []) or []}
                quota_raw = next(
                    (v for t, v in ben.items()
                     if any(k in t for k in QUOTA_KEYS)), None)
                valid_raw = next((v for t, v in ben.items() if "Aktif" in t), None)
                if not quota_raw or not valid_raw:
                    continue
                quota, validity = _gb(quota_raw), _days(valid_raw)
                title = (card.get("title") or "").strip()
                if (tab_name, title) in AKRAB_IDS:
                    pid, name = AKRAB_IDS[(tab_name, title)]
                else:
                    pid = f"xl-{_slug(title)}-{validity}d"
                    name = title
                shared = next((v for t, v in ben.items()
                               if "Bersama" in t or "Anggota" in t), None)
                total = next((v for t, v in ben.items() if "Total Kuota" in t), None)
                if shared or total:
                    bonus = f"Kuota bersama{f' ({shared})' if shared and 'orang' in shared else ''}."
                else:
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
