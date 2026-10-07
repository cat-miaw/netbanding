"""Strip pages to compact text + content hash. Unchanged hash = skip LLM."""
import hashlib
import re

from bs4 import BeautifulSoup

_WS = re.compile(r"\s+")


def clean(html: str) -> str:
    soup = BeautifulSoup(html, "html.parser")
    for tag in soup(["script", "style", "nav", "footer", "header", "aside", "form"]):
        tag.decompose()
    text = soup.get_text(separator="\n")
    lines = [_WS.sub(" ", ln).strip() for ln in text.splitlines()]
    lines = [ln for ln in lines if ln]
    return "\n".join(lines)


def content_hash(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()
