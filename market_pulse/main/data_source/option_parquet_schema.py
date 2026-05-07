from typing import Any, TypedDict


class TrackedSymbol(TypedDict):
    symbol: str


class OptionExpiry(TypedDict, total=False):
    expiry: str
    expired: bool


class StrikeHistory(TypedDict, total=False):
    strike: float
    contractSymbol: str | list[str]
    contractSize: str | list[str]
    history: dict[str, list[Any]]


class OptionHistory(TypedDict):
    symbol: str
    expiry: str
    option_type: str
    strikes: list[StrikeHistory]
