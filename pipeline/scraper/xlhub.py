"""Hub discovery for XL product families.

The hub page (produk/paket-dan-addon) lists every family behind
"Lihat Detail" links. Instead of hardcoding family URLs, each run
re-discovers them: new families become review items (never silently
skipped, never blindly ingested), known families get extracted.
"""
import re
from urllib.parse import urljoin

BASE = "https://www.xl.co.id"
LINK = re.compile(r'href="((?:/id)?/produk/paket-dan-addon/([a-z0-9-]+)/?)"')


def discover(hub_html: str) -> dict:
    """Return {slug: absolute url} for every family linked from the hub."""
    found: dict = {}
    for href, slug in LINK.findall(hub_html):
        if slug == "paket-dan-addon":
            continue
        found.setdefault(slug, urljoin(BASE, href))
    return found
