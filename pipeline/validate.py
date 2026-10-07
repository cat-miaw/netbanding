import json, sys
sys.path.insert(0, "pipeline/scraper")
from schema import Catalog, Manifest  # noqa: E402

catalog = Catalog(**json.load(open("data/catalog.json")))
manifest = Manifest(**json.load(open("data/manifest.json")))
assert {p.isp_id for p in catalog.packages} <= {i.id for i in catalog.isps}, "orphan package"
print(f"OK: {len(catalog.isps)} ISPs, {len(catalog.packages)} packages, data_version={manifest.data_version}")
