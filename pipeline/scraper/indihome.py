"""Deterministic extractor for IndiHome package cards (indihome.co.id).

The official site is server-rendered, so no JS engine and no LLM are needed:
every card is a rigid block

    <promo title>
    <speed> Mbps
    Mulai dari
    Rp<price>
    /bulan
    Pilih Paket

Card titles are PROMO TIER NAMES ("Double Speed - 500 Mbps Internet") and
deliberately do NOT match the speed printed underneath, so the numbered line
wins (confirmed by the maintainer 2026-10-09). Speed is therefore part of the
id: `indihome-<title slug>-<speed>`.

The page states NO PPN, install fee, rental or ONT anywhere (verified by
keyword scan 2026-10-09), so:
  * `tax_inclusive` is a CURATED constant in this file, not page evidence
    (False = ex-PPN, maintainer-confirmed). Never derive it from the page.
  * `install_fee` stays None -> contract "not stated" -> UI shows
    "Tanya provider". Never guessed.
  * `device_rental_fee` = 0 (ONT/modem included; the sales LP says so
    explicitly, the plan page stays silent) - watch item.

The telkomsel.com IndiHome LP is NOT usable: it renders packages from
`POST landingpage/api/lp/fmc/v1/get-package` behind a CryptoJS AES
`x-api-key` plus an install-location step (see PROJECT.md quirks ledger).
"""
import re

from scraper.extract import make_id

# title / speed / "Mulai dari" / price / "/bulan" / CTA, line by line.
CARD = re.compile(
    r"(?m)^(.+)\n([\d.,]+)\s*Mbps\nMulai dari\nRp([\d.]+)\n/bulan\nPilih Paket[ \t]*$"
)

# Curated: the plan page is silent on tax. Maintainer-confirmed ex-PPN.
TAX_INCLUSIVE = False

# Curated: IndiHome bundles the ONT/modem into the plan (stated on the LP).
DEVICE_RENTAL_FEE = 0


def parse_speed(s: str) -> int:
    s = s.strip()
    if "," in s:
        return round(float(s.replace(".", "").replace(",", ".")))
    return int(s.replace(".", ""))


def parse_idr(s: str) -> int:
    return int(s.replace(".", "").replace(",", ""))


class IndiHomeExtractor:
    """Same interface as DeepSeekExtractor; ids are deterministic, never guessed."""

    def extract(self, text: str) -> dict:
        pkgs, evidence, seen = [], {}, {}
        for m in CARD.finditer(text):
            title, speed_raw, price_raw = m.groups()
            title = title.strip()
            speed = parse_speed(speed_raw)
            price = parse_idr(price_raw)
            pkg_id = make_id("indihome", title, speed)
            if pkg_id in seen:
                # Same card repeats across sections ("Best Deal" + its tab).
                if seen[pkg_id] != price:
                    raise ValueError(
                        f"conflicting price for {pkg_id}: {seen[pkg_id]} vs {price}"
                    )
                continue
            seen[pkg_id] = price
            pkgs.append({
                "id": pkg_id,
                "name": title,
                "type": "broadband",
                "speed_mbps": speed,
                "quota_mb": None,
                "validity_days": None,
                "contract_months": None,
                "base_price": price,
                "tax_inclusive": TAX_INCLUSIVE,
                "device_rental_fee": DEVICE_RENTAL_FEE,
                "install_fee": None,
                "fup_note": None,
                "promo_note": None,
            })
            evidence[str(len(pkgs) - 1)] = {
                "base_price": f"Rp{price_raw.strip()}",
                "speed_mbps": f"{speed_raw.strip()} Mbps",
            }
        return {"packages": pkgs, "evidence": evidence, "tokens": 0}
