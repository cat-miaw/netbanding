"""LLM extraction behind an interface. IDs are deterministic slugs, never LLM output.

Providers are OpenAI-compatible `/chat/completions` endpoints (DeepSeek, Agnes AI).
`FallbackExtractor` walks them in order and returns the first success, so an
exhausted or flaky provider never blocks the weekly run. Order comes from
`EXTRACTOR_CHAIN` (default "agnes,deepseek"); providers without an API key are
skipped, and a pinned provider can still be selected per ISP in sources.yaml
(`extractor: agnes` / `extractor: deepseek`).
"""
import json
import os
import re
from typing import Protocol

import httpx

EXTRACTION_PROMPT = """Extract ALL home-internet packages from the page text below.
Return ONE JSON object only, with exactly these two keys:
{"packages": [ ... ], "evidence": { ... }}.
"packages" MUST be an array holding ONE object per plan on the page (never a
single bare object, never a nested object). Each package object:
{"product": "<plan name as listed>",
"speed_mbps": <int or null>, "base_price": <int IDR as listed>,
"tax_inclusive": <true|false, NEVER null - fail the item if unclear>,
"device_rental_fee": <int/month, 0 if none>, "install_fee": <int one-time, 0 if free, null if unstated>,
"fup_note": <string or null>, "promo_note": <string or null>}.

Rules: null for anything not explicitly stated. Never guess or infer.
Plan cards sometimes repeat the same speed for every tier (placeholder).
If all tiers show an identical speed, look for a comparison/spec table
(a Bandwidth/Mbps row or per-plan detail sections) and use the per-tier
values from there. Record quota/FUP figures into fup_note (e.g. "FUP 1500 GB").
If a summary table contradicts detailed plan blocks on speed or price,
prefer the detailed blocks.
Indonesian cues: 'belum termasuk PPN'/'exclude PPN' -> tax_inclusive=false;
'sudah termasuk PPN'/'include PPN' -> true; 'gratis instalasi'/'free pemasangan' -> install_fee=0;
'biaya pemasangan Rp X' -> install_fee=X; 'sewa perangkat/modem/router' -> device_rental_fee.

"evidence" maps each package's array index (as a string: "0", "1", ...) to
short exact quotes backing tax_inclusive, install_fee and base_price, e.g.
{"0": {"tax_inclusive": "belum termasuk PPN", ...}}.
Evidence is logged to runs/, never shipped to the app.
"""


def make_id(isp_id: str, product: str, speed_mbps: int | None) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", product.lower()).strip("-")
    speed = f"-{speed_mbps}" if speed_mbps else ""
    return f"{isp_id}-{slug}{speed}"


def normalize_package(
    isp_id: str, item: dict, index: int, source_url: str, today: str,
    regions: list | None = None,
) -> dict:
    """Map one LLM item to catalog shape. Deterministic id; LLM never invents it."""
    raw = dict(item)
    if "id" not in raw:
        product = raw.pop("product", f"plan-{index}")
        raw["id"] = make_id(isp_id, product, raw.get("speed_mbps"))
        raw["name"] = product
    raw["regions"] = raw.get("regions") or regions or ["JAVA_ALL"]
    raw.setdefault("source_url", source_url)
    raw.setdefault("last_verified_at", f"{today}T02:00:00Z")
    raw.setdefault("updated_at", f"{today}T02:00:00Z")
    return raw


class Extractor(Protocol):
    def extract(self, text: str) -> dict:
        """Return {{"packages": [...], "evidence": {{...}}, "tokens": int}}."""
        ...


def parse_extraction(content: str) -> dict:
    """Normalize a provider's JSON reply into {"packages": [...], "evidence": {}}.

    The documented shape is the wrapper object {{"packages": [...], "evidence": {...}}}.
    Providers vary though: some emit a bare array, and some (seen with Agnes)
    emit a single bare package object when the page looks like one plan. Accept
    all three so a provider swap never silently yields zero packages.
    """
    parsed = json.loads(content)
    if isinstance(parsed, list):
        return {"packages": parsed, "evidence": {}}
    if isinstance(parsed, dict):
        pkgs = parsed.get("packages")
        if isinstance(pkgs, list):
            return {"packages": pkgs, "evidence": parsed.get("evidence") or {}}
        if any(k in parsed for k in ("product", "base_price", "speed_mbps", "id")):
            return {"packages": [parsed], "evidence": parsed.get("evidence") or {}}
    return {"packages": [], "evidence": {}}


class LLMExtractor:
    """OpenAI-compatible /chat/completions provider (strict JSON, temperature 0)."""

    name = "llm"
    requires_key = True
    env_key = ""
    env_model = ""
    default_base_url = ""
    default_model = ""

    def __init__(
        self,
        api_key: str | None = None,
        model: str | None = None,
        base_url: str | None = None,
        tries: int | None = None,
    ) -> None:
        self.api_key = api_key or os.environ.get(self.env_key, "")
        self.model = model or os.environ.get(self.env_model, "") or self.default_model
        self.base_url = (base_url or self.default_base_url).rstrip("/")
        self.tries = tries if tries is not None else int(os.environ.get("LLM_TRIES", "2"))

    @property
    def available(self) -> bool:
        return bool(self.api_key)

    def extract(self, text: str) -> dict:
        """Call the provider, retrying a degenerate (0-package) reply.

        Free-tier backends are non-deterministic even at temperature 0: a call
        occasionally returns an empty wrapper or one bare object. A second
        identical call usually comes back complete, so retry before giving up
        (the pipeline's hash-skip means this costs nothing on unchanged pages).
        """
        out: dict = {"packages": [], "evidence": {}, "tokens": 0}
        for _ in range(max(1, self.tries)):
            out = self._call(text)
            if out["packages"]:
                break
        return out

    def _call(self, text: str) -> dict:
        resp = httpx.post(
            f"{self.base_url}/chat/completions",
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
        parsed = parse_extraction(content)
        raw_pkgs = parsed["packages"]
        ev_all = parsed["evidence"]
        evidence = {str(i): ev_all.get(str(i), {}) for i in range(len(raw_pkgs))}
        return {
            "packages": raw_pkgs,
            "evidence": evidence,
            "tokens": data.get("usage", {}).get("total_tokens", 0),
        }


class DeepSeekExtractor(LLMExtractor):
    """DeepSeek (api.deepseek.com). Primary until credit ran out 2026-10-11."""

    name = "deepseek"
    env_key = "DEEPSEEK_API_KEY"
    env_model = "DEEPSEEK_MODEL"
    default_base_url = "https://api.deepseek.com"
    default_model = "deepseek-chat"


class AgnesExtractor(LLMExtractor):
    """Agnes AI (OpenAI-compatible, free tier). https://agnes-ai.com/en/docs/overview

    Model choice (probed 2026-10-11 on the Biznet page, 4 trials each):
    `agnes-2.5-flash` returned all 4 plans 4/4; `agnes-3.0-flash` returned an
    empty array 3/4; the pro / flash-max tiers are HTTP 403 (not in free quota).
    """

    name = "agnes"
    env_key = "AGNES_API_KEY"
    env_model = "AGNES_MODEL"
    default_base_url = "https://apihub.agnes-ai.com/v1"
    default_model = "agnes-2.5-flash"


PROVIDERS = {
    "agnes": AgnesExtractor,
    "deepseek": DeepSeekExtractor,
}
DEFAULT_CHAIN = "agnes,deepseek"


def parse_chain(spec: str | None = None) -> list[str]:
    """Resolve the provider order: explicit arg > EXTRACTOR_CHAIN env > default."""
    raw = spec if spec is not None else os.environ.get("EXTRACTOR_CHAIN") or DEFAULT_CHAIN
    order: list[str] = []
    for name in (n.strip().lower() for n in raw.split(",")):
        if name in PROVIDERS and name not in order:
            order.append(name)
    return order or DEFAULT_CHAIN.split(",")


class FallbackExtractor:
    """Try each configured provider in order; the first usable success wins.

    Keyless providers are skipped, so a single Agnes key is enough to run. A
    provider that raises OR returns zero packages counts as a failure and the
    next provider is tried. If all providers raise, raise with each error so
    run.py records a real reason per ISP; if all merely found nothing, return
    that empty result so run.py keeps the previous data (extract_empty).
    """

    name = "llm"
    requires_key = True

    def __init__(self, providers: list | None = None) -> None:
        self.providers = providers if providers is not None else [PROVIDERS[n]() for n in parse_chain()]

    @property
    def available(self) -> bool:
        return any(p.available for p in self.providers)

    @property
    def chain(self) -> str:
        return ",".join(p.name for p in self.providers)

    def extract(self, text: str) -> dict:
        usable = [p for p in self.providers if p.available]
        if not usable:
            raise RuntimeError(
                "no LLM provider has an API key (set AGNES_API_KEY or DEEPSEEK_API_KEY)"
            )
        errors: list[str] = []
        empty: dict | None = None
        empty_by = ""
        for provider in usable:
            try:
                out = provider.extract(text)
            except Exception as e:  # noqa: BLE001 - fall through to the next provider
                errors.append(f"{provider.name}: {e}")
                continue
            if out.get("packages"):
                out["provider"] = provider.name
                return out
            # A keyless/degenerate reply is not a success: try the next provider.
            empty, empty_by = out, provider.name
            errors.append(f"{provider.name}: returned 0 packages")
        if empty is not None:
            # Every provider ran but found nothing -> let run.py keep prior data
            # (extract_empty) rather than reporting a hard failure.
            empty["provider"] = empty_by
            return empty
        raise RuntimeError("all LLM providers failed -> " + " | ".join(errors))
