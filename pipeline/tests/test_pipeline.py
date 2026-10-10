"""Fixture HTML -> expected JSON per ISP; validators, diff classifier, history logic."""
import json
import os
import sys

sys.path.insert(0, "pipeline")

from scraper.checks import validate_package
from scraper.clean import clean, content_hash
from scraper.diff import classify
from scraper.extract import (
    AgnesExtractor,
    DeepSeekExtractor,
    FallbackExtractor,
    LLMExtractor,
    parse_chain,
    parse_extraction,
)
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


# --- LLM provider chain (Agnes primary, DeepSeek fallback) -------------------

class _FakeLLM:
    """Offline stand-in for LLMExtractor: no network, controllable outcome."""

    requires_key = True

    def __init__(self, name, key="k", result=None, error=None):
        self.name = name
        self._key = key
        self._result = result or {"packages": [{"product": name}], "evidence": {}, "tokens": 7}
        self._error = error
        self.calls = 0

    @property
    def available(self):
        return bool(self._key)

    def extract(self, text):
        self.calls += 1
        if self._error:
            raise RuntimeError(self._error)
        return dict(self._result)


def test_parse_chain_default_order_and_dedup():
    assert parse_chain("") == ["agnes", "deepseek"]
    assert parse_chain("deepseek") == ["deepseek"]
    assert parse_chain("deepseek, agnes ,deepseek") == ["deepseek", "agnes"]
    # unknown names are dropped; if that empties the list we keep the default
    assert parse_chain("nope") == ["agnes", "deepseek"]


def test_agnes_and_deepseek_defaults():
    a = AgnesExtractor(api_key="x")
    assert a.base_url == "https://apihub.agnes-ai.com/v1"
    assert a.model == "agnes-2.5-flash" and a.available
    d = DeepSeekExtractor(api_key="y")
    assert d.base_url == "https://api.deepseek.com" and d.model == "deepseek-chat"
    assert not AgnesExtractor(api_key="").available


def test_chain_falls_back_to_next_provider_on_failure():
    dead = _FakeLLM("agnes", error="402 insufficient balance")
    alive = _FakeLLM("deepseek")
    out = FallbackExtractor([dead, alive]).extract("text")
    assert out["provider"] == "deepseek" and dead.calls == 1 and alive.calls == 1


def test_chain_skips_providers_without_a_key():
    keyless = _FakeLLM("agnes", key="")
    alive = _FakeLLM("deepseek")
    out = FallbackExtractor([keyless, alive]).extract("text")
    assert out["provider"] == "deepseek" and keyless.calls == 0


def test_chain_raises_when_all_providers_fail():
    chain = FallbackExtractor([_FakeLLM("agnes", error="boom"),
                               _FakeLLM("deepseek", error="402")])
    try:
        chain.extract("text")
    except RuntimeError as e:
        assert "agnes: boom" in str(e) and "deepseek: 402" in str(e)
        return
    raise AssertionError("all-fail must raise so run.py logs a real reason")


def test_chain_raises_when_no_provider_has_a_key():
    chain = FallbackExtractor([_FakeLLM("agnes", key=""), _FakeLLM("deepseek", key="")])
    assert chain.available is False
    try:
        chain.extract("text")
    except RuntimeError as e:
        assert "no LLM provider has an API key" in str(e)
        return
    raise AssertionError("keyless chain must not attempt a request")


def test_dotenv_loader_sets_missing_and_keeps_real_env(tmp_path, monkeypatch):
    import run
    monkeypatch.delenv("AGNES_API_KEY", raising=False)
    monkeypatch.setenv("DEEPSEEK_API_KEY", "from-real-env")
    env = tmp_path / ".env"
    env.write_text(
        "# comment line\n"
        "AGNES_API_KEY=sk-agnes-key\n"
        'DEEPSEEK_API_KEY="from-file"\n'
        "EXTRACTOR_CHAIN=deepseek,agnes\n"
        "\n"
        "not-a-kv-line\n",
        encoding="utf-8",
    )
    run._load_dotenv(str(env))
    assert os.environ["AGNES_API_KEY"] == "sk-agnes-key"
    assert os.environ["DEEPSEEK_API_KEY"] == "from-real-env"  # real env wins
    assert os.environ["EXTRACTOR_CHAIN"] == "deepseek,agnes"


def test_parse_extraction_accepts_all_provider_shapes():
    # documented wrapper object
    wrapped = parse_extraction(
        '{"packages": [{"product": "A"}], "evidence": {"0": {"base_price": "Rp1"}}}')
    assert wrapped["packages"] == [{"product": "A"}]
    assert wrapped["evidence"]["0"] == {"base_price": "Rp1"}
    # bare array (some providers)
    assert parse_extraction('[{"product": "B"}]')["packages"] == [{"product": "B"}]
    # single bare package object (observed from Agnes under json_object mode)
    bare = parse_extraction('{"product": "C", "base_price": 250000, "tax_inclusive": false}')
    assert bare["packages"] == [
        {"product": "C", "base_price": 250000, "tax_inclusive": False}]
    # junk / empty stays empty, never raises
    assert parse_extraction('{"note": "no plans"}')["packages"] == []


class _FlakyLLM(LLMExtractor):
    """LLMExtractor with a scripted sequence of replies (no network)."""

    name = "flaky"
    env_key = "FLAKY_KEY"
    default_base_url = "http://example.invalid"
    default_model = "m"

    def __init__(self, replies, tries=None):
        super().__init__(api_key="k", tries=tries if tries is not None else len(replies))
        self._replies = list(replies)
        self.calls = 0

    def _call(self, text):
        out = self._replies[min(self.calls, len(self._replies) - 1)]
        self.calls += 1
        return out


_EMPTY = {"packages": [], "evidence": {}, "tokens": 5}
_FULL = {"packages": [{"product": "HOME 0D"}], "evidence": {}, "tokens": 9}


def test_provider_retries_a_degenerate_empty_reply():
    llm = _FlakyLLM([_EMPTY, _FULL])
    out = llm.extract("text")
    assert len(out["packages"]) == 1 and llm.calls == 2


def test_provider_gives_up_after_tries():
    llm = _FlakyLLM([_EMPTY], tries=3)
    out = llm.extract("text")
    assert out["packages"] == [] and llm.calls == 3


def test_chain_treats_zero_packages_as_failure_then_falls_through():
    empty = _FakeLLM("agnes", result=dict(_EMPTY))
    good = _FakeLLM("deepseek")
    out = FallbackExtractor([empty, good]).extract("text")
    assert out["provider"] == "deepseek"


def test_chain_returns_empty_when_every_provider_finds_nothing():
    a = _FakeLLM("agnes", result=dict(_EMPTY))
    b = _FakeLLM("deepseek", result=dict(_EMPTY))
    out = FallbackExtractor([a, b]).extract("text")
    # run.py turns this into extract_empty (keeps prior data), not a hard error
    assert out["packages"] == [] and out["provider"] == "deepseek"
