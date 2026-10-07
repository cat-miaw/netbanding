"""Polite fetcher. Static pages via httpx; Playwright only if a source needs JS."""
import time

import httpx

USER_AGENT = "NetBandingBot/1.0 (+price comparison, low volume; contact via repo issues)"


def fetch(url: str, mode: str = "static", timeout: int = 30, tries: int = 3) -> str:
    if mode == "playwright":
        raise NotImplementedError(
            "playwright mode not wired yet; add it only for a JS-rendered page"
        )
    if mode != "static":
        raise ValueError(f"unknown fetch mode: {mode}")
    last: Exception | None = None
    for attempt in range(tries):
        try:
            resp = httpx.get(
                url,
                headers={"User-Agent": USER_AGENT},
                timeout=timeout,
                follow_redirects=True,
            )
            resp.raise_for_status()
            time.sleep(2)  # polite delay between requests
            return resp.text
        except Exception as e:  # noqa: BLE001 - isolate per ISP in run.py
            last = e
            time.sleep(2 * (attempt + 1))
    raise last  # type: ignore[misc]
