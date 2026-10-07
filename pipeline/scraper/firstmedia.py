"""Deterministic extractor for the FirstMedia reseller promo page.

The generic LLM prompt returns [] here (contradictory summary table +
detail blocks). This page's numbered blocks (1A-3C) are stable enough for
a selector-free regex parser: $0 forever, zero drift. Curates the 9 plans
the seed tracks; anything else on the page is ignored by design.
"""
import re

BLOCKS = {
    # code: (seed package id, display name)
    "1A": ("firstmedia-starter-20", "Internet Only Starter"),
    "1B": ("firstmedia-smart-250", "Internet Only Smart"),
    "1C": ("firstmedia-family-350", "Internet Only Family"),
    "1D": ("firstmedia-superuser-350", "Internet Only Superuser"),
    "1E": ("firstmedia-ultraspeed-600", "Internet Ultra Speed"),
    "1F": ("firstmedia-extreme-1000", "Internet Only Extreme"),
    "2A": ("firstmedia-joy-value-100", "Combo Joy Value"),
    "2B": ("firstmedia-joy-pro-150", "Combo Joy Pro"),
    "3A": ("firstmedia-star-value-300", "Combo Star Value"),
}

HEADER = re.compile(r"(?m)^([123][A-F])\.\s*(.*)$", re.IGNORECASE)
PRICE = re.compile(r"Harga sebelum PPN\s*:?\s*Rp\.?\s*([\d\.]+)")
SPEED = re.compile(r"Internet Speed Up to\s*([\d\.]+)\s*Mbps", re.IGNORECASE)
INSTALL = re.compile(r"Biaya Pemasangan\s*Rp\.?\s*([\d\.]+)")
CONTRACT = re.compile(r"Kontrak Berlangganan Selama\s*(\d+)\s*Bulan", re.IGNORECASE)


def parse_idr(s: str) -> int:
    return int(s.replace(".", "").replace(",", ""))


class FirstMediaExtractor:
    """Same interface as DeepSeekExtractor; ids are curated, never guessed."""

    def extract(self, text: str) -> dict:
        heads = list(HEADER.finditer(text))
        pkgs, evidence = [], {}
        for n, m in enumerate(heads):
            code = m.group(1).upper()
            if code not in BLOCKS:
                continue
            chunk = text[m.start():heads[n + 1].start() if n + 1 < len(heads) else len(text)]
            price_m = PRICE.search(chunk)
            speed_m = SPEED.search(chunk)
            if not price_m or not speed_m:
                continue
            pkg_id, name = BLOCKS[code]
            install_m = INSTALL.search(chunk)
            free = re.search(r"FREE BIAYA PASANG|free instalasi", chunk, re.IGNORECASE)
            install = 0 if free else (
                parse_idr(install_m.group(1)) if install_m else None)
            contract_m = CONTRACT.search(chunk)
            pkgs.append({
                "id": pkg_id,
                "name": name,
                "type": "broadband",
                "speed_mbps": int(parse_idr(speed_m.group(1))),
                "base_price": parse_idr(price_m.group(1)),
                "tax_inclusive": False,
                "device_rental_fee": 0,
                "install_fee": install,
                "contract_months": int(contract_m.group(1)) if contract_m else None,
                "fup_note": None,
                "promo_note": ("Kontrak 12 bulan; penalti Rp1jt bila berhenti sebelum 12 bulan."
                               if contract_m else None),
            })
            evidence[str(len(pkgs) - 1)] = {
                "base_price": price_m.group(0).strip(),
                "speed": speed_m.group(0).strip(),
                "tax_inclusive": "Harga sebelum PPN",
            }
        return {"packages": pkgs, "evidence": evidence, "tokens": 0}
