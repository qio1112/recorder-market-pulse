import os

import httpx
import pytest

from main.llm.client import LlmClientError, chat_completion, load_llm_config, load_llm_fallback_config


def test_load_llm_config_defaults_for_missing_file(tmp_path):
    config = load_llm_config(str(tmp_path / "missing.yaml"))

    assert config.provider == "lmstudio"
    assert config.base_url == "http://localhost:1234/v1"
    assert config.chat_completion_path == "/chat/completions"
    assert config.api_key_env is None


def test_chat_completion_without_auth(monkeypatch, tmp_path):
    config_path = tmp_path / "llm.yaml"
    config_path.write_text("model: test-model\napi_key_env:\n", encoding="utf-8")
    config = load_llm_config(str(config_path))
    captured = {}

    def fake_post(url, headers, json, timeout):
        captured.update({"url": url, "headers": headers, "json": json, "timeout": timeout})
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": " summary "}}]},
            request=httpx.Request("POST", url),
        )

    monkeypatch.setattr("main.llm.client.httpx.post", fake_post)

    result = chat_completion([{"role": "user", "content": "hello"}], config=config)

    assert result == "summary"
    assert "Authorization" not in captured["headers"]
    assert captured["json"]["model"] == "test-model"


def test_chat_completion_with_auth(monkeypatch, tmp_path):
    config_path = tmp_path / "llm.yaml"
    config_path.write_text("api_key_env: TEST_LLM_API_KEY\n", encoding="utf-8")
    config = load_llm_config(str(config_path))
    monkeypatch.setenv("TEST_LLM_API_KEY", "secret")
    captured = {}

    def fake_post(url, headers, json, timeout):
        captured["headers"] = headers
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": "ok"}}]},
            request=httpx.Request("POST", url),
        )

    monkeypatch.setattr("main.llm.client.httpx.post", fake_post)

    assert chat_completion([{"role": "user", "content": "hello"}], config=config) == "ok"
    assert captured["headers"]["Authorization"] == "Bearer secret"


def test_load_llm_config_uses_direct_llm_api_key_env(monkeypatch, tmp_path):
    monkeypatch.setenv("LLM_API_KEY", "secret")

    config = load_llm_config(str(tmp_path / "missing.yaml"))

    assert config.api_key_env == "LLM_API_KEY"


def test_load_llm_fallback_config_inherits_primary_and_overrides_model(monkeypatch, tmp_path):
    monkeypatch.setenv("LLM_PROVIDER", "lmstudio")
    monkeypatch.setenv("LLM_BASE_URL", "http://primary:1234/v1")
    monkeypatch.setenv("LLM_MODEL", "primary-model")
    monkeypatch.setenv("LLM_API_KEY", "secret")
    monkeypatch.setenv("LLM_FALLBACK_MODEL", "fallback-model")
    monkeypatch.setenv("LLM_FALLBACK_API_KEY_ENV", "LLM_API_KEY")

    config = load_llm_fallback_config(str(tmp_path / "missing.yaml"))

    assert config is not None
    assert config.provider == "lmstudio"
    assert config.base_url == "http://primary:1234/v1"
    assert config.model == "fallback-model"
    assert config.api_key_env == "LLM_API_KEY"


def test_chat_completion_resolves_lm_studio_placeholder_model(monkeypatch, tmp_path):
    config = load_llm_config(str(tmp_path / "missing.yaml"))
    captured = {}

    def fake_get(url, headers, timeout):
        captured["models_url"] = url
        return httpx.Response(
            200,
            json={"data": [{"id": "loaded-lm-studio-model"}]},
            request=httpx.Request("GET", url),
        )

    def fake_post(url, headers, json, timeout):
        captured["chat_payload"] = json
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": "ok"}}]},
            request=httpx.Request("POST", url),
        )

    monkeypatch.setattr("main.llm.client.httpx.get", fake_get)
    monkeypatch.setattr("main.llm.client.httpx.post", fake_post)

    assert chat_completion([{"role": "user", "content": "hello"}], config=config) == "ok"
    assert captured["models_url"] == "http://localhost:1234/v1/models"
    assert captured["chat_payload"]["model"] == "loaded-lm-studio-model"


def test_chat_completion_raises_on_http_error(monkeypatch, tmp_path):
    config = load_llm_config(str(tmp_path / "missing.yaml"))

    def fake_post(url, headers, json, timeout):
        return httpx.Response(500, request=httpx.Request("POST", url))

    monkeypatch.setattr("main.llm.client.httpx.post", fake_post)

    with pytest.raises(LlmClientError):
        chat_completion([{"role": "user", "content": "hello"}], config=config)


def test_chat_completion_uses_fallback_after_primary_http_error(monkeypatch, tmp_path):
    monkeypatch.setenv("LLM_BASE_URL", "http://primary:1234/v1")
    monkeypatch.setenv("LLM_MODEL", "primary-model")
    monkeypatch.setenv("LLM_FALLBACK_BASE_URL", "http://fallback:1234/v1")
    monkeypatch.setenv("LLM_FALLBACK_MODEL", "fallback-model")
    calls = []

    def fake_post(url, headers, json, timeout):
        calls.append({"url": url, "json": json})
        if "primary" in url:
            return httpx.Response(503, request=httpx.Request("POST", url))
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": "fallback ok"}}]},
            request=httpx.Request("POST", url),
        )

    monkeypatch.setattr("main.llm.client.httpx.post", fake_post)

    result = chat_completion([{"role": "user", "content": "hello"}])

    assert result == "fallback ok"
    assert calls[0]["url"] == "http://primary:1234/v1/chat/completions"
    assert calls[0]["json"]["model"] == "primary-model"
    assert calls[1]["url"] == "http://fallback:1234/v1/chat/completions"
    assert calls[1]["json"]["model"] == "fallback-model"


def test_chat_completion_raises_on_invalid_payload(monkeypatch, tmp_path):
    config = load_llm_config(str(tmp_path / "missing.yaml"))

    def fake_post(url, headers, json, timeout):
        return httpx.Response(200, json={"choices": []}, request=httpx.Request("POST", url))

    monkeypatch.setattr("main.llm.client.httpx.post", fake_post)

    with pytest.raises(LlmClientError):
        chat_completion([{"role": "user", "content": "hello"}], config=config)


def test_chat_completion_raises_on_endpoint_error_content(monkeypatch, tmp_path):
    config = load_llm_config(str(tmp_path / "missing.yaml"))

    def fake_post(url, headers, json, timeout):
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": "Unexpected endpoint or method. (POST /v1/chat/completions). Returning 200 anyway"}}]},
            request=httpx.Request("POST", url),
        )

    monkeypatch.setattr("main.llm.client.httpx.post", fake_post)

    with pytest.raises(LlmClientError):
        chat_completion([{"role": "user", "content": "hello"}], config=config)
