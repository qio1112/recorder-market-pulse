"""Configurable OpenAI-compatible LLM client helpers."""

from __future__ import annotations

import os
from dataclasses import dataclass
from typing import Any
from urllib.parse import urlparse, urlunparse

import httpx
import yaml

from main.utils.path_utils import get_resources_path


DEFAULT_CONFIG = {
    "provider": "lmstudio",
    "model": "local-model",
    "base_url": "http://localhost:1234/v1",
    "chat_completion_path": "/chat/completions",
    "api_key_env": None,
    "timeout_seconds": 60,
    "temperature": 0.2,
    "max_tokens": 800,
}


class LlmClientError(RuntimeError):
    """Raised when an LLM request fails or returns an invalid payload."""


LLM_ENDPOINT_ERROR_MARKERS = (
    "Unexpected endpoint or method",
    "Returning 200 anyway",
)


@dataclass(frozen=True)
class LlmConfig:
    provider: str
    model: str
    base_url: str
    chat_completion_path: str
    api_key_env: str | None
    timeout_seconds: float
    temperature: float
    max_tokens: int

    @property
    def chat_completion_url(self) -> str:
        return self.base_url.rstrip("/") + "/" + self.chat_completion_path.lstrip("/")

    @property
    def models_url(self) -> str:
        return self.base_url.rstrip("/") + "/models"


def load_llm_config(config_path: str | None = None) -> LlmConfig:
    return _load_llm_config_from_env(config_path=config_path)


def load_llm_fallback_config(config_path: str | None = None) -> LlmConfig | None:
    fallback_env_keys = [
        "LLM_FALLBACK_PROVIDER",
        "LLM_FALLBACK_MODEL",
        "LLM_FALLBACK_BASE_URL",
        "LLM_FALLBACK_CHAT_COMPLETION_PATH",
        "LLM_FALLBACK_API_KEY",
        "LLM_FALLBACK_API_KEY_ENV",
        "LLM_FALLBACK_TIMEOUT_SECONDS",
        "LLM_FALLBACK_TEMPERATURE",
        "LLM_FALLBACK_MAX_TOKENS",
    ]
    if not any(os.getenv(key) for key in fallback_env_keys):
        return None
    return _load_llm_config_from_env(config_path=config_path, fallback=True)


def _load_llm_config_from_env(config_path: str | None = None, *, fallback: bool = False) -> LlmConfig:
    path = config_path or get_resources_path("config", "llm.yaml")
    raw: dict[str, Any] = {}
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as input_file:
            loaded = yaml.safe_load(input_file) or {}
            if not isinstance(loaded, dict):
                raise ValueError(f"LLM config must be a mapping: {path}")
            raw = loaded

    merged = {**DEFAULT_CONFIG, **raw}
    primary_env_overrides = {
        "provider": os.getenv("LLM_PROVIDER"),
        "model": os.getenv("LLM_MODEL"),
        "base_url": os.getenv("LLM_BASE_URL"),
        "chat_completion_path": os.getenv("LLM_CHAT_COMPLETION_PATH"),
        "api_key_env": os.getenv("LLM_API_KEY_ENV") or ("LLM_API_KEY" if os.getenv("LLM_API_KEY") else None),
        "timeout_seconds": os.getenv("LLM_TIMEOUT_SECONDS"),
        "temperature": os.getenv("LLM_TEMPERATURE"),
        "max_tokens": os.getenv("LLM_MAX_TOKENS"),
    }
    merged.update({key: value for key, value in primary_env_overrides.items() if value})

    if fallback:
        fallback_env_overrides = {
            "provider": os.getenv("LLM_FALLBACK_PROVIDER"),
            "model": os.getenv("LLM_FALLBACK_MODEL"),
            "base_url": os.getenv("LLM_FALLBACK_BASE_URL"),
            "chat_completion_path": os.getenv("LLM_FALLBACK_CHAT_COMPLETION_PATH"),
            "api_key_env": os.getenv("LLM_FALLBACK_API_KEY_ENV")
            or ("LLM_FALLBACK_API_KEY" if os.getenv("LLM_FALLBACK_API_KEY") else None),
            "timeout_seconds": os.getenv("LLM_FALLBACK_TIMEOUT_SECONDS"),
            "temperature": os.getenv("LLM_FALLBACK_TEMPERATURE"),
            "max_tokens": os.getenv("LLM_FALLBACK_MAX_TOKENS"),
        }
        merged.update({key: value for key, value in fallback_env_overrides.items() if value})

    api_key_env = merged.get("api_key_env") or None
    base_url = _docker_host_safe_base_url(str(merged["base_url"]))
    return LlmConfig(
        provider=str(merged["provider"]),
        model=str(merged["model"]),
        base_url=base_url,
        chat_completion_path=str(merged["chat_completion_path"]),
        api_key_env=str(api_key_env) if api_key_env else None,
        timeout_seconds=float(merged["timeout_seconds"]),
        temperature=float(merged["temperature"]),
        max_tokens=int(merged["max_tokens"]),
    )


def _docker_host_safe_base_url(base_url: str) -> str:
    """Make host LM Studio reachable from the Market Pulse Docker container."""
    if not os.path.exists("/.dockerenv"):
        return base_url
    if os.getenv("LLM_ALLOW_DOCKER_LOCALHOST", "").lower() in {"1", "true", "yes"}:
        return base_url

    parsed = urlparse(base_url)
    if parsed.hostname not in {"localhost", "127.0.0.1"}:
        return base_url

    netloc = "host.docker.internal"
    if parsed.port:
        netloc = f"{netloc}:{parsed.port}"
    return urlunparse(parsed._replace(netloc=netloc))


def _auth_headers(config: LlmConfig) -> dict[str, str]:
    headers = {"Content-Type": "application/json"}
    if config.api_key_env:
        api_key = os.getenv(config.api_key_env)
        if api_key:
            headers["Authorization"] = f"Bearer {api_key}"
    return headers


def _resolve_model(config: LlmConfig, headers: dict[str, str]) -> str:
    if config.model and config.model != "local-model":
        return config.model
    try:
        response = httpx.get(config.models_url, headers=headers, timeout=config.timeout_seconds)
        response.raise_for_status()
        models = response.json().get("data", [])
        first_model = models[0].get("id") if models and isinstance(models[0], dict) else None
        if first_model:
            return first_model
    except Exception:
        pass
    return config.model


def chat_completion(
    messages: list[dict[str, str]],
    *,
    config: LlmConfig | None = None,
    fallback_config: LlmConfig | None = None,
    temperature: float | None = None,
    max_tokens: int | None = None,
) -> str:
    cfg = config or load_llm_config()
    fallback_cfg = fallback_config if config else load_llm_fallback_config()
    try:
        return _chat_completion_single(
            messages,
            config=cfg,
            temperature=temperature,
            max_tokens=max_tokens,
        )
    except LlmClientError as exc:
        if not fallback_cfg:
            raise
        try:
            return _chat_completion_single(
                messages,
                config=fallback_cfg,
                temperature=temperature,
                max_tokens=max_tokens,
            )
        except LlmClientError as fallback_exc:
            raise LlmClientError(
                f"Primary LLM failed: {exc}; fallback LLM failed: {fallback_exc}"
            ) from fallback_exc


def _chat_completion_single(
    messages: list[dict[str, str]],
    *,
    config: LlmConfig,
    temperature: float | None = None,
    max_tokens: int | None = None,
) -> str:
    cfg = config
    headers = _auth_headers(cfg)

    payload = {
        "model": _resolve_model(cfg, headers),
        "messages": messages,
        "temperature": cfg.temperature if temperature is None else temperature,
        "max_tokens": cfg.max_tokens if max_tokens is None else max_tokens,
    }
    try:
        response = httpx.post(
            cfg.chat_completion_url,
            headers=headers,
            json=payload,
            timeout=cfg.timeout_seconds,
        )
        response.raise_for_status()
    except httpx.HTTPError as exc:
        response_body = getattr(getattr(exc, "response", None), "text", "")
        detail = f" Response body: {response_body[:500]}" if response_body else ""
        raise LlmClientError(f"LLM request failed: {exc}{detail}") from exc

    data = response.json()
    try:
        content = data["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError) as exc:
        raise LlmClientError("LLM response did not include choices[0].message.content") from exc
    if not isinstance(content, str):
        raise LlmClientError("LLM response content must be a string")
    content = content.strip()
    if any(marker in content for marker in LLM_ENDPOINT_ERROR_MARKERS):
        raise LlmClientError(f"LLM returned endpoint error content: {content[:500]}")
    return content


def summarize_text(
    text: str,
    *,
    prompt: str = "Summarize the following text clearly and concisely.",
    config: LlmConfig | None = None,
    temperature: float | None = None,
    max_tokens: int | None = None,
) -> str:
    messages = [
        {"role": "system", "content": prompt},
        {"role": "user", "content": text},
    ]
    return chat_completion(
        messages,
        config=config,
        temperature=temperature,
        max_tokens=max_tokens,
    )
