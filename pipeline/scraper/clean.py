"""Strip pages to compact text + content hash. Unchanged hash = skip LLM."""
import hashlib
import re

from bs4 import BeautifulSoup, CData, NavigableString, TemplateString

_WS = re.compile(r"\s+")

# BeautifulSoup's html.parser marks text inside <template> as TemplateString,
# which get_text() skips by default. Sites (e.g. indihome.co.id) moved their
# package cards into templates, so without this the page cleans down to the
# two hardcoded cards and every scrape looks like a 100% count drop.
_TEXT_TYPES = (NavigableString, CData, TemplateString)


def clean(html: str) -> str:
    soup = BeautifulSoup(html, "html.parser")
    # JS-rendered frameworks (Next.js etc.) embed page data in JSON blobs.
    # Harvest their text BEFORE stripping scripts, else data-rich pages
    # clean down to a few hundred chars of shell.
    embedded = "\n".join(
        script.get_text(separator=" ")
        for script in soup.find_all("script", attrs={"type": "application/json"})
    )
    next_data = soup.find("script", attrs={"id": "__NEXT_DATA__"})
    if next_data is not None:
        embedded += "\n" + next_data.get_text(separator=" ")
    for tag in soup(["script", "style", "nav", "footer", "header", "aside", "form"]):
        tag.decompose()
    text = soup.get_text(separator="\n", types=_TEXT_TYPES)
    lines = [_WS.sub(" ", ln).strip() for ln in text.splitlines()]
    lines = [ln for ln in lines if ln]
    out = "\n".join(lines)
    if embedded.strip():
        out += "\n" + _WS.sub(" ", embedded).strip()
    return out


def content_hash(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()
