"""Market news API routes."""

from typing import List, Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from main.news.stock_news import get_stock_news_summaries
from main.utils.logger_utils import setup_logging

router = APIRouter(prefix="/news", tags=["news"])
logger = setup_logging("news_api")


class StockNewsSummaryRequest(BaseModel):
    symbols: Optional[List[str]] = Field(default=None)
    max_news_per_symbol: int = Field(default=5, gt=0, le=20)
    max_workers: int = Field(default=4, gt=0, le=16)
    summary_prompt: Optional[str] = Field(default=None)
    max_tokens: Optional[int] = Field(default=None, gt=0)


@router.post("/stock-summary")
def stock_news_summary(payload: StockNewsSummaryRequest):
    try:
        symbol_count = len(payload.symbols) if payload.symbols else 0
        logger.info(
            "Received stock news summary API request: symbols=%s default_symbols=%s max_news_per_symbol=%s max_workers=%s prompt_chars=%s max_tokens=%s",
            symbol_count,
            payload.symbols is None,
            payload.max_news_per_symbol,
            payload.max_workers,
            len(payload.summary_prompt or ""),
            payload.max_tokens,
        )
        result = get_stock_news_summaries(
            symbols=payload.symbols,
            max_news_per_symbol=payload.max_news_per_symbol,
            max_workers=payload.max_workers,
            summary_prompt=payload.summary_prompt,
            max_tokens=payload.max_tokens,
        )
        logger.info(
            "Finished stock news summary API request: summary_count=%s",
            len(result.get("summaries", [])),
        )
        return result
    except Exception as exc:  # pragma: no cover - surface runtime errors to clients
        logger.exception("stock news summary failed")
        raise HTTPException(status_code=500, detail=f"stock news summary failed: {exc}") from exc
