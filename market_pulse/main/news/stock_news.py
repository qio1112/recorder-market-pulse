"""Stock news collection and LLM summaries."""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
import os
import re
from typing import Any

import yfinance as yf

from main.llm.client import summarize_text
from main.data_source.data_source import get_symbols_from_file
from main.utils.logger_utils import setup_logging
from main.utils.path_utils import get_resources_path

logger = setup_logging("stock_news")

THINK_BLOCK_PATTERN = re.compile(r"(?is)<think>.*?</think>")
ELLIPSIS_PATTERN = re.compile(r"\s*(?:\.{3,}|…+)\s*")
COMPLETE_SENTENCE_END_PATTERN = re.compile(r"[.!?][)\"']?$")
DEFAULT_MAX_LLM_NEWS_ARTICLES = 5
DEFAULT_MAX_NEWS_TITLE_CHARS = 240
DEFAULT_MAX_NEWS_SUMMARY_CHARS = 1800
DEFAULT_MAX_NEWS_PROMPT_CHARS = 12000


def _first_present(source: dict[str, Any], keys: list[str]) -> Any:
    for key in keys:
        value = source.get(key)
        if value:
            return value
    return None


def normalize_news_item(item: dict[str, Any]) -> dict[str, Any]:
    content = item.get("content") if isinstance(item.get("content"), dict) else {}
    title = _first_present(item, ["title"]) or _first_present(content, ["title"]) or ""
    publisher = (
        _first_present(item, ["publisher", "provider", "source"])
        or _first_present(content, ["provider", "publisher", "source"])
        or ""
    )
    link = (
        _first_present(item, ["link", "url"])
        or _first_present(content, ["canonicalUrl", "clickThroughUrl", "link", "url"])
        or ""
    )
    if isinstance(link, dict):
        link = link.get("url") or ""
    summary = (
        _first_present(item, ["summary", "description"])
        or _first_present(content, ["summary", "description", "previewText"])
        or ""
    )
    publish_time = (
        _first_present(item, ["providerPublishTime", "publishTime", "pubDate"])
        or _first_present(content, ["pubDate", "providerPublishTime", "publishTime"])
    )

    return {
        "title": str(title),
        "publisher": str(publisher),
        "link": str(link),
        "publish_time": publish_time,
        "summary": str(summary),
    }


def get_symbol_news(symbol: str, max_news: int = 5) -> list[dict[str, Any]]:
    raw_news = yf.Ticker(symbol).news or []
    normalized = [
        normalize_news_item(item)
        for item in raw_news[:max_news]
        if isinstance(item, dict)
    ]
    return [item for item in normalized if item["title"] or item["summary"]]


def summarize_symbol_news(
    symbol: str,
    articles: list[dict[str, Any]],
    *,
    summary_prompt: str | None = None,
    max_tokens: int | None = None,
) -> str:
    if not articles:
        return "No recent news found."
    article_text = build_llm_article_text(articles)
    try:
        summary = summarize_text(
            f"Symbol: {symbol}\n\n{article_text}",
            prompt=summary_prompt,
            max_tokens=max_tokens,
            temperature=0.1,
        )
    except Exception as exc:
        logger.warning(
            "LLM news summary failed for %s with %s prompt chars, using article fallback: %s",
            symbol,
            len(article_text),
            exc,
        )
        return fallback_article_summary(symbol, articles)
    cleaned_summary = clean_llm_summary(summary)
    return cleaned_summary or fallback_article_summary(symbol, articles)


def build_llm_article_text(articles: list[dict[str, Any]]) -> str:
    max_articles = get_int_env("NEWS_LLM_MAX_ARTICLES", DEFAULT_MAX_LLM_NEWS_ARTICLES)
    max_title_chars = get_int_env("NEWS_LLM_MAX_TITLE_CHARS", DEFAULT_MAX_NEWS_TITLE_CHARS)
    max_summary_chars = get_int_env("NEWS_LLM_MAX_SUMMARY_CHARS", DEFAULT_MAX_NEWS_SUMMARY_CHARS)
    max_prompt_chars = get_int_env("NEWS_LLM_MAX_PROMPT_CHARS", DEFAULT_MAX_NEWS_PROMPT_CHARS)
    chunks = []
    for article in articles[:max_articles]:
        title = truncate_text(sanitize_summary_text(str(article.get("title") or "")), max_title_chars)
        publisher = truncate_text(sanitize_summary_text(str(article.get("publisher") or "")), 80)
        summary = truncate_text(sanitize_summary_text(str(article.get("summary") or "")), max_summary_chars)
        chunk = f"Title: {title}\nPublisher: {publisher}\nSummary: {summary}".strip()
        if chunk:
            chunks.append(chunk)
    return truncate_text("\n\n".join(chunks), max_prompt_chars)


def get_int_env(name: str, default: int) -> int:
    try:
        return max(1, int(os.getenv(name, default)))
    except (TypeError, ValueError):
        return default


def truncate_text(value: str, max_chars: int) -> str:
    cleaned = re.sub(r"\s+", " ", str(value or "")).strip()
    if len(cleaned) <= max_chars:
        return cleaned
    truncated = cleaned[:max_chars].rsplit(" ", 1)[0].rstrip()
    sentence_end = max(truncated.rfind("."), truncated.rfind("!"), truncated.rfind("?"))
    if sentence_end >= max_chars // 2:
        return truncated[:sentence_end + 1].strip()
    return truncated


def sanitize_summary_text(value: str) -> str:
    cleaned = ELLIPSIS_PATTERN.sub(" ", str(value or ""))
    return re.sub(r"\s+", " ", cleaned).strip()


def clean_llm_summary(summary: str) -> str:
    cleaned = THINK_BLOCK_PATTERN.sub(" ", str(summary or ""))
    cleaned = (
        cleaned.replace("```markdown", "")
        .replace("```text", "")
        .replace("```", "")
        .strip()
    )
    lines = []
    for line in cleaned.splitlines():
        normalized = line.strip()
        if not normalized:
            continue
        if re.match(r"(?i)^(summary|final answer|answer|analysis|reasoning)\s*[:\-]\s*", normalized):
            normalized = re.sub(r"(?i)^(summary|final answer|answer|analysis|reasoning)\s*[:\-]\s*", "", normalized).strip()
        if normalized:
            lines.append(normalized.lstrip("-* ").strip())
    return complete_summary_or_blank(sanitize_summary_text(" ".join(lines)))


def complete_summary_or_blank(summary: str) -> str:
    cleaned = sanitize_summary_text(summary)
    if not cleaned:
        return ""
    if COMPLETE_SENTENCE_END_PATTERN.search(cleaned):
        return cleaned
    sentence_end = max(cleaned.rfind("."), cleaned.rfind("!"), cleaned.rfind("?"))
    if sentence_end >= max(40, len(cleaned) // 3):
        return cleaned[:sentence_end + 1].strip()
    if len(cleaned) <= 180:
        return cleaned + "."
    return ""


def fallback_article_summary(symbol: str, articles: list[dict[str, Any]]) -> str:
    snippets = []
    for article in articles[:3]:
        title = str(article.get("title") or "").strip()
        summary = str(article.get("summary") or "").strip()
        text = summary or title
        if title and summary and title not in summary:
            text = f"{title}: {summary}"
        if text:
            snippets.append(sanitize_summary_text(text))
    if not snippets:
        return f"No usable news text found for {symbol}."
    return complete_summary_or_blank(f"{symbol}: " + " ".join(snippets)) or f"No complete news summary generated for {symbol}."


def summarize_symbol_from_yfinance(
    symbol: str,
    max_news_per_symbol: int,
    summary_prompt: str | None = None,
    max_tokens: int | None = None,
) -> dict[str, Any]:
    articles = get_symbol_news(symbol, max_news=max_news_per_symbol)
    summary = summarize_symbol_news(
        symbol,
        articles,
        summary_prompt=summary_prompt,
        max_tokens=max_tokens,
    )
    return {
        "symbol": symbol,
        "summary": summary,
        "article_count": len(articles),
        "articles": articles,
    }


def get_default_news_symbols_from_file() -> list[str]:
    return get_symbols_from_file(get_resources_path("symbols", "news_symbols.txt"))


def get_stock_news_summaries(
    symbols: list[str] | None = None,
    *,
    max_news_per_symbol: int = 5,
    max_workers: int = 4,
    summary_prompt: str | None = None,
    max_tokens: int | None = None,
) -> dict[str, Any]:
    selected_symbols = symbols or get_default_news_symbols_from_file()
    cleaned_symbols = []
    for symbol in selected_symbols:
        normalized = symbol.strip().upper()
        if normalized and normalized not in cleaned_symbols:
            cleaned_symbols.append(normalized)

    if not cleaned_symbols:
        return {
            "generated_at": datetime.now(timezone.utc).isoformat(),
            "summaries": [],
        }

    summaries_by_index: dict[int, dict[str, Any]] = {}
    worker_count = max(1, min(max_workers, len(cleaned_symbols)))
    with ThreadPoolExecutor(max_workers=worker_count) as executor:
        futures = {
            executor.submit(
                summarize_symbol_from_yfinance,
                symbol,
                max_news_per_symbol,
                summary_prompt,
                max_tokens,
            ): (index, symbol)
            for index, symbol in enumerate(cleaned_symbols)
        }
        for future in as_completed(futures):
            index, symbol = futures[future]
            try:
                summaries_by_index[index] = future.result()
            except Exception as exc:  # pragma: no cover - runtime resilience
                logger.exception("Failed to summarize news for %s", symbol)
                summaries_by_index[index] = {
                    "symbol": symbol,
                    "summary": f"Failed to summarize news for {symbol}: {exc}",
                    "article_count": 0,
                    "articles": [],
                }

    summaries = [
        summaries_by_index[index]
        for index in range(len(cleaned_symbols))
    ]

    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "summaries": summaries,
    }
