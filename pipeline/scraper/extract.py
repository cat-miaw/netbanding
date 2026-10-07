"""LLM extraction behind an interface. IDs are deterministic slugs, never LLM output."""
import json
import os
import re
from typing import Protocol

import httpx

EXTRACTION_PROMPT = """Extract home-internet packages from the page text below.
Return a JSON array only. Each item: {{"product": "<plan name as listed>",
"speed_mbps": <int or null>, "base_price": <int IDR as listed>,
"tax_inclusive": <true|false, NEVER null - fail the item if unclear>,
"device_rental_fee": <int/month, 0 if none>, "install_fee": <int one-time, 0 if free, null if unstated>,
"fup_note": <string or null>, "promo_note": <string or null>}}.

Rules: null for anything not explicitly stated. Never guess or infer.
Indonesian cues: 'belum termasuk PPN'/'exclude PPN' -> tax_inclusive=false;
'sudah termasuk PPN'/'include PPN' -> true; 'gratis instalasi'/'free pemasangan' -> install_fee=0;
'biaya pemasangan Rp X' -> install_fee=X; 'sewa perangkat/modem/router' -> device_rental_fee.

Also return an "evidence" object with short exact quotes backing tax_inclusive,
install_fee and base_price for each item, e.g. {{"0": {{"tax_inclusive": "belum termasuk PPN", ...}}}}.
Evidence is logged to runs/, never shipped to the app.
"""


def make_id(isp_id: str, product: str, speed_mbps: int | None) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", product.lower()).strip("-")
    speed = f"-{speed_mbps}" if speed_mbps else ""
    return f"{isp_id}-{slug}{speed}"


def normalize_package(
    isp_id: str, item: dict, index: int, source_url: str, today: str
) -> dict:
    """Map one LLM item to catalog shape. Deterministic id; LLM never invents it."""
    raw = dict(item)
    product = raw.pop("product", f"plan-{index}")
    raw["id"] = make_id(isp_id, product, raw.get("speed_mbps"))
    raw["name"] = product
    raw.setdefault("regions", ["JAVA_ALL"])
    raw.setdefault("source_url", source_url)
    raw.setdefault("last_verified_at", f"{today}T02:00:00Z")
    raw.setdefault("updated_at", f"{today}T02:00:00Z")
    return raw


class Extractor(Protocol):
    def extract(self, text: str) -> dict:
        """Return {{"packages": [...], "evidence": {{...}}, "tokens": int}}."""
        ...


class DeepSeekExtractor:
    def __init__(self, api_key: str | None = None, model: str = "deepseek-chat") -> None:
        self.api_key = api_key or os.environ.get("DEEPSEEK_API_KEY", "")
        self.model = model

    def extract(self, text: str) -> dict:
        resp = httpx.post(
            "https://api.deepseek.com/chat/completions",
            headers={"Authorization": f"Bearer {self.api_key}"},
            json={
                "model": self.model,
                "temperature": 0,
                "response_format": {"type": "json_object"},
                "messages": [
                    {"role": "system", "content": EXTRACTION_PROMPT},
                    {"role": "user", "content": text[:12000]},
                ],
            },
            timeout=120,
        )
        resp.raise_for_status()
        data = resp.json()
        content = data["choices"][0]["message"]["content"]
        parsed = json.loads(content)
        items = parsed if isinstance(parsed, list) else parsed.get("packages", [])
        raw_pkgs, evidence = [], {}
        for i, item in enumerate(items):
            ev = (parsed.get("evidence", {}) or {}).get(str(i), {})
            raw_pkgs.append(item)
            evidence[str(i)] = ev
        return {
            "packages": raw_pkgs,
            "evidence": evidence,
            "tokens": data.get("usage", {}).get("total_tokens", 0),
        }
