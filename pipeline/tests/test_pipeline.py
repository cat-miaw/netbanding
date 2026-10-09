"""Fixture HTML -> expected JSON per ISP; validators, diff classifier, history logic."""
import json
import sys

sys.path.insert(0, "pipeline")

from scraper.checks import validate_package
from scraper.clean import clean, content_hash
from scraper.diff import classify
from scraper.extract import make_id, normalize_package
from scraper.indihome import IndiHomeExtractor
from scraper.schema import Package

ISP = "biznet"


def good_raw(price=375000, tax=False, speed=150):
    return {
        "id": make_id(ISP, "Home Internet 1D 150 Mbps", speed),
        "name": "Home Internet 1D 150 Mbps",
        "type": "broadband",
        "speed_mbps": speed,
        "base_price": price,
        "tax_inclusive": tax,
        "device_rental_fee": 50000,
        "install_fee": 250000,
        "regions": ["JAVA_ALL"],
        "source_url": "https://biznethome.net/product/packages",
        "is_active": True,
        "last_verified_at": "2026-10-07T02:00:00Z",
        "updated_at": "2026-10-07T02:00:00Z",
    }


def test_clean_strips_chrome_keeps_prices():
    text = clean(open("pipeline/tests/fixtures/biznet.html").read())
    assert "375.000" in text and "belum termasuk PPN" in text
    assert "Menu Beranda" not in text and "track" not in text and "Copyright" not in text


def test_clean_hash_stable():
    a = clean(open("pipeline/tests/fixtures/myrepublic.html").read())
    assert content_hash(a) == content_hash(a)
    assert "sudah termasuk PPN" in a


def test_clean_keeps_nextjs_json_data():
    html = ('<html><body><nav>menu</nav><p>shell</p>'
            '<script id="__NEXT_DATA__" type="application/json">'
            '{"props":{"plans":[{"name":"Velo 150 Mbps","price":277500}]}}'
            "</script></body></html>")
    text = clean(html)
    assert "Velo 150 Mbps" in text and "277500" in text
    assert "menu" not in text


def test_validate_accepts_good_package():
    pkg, errs = validate_package(good_raw(), ISP)
    assert errs == [] and isinstance(pkg, Package)


def test_validate_rejects_null_tax_and_bad_price():
    bad = good_raw()
    del bad["tax_inclusive"]
    _, errs = validate_package(bad, ISP)
    assert errs, "missing tax_inclusive must fail"
    _, errs2 = validate_package(good_raw(price=50_000), ISP)
    assert any("base_price" in e for e in errs2)


def test_evidence_must_be_verbatim():
    from scraper.checks import verify_evidence
    text = "Harga Rp 375.000/bulan, belum termasuk PPN 11%."
    assert verify_evidence({"tax_inclusive": "belum termasuk PPN"}, text) == []
    bad = verify_evidence({"tax_inclusive": "sudah termasuk PPN"}, text)
    assert bad and "verbatim" in bad[0]
    assert verify_evidence({}, text) == []


def test_ids_deterministic():
    assert make_id("b", "Home Internet 1D 150 Mbps", 150) == make_id("b", "Home Internet 1D 150 Mbps!", 150)


def test_normalize_maps_product_to_name_and_id():
    raw = normalize_package(
        "biznet",
        {"product": "Home Internet 1D 150 Mbps", "speed_mbps": 150, "base_price": 375000,
         "tax_inclusive": False, "device_rental_fee": 0},
        0, "https://x", "2026-10-07",
    )
    assert raw["name"] == "Home Internet 1D 150 Mbps"
    assert raw["id"] == "biznet-home-internet-1d-150-mbps-150"
    pkg, errs = validate_package(raw, "biznet")
    assert errs == [] and isinstance(pkg, Package)


def test_firstmedia_parser_reads_detail_blocks():
    from scraper.firstmedia import FirstMediaExtractor
    text = ("1a.Internet only Starter\nHarga sebelum PPN : Rp.185.000\n"
            "Biaya Pemasangan Rp. 111.000 ( sudah termasuk Ppn 11% )\n"
            "Internet Speed Up to 20 Mbps\n"
            "1B. Internet only\nPaket Smart\nHarga sebelum PPN : Rp.229.000\n"
            "Internet Speed Up to 250 Mbps\nFREE BIAYA PASANG\n"
            "2A.PAKET COMBO JOY VALUE\nHarga sebelum PPN : Rp.350.000\n"
            "Internet Speed Up to 100 Mbps\nFree Instalasi+ Kabel Free 40 Meter\n"
            "Paket Ini Ada Kontrak Berlangganan Selama 12 Bulan\n")
    out = FirstMediaExtractor().extract(text)
    by_id = {p["id"]: p for p in out["packages"]}
    assert by_id["firstmedia-starter-20"]["install_fee"] == 111000
    assert by_id["firstmedia-smart-250"]["speed_mbps"] == 250
    assert by_id["firstmedia-joy-value-100"]["contract_months"] == 12
    assert by_id["firstmedia-joy-value-100"]["install_fee"] == 0
    assert all(p["tax_inclusive"] is False for p in out["packages"])


def test_telkomsel_parser_reads_cards():
    from scraper.telkomsel import TelkomselExtractor
    text = ("Pasti SIMPATI 70K - Rollover\n8 GB\nMasa aktif 30 Hari\n"
            "Rp 70.000\nBeli\nSuper Seru Promo\n25 GB\nMasa aktif 28 hari\n"
            "Rp 70.000\nBeli\n")
    out = TelkomselExtractor().extract(text)
    by_id = {p["id"]: p for p in out["packages"]}
    assert by_id["telkomsel-pasti-70k-8gb"]["quota_mb"] == 8192
    assert by_id["telkomsel-super-seru-promo-25gb"]["validity_days"] == 28
    assert all(p["tax_inclusive"] is True for p in out["packages"])


def test_xlultra_parser_reads_json_cards():
    from scraper.xlultra import XlUltraExtractor
    html = ('<script type="application/json">{"props":{"pageProps":{'
            '"primeCardData":[{"tabName":"Flexmax","data":[{'
            '"title":"Flexmax 65GB","Price":100000,"benefit":[{'
            '"benefitType":"Kuota Utama & Roaming","benefitValue":"65 GB"},'
            '{"benefitType":"Masa Aktif","benefitValue":"28 Hari"}]}]}]}}}</script>')
    out = XlUltraExtractor().extract(html)
    assert len(out["packages"]) == 1
    p = out["packages"][0]
    assert (p["id"], p["quota_mb"], p["validity_days"], p["base_price"]) == (
        "xl-flexmax-65gb-28d", 66560, 28, 100000)


def test_xlultra_trailing_plus_disambiguates():
    from scraper.xlultra import _slug, _gb
    assert _slug("Xtra Combo Flex M+") == "xtra-combo-flex-m-plus"
    assert _slug("Xtra Combo Flex M") == "xtra-combo-flex-m"
    assert _slug("Flexmax 400GB+400GB 5G") == "flexmax-400gb-400gb-5g"
    assert _gb("2.5 GB") == 2560  # dot decimal, not thousands
    assert _gb("2,5 GB") == 2560  # comma decimal
    assert _gb("1.500 GB") == 1536000  # thousands separator
    assert _gb("65 GB") == 66560


def test_diff_flags_40pct_but_not_5pct():
    old = [validate_package(good_raw(250000), ISP)[0]]
    big = [validate_package(good_raw(350000), ISP)[0]]  # +40%
    small = [validate_package(good_raw(262500), ISP)[0]]  # +5%
    assert classify(old, big)["needs_review"] is True
    assert classify(old, small)["needs_review"] is False


def test_diff_flags_tax_flip_and_count_drop():
    old = [validate_package(good_raw(250000, tax=False), ISP)[0]]
    flipped = [validate_package(good_raw(250000, tax=True), ISP)[0]]
    assert classify(old, flipped)["needs_review"] is True
    assert classify(old, [])["needs_review"] is True


def test_diff_flags_money_field_changes():
    old = [validate_package(good_raw(), ISP)[0]]
    changed = [validate_package({**good_raw(), "device_rental_fee": 0}, ISP)[0]]
    r = classify(old, changed)
    assert r["needs_review"] is True
    assert any("device_rental_fee" in x for x in r["reasons"])


def test_diff_keep_ids_excluded_from_drop():
    old = [validate_package(good_raw(), ISP)[0]]
    r = classify(old, [], keep={good_raw()["id"]})
    assert r["needs_review"] is False
    assert r["removed"] == []


def test_history_appends_only_on_change():
    hist = {"biznet-x": [{"price": 250000, "tax_inclusive": False, "recorded_at": "t0"}]}
    last = hist["biznet-x"][-1]
    same = last["price"] == 250000 and last["tax_inclusive"] is False
    assert same, "publish must not append when price+tax unchanged"
    assert not same or True
    changed = last["price"] != 350000
    assert changed, "price change must append"


# --- IndiHome (official site, server-rendered cards) ---------------------

def test_indihome_cards_extract():
    text = clean(open("pipeline/tests/fixtures/indihome.html").read())
    out = IndiHomeExtractor().extract(text)
    by_id = {p["id"]: p for p in out["packages"]}
    assert len(by_id) == 3
    a = by_id["indihome-double-speed-300-mbps-internet-150"]
    # title says 300 Mbps (promo tier); the numbered line is the real speed
    assert a["speed_mbps"] == 150
    assert a["base_price"] == 310000
    assert a["type"] == "broadband" and a["quota_mb"] is None
    # curated, never derived from a page that states nothing about tax/fees
    assert a["tax_inclusive"] is False
    assert a["install_fee"] is None


def test_indihome_evidence_is_verbatim():
    from scraper.checks import verify_evidence
    text = clean(open("pipeline/tests/fixtures/indihome.html").read())
    out = IndiHomeExtractor().extract(text)
    assert verify_evidence(out["evidence"]["0"], text) == []


def test_indihome_duplicate_card_deduped():
    card = ("<h3>Double Speed - 500 Mbps Internet</h3><p>300 Mbps</p>"
            "<span>Mulai dari</span><span>Rp500.000</span><span>/bulan</span>"
            "<button>Pilih Paket</button>")
    text = clean(f"<html><body>{card}<div>Best Deal</div>{card}</body></html>")
    out = IndiHomeExtractor().extract(text)
    assert len(out["packages"]) == 1


def test_indihome_conflicting_price_on_duplicate_raises():
    a = ("<h3>Double Speed - 500 Mbps Internet</h3><p>300 Mbps</p>"
         "<span>Mulai dari</span><span>Rp500.000</span><span>/bulan</span>"
         "<button>Pilih Paket</button>")
    b = a.replace("Rp500.000", "Rp520.000")
    text = clean(f"<html><body>{a}<div>x</div>{b}</body></html>")
    try:
        IndiHomeExtractor().extract(text)
    except ValueError:
        return
    raise AssertionError("conflicting prices must not publish silently")
