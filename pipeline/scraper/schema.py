"""Pydantic schema mirroring NETBANDING_BLUEPRINT Section 4. Contract is law."""
from typing import Literal
from pydantic import BaseModel, Field

Region = str

class Isp(BaseModel):
    id: str = Field(pattern=r"^[a-z0-9-]+$")
    name: str
    category: Literal["broadband", "cellular"] = "broadband"
    logo_asset: str
    website_url: str

class Package(BaseModel):
    id: str = Field(pattern=r"^[a-z0-9-]+$")
    isp_id: str
    name: str
    type: Literal["broadband", "cellular"] = "broadband"
    speed_mbps: int | None = Field(default=None, ge=5, le=2000)
    quota_mb: int | None = None
    validity_days: int | None = None
    contract_months: int | None = None
    base_price: int = Field(ge=5_000, le=2_000_000)
    tax_inclusive: bool  # required, never guessed
    device_rental_fee: int = Field(ge=0, default=0)
    install_fee: int | None = Field(default=None, ge=0, le=2_000_000)
    fup_note: str | None = None
    promo_note: str | None = None
    regions: list[str] = ["JAVA_ALL"]
    source_url: str
    is_active: bool = True
    last_verified_at: str
    updated_at: str

class Catalog(BaseModel):
    isps: list[Isp]
    packages: list[Package]

class ManifestFile(BaseModel):
    path: str
    sha256: str
    bytes: int

class Manifest(BaseModel):
    schema_version: int = 1
    data_version: int = Field(ge=1)
    generated_at: str
    ppn_rate: float = 0.11
    files: dict[str, ManifestFile]
