"""LLM API routes."""

from typing import List

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from main.llm.client import LlmClientError, chat_completion
from main.utils.logger_utils import setup_logging

router = APIRouter(prefix="/llm", tags=["llm"])
logger = setup_logging("llm_api")


class ChatMessage(BaseModel):
    role: str = Field(..., pattern="^(system|user|assistant)$")
    content: str = Field(..., min_length=1)


class ChatRequest(BaseModel):
    messages: List[ChatMessage] = Field(..., min_length=1)
    temperature: float | None = Field(default=None, ge=0, le=2)
    max_tokens: int | None = Field(default=None, gt=0)


@router.post("/chat")
def chat(payload: ChatRequest):
    try:
        reply = chat_completion(
            [message.model_dump() for message in payload.messages],
            temperature=payload.temperature,
            max_tokens=payload.max_tokens,
        )
        return {"reply": reply}
    except LlmClientError as exc:
        logger.warning("LLM chat request failed: %s", exc)
        raise HTTPException(status_code=503, detail="No LLM connection") from exc
    except Exception as exc:  # pragma: no cover - runtime surfacing
        logger.exception("LLM chat request failed")
        raise HTTPException(status_code=500, detail=f"LLM chat failed: {exc}") from exc
