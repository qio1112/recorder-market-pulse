from main.news.stock_news import build_llm_article_text, clean_llm_summary, get_default_news_symbols_from_file, get_stock_news_summaries, normalize_news_item, summarize_symbol_news


def test_normalize_news_item_legacy_shape():
    item = {
        "title": "AAPL rises",
        "publisher": "Yahoo Finance",
        "link": "https://example.com",
        "providerPublishTime": 123,
        "summary": "Apple news",
    }

    result = normalize_news_item(item)

    assert result["title"] == "AAPL rises"
    assert result["publisher"] == "Yahoo Finance"
    assert result["link"] == "https://example.com"
    assert result["publish_time"] == 123
    assert result["summary"] == "Apple news"


def test_get_stock_news_summaries_uses_yfinance_and_llm(monkeypatch):
    class FakeTicker:
        def __init__(self, symbol):
            self.news = [
                {
                    "title": f"{symbol} headline",
                    "publisher": "Publisher",
                    "summary": "Story summary",
                    "link": "https://example.com/story",
                }
            ]

    monkeypatch.setattr("main.news.stock_news.yf.Ticker", FakeTicker)
    monkeypatch.setattr("main.news.stock_news.summarize_text", lambda text, **kwargs: "One paragraph.")

    result = get_stock_news_summaries(["aapl"], max_news_per_symbol=3)

    assert result["summaries"][0]["symbol"] == "AAPL"
    assert result["summaries"][0]["summary"] == "One paragraph."
    assert result["summaries"][0]["article_count"] == 1


def test_clean_llm_summary_removes_thinking_and_prefixes():
    result = clean_llm_summary("<think>reasoning</think>\nSummary: Apple shares rose after earnings.")

    assert result == "Apple shares rose after earnings."


def test_summarize_symbol_news_falls_back_when_llm_is_empty(monkeypatch):
    monkeypatch.setattr("main.news.stock_news.summarize_text", lambda text, **kwargs: "<think>reasoning only</think>")

    result = summarize_symbol_news("AAPL", [
        {
            "title": "Apple earnings beat",
            "publisher": "Publisher",
            "summary": "Revenue improved on iPhone demand.",
            "link": "https://example.com/story",
        }
    ])

    assert result == "AAPL: Apple earnings beat: Revenue improved on iPhone demand."


def test_summarize_symbol_news_falls_back_when_llm_raises(monkeypatch):
    monkeypatch.setattr("main.news.stock_news.summarize_text", lambda text, **kwargs: (_ for _ in ()).throw(RuntimeError("endpoint failed")))

    result = summarize_symbol_news("MSFT", [
        {
            "title": "Microsoft cloud grows",
            "publisher": "Publisher",
            "summary": "Azure revenue increased.",
            "link": "https://example.com/story",
        }
    ])

    assert result == "MSFT: Microsoft cloud grows: Azure revenue increased."


def test_build_llm_article_text_is_bounded_by_env_and_omits_links(monkeypatch):
    monkeypatch.setenv("NEWS_LLM_MAX_ARTICLES", "3")
    monkeypatch.setenv("NEWS_LLM_MAX_SUMMARY_CHARS", "700")
    monkeypatch.setenv("NEWS_LLM_MAX_PROMPT_CHARS", "3500")
    articles = [
        {
            "title": "Very long title " * 40,
            "publisher": "Publisher",
            "summary": "Long summary " * 200,
            "link": "https://example.com/large-link",
        }
        for _ in range(10)
    ]

    result = build_llm_article_text(articles)

    assert len(result) <= 3503
    assert result.count("Title:") == 3
    assert "https://example.com" not in result
    assert "..." not in result


def test_get_default_news_symbols_from_news_symbols_file(monkeypatch):
    captured = {}

    def fake_get_resources_path(*subpaths):
        captured["subpaths"] = subpaths
        return "/tmp/news_symbols.txt"

    monkeypatch.setattr("main.news.stock_news.get_resources_path", fake_get_resources_path)
    monkeypatch.setattr("main.news.stock_news.get_symbols_from_file", lambda path: ["SPY"])

    assert get_default_news_symbols_from_file() == ["SPY"]
    assert captured["subpaths"] == ("symbols", "news_symbols.txt")
