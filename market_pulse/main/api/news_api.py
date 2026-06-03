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
        return get_stock_news_summaries(
            symbols=payload.symbols,
            max_news_per_symbol=payload.max_news_per_symbol,
            max_workers=payload.max_workers,
            summary_prompt=payload.summary_prompt,
            max_tokens=payload.max_tokens,
        )
    except Exception as exc:  # pragma: no cover - surface runtime errors to clients
        logger.exception("stock news summary failed")
        raise HTTPException(status_code=500, detail=f"stock news summary failed: {exc}") from exc
