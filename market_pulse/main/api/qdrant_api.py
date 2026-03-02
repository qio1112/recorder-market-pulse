"""Qdrant-backed record embedding APIs."""

from typing import List, Optional
import logging

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from main.ml import qdrant_embeddings as qe
from main.utils.logger_utils import setup_logging

router = APIRouter(prefix="/qdrant", tags=["qdrant"])
logger = setup_logging("qdrant_api")

# Load once per process
_QDRANT = qe.get_qdrant_client()
_EMBED_MODEL = qe.get_embed_model()


def _ensure_default_collection():
    try:
        vector_dim = _EMBED_MODEL.get_sentence_embedding_dimension()
        qe.ensure_collection(_QDRANT, qe.COLLECTION, vector_dim)
        logger.info("Ensured Qdrant collection '%s' exists", qe.COLLECTION)
    except Exception as exc:  # pragma: no cover - startup safety
        logger.error("Failed to ensure Qdrant collection '%s': %s", qe.COLLECTION, exc)


_ensure_default_collection()


def _get_clients():
    return _QDRANT, _EMBED_MODEL


class UpsertRequest(BaseModel):
    record_id: str
    owner_user_id: str
    is_public: bool
    text: str
    labels: Optional[List[str]] = None
    chunk_words: Optional[int] = Field(
        default=None, gt=0, description="Override default chunk size."
    )
    chunk_overlap: Optional[int] = Field(
        default=None, ge=0, description="Override default chunk overlap."
    )
    collection: str = Field(default=qe.COLLECTION)


class QueryRequest(BaseModel):
    user_id: str
    query_text: str
    similarity_threshold: Optional[float] = Field(
        default=None, description="Minimum similarity score to include a chunk."
    )
    limit: int = Field(default=20, gt=0)
    collection: str = Field(default=qe.COLLECTION)


class DeleteRequest(BaseModel):
    record_id: str
    collection: str = Field(default=qe.COLLECTION)


class ExistsRequest(BaseModel):
    record_id: str
    collection: str = Field(default=qe.COLLECTION)


@router.post("/upsert")
def upsert_record(payload: UpsertRequest, deps=Depends(_get_clients)):
    qdrant, embed_model = deps
    try:
        point_ids = qe.upsert_vectors_by_record_id(
            qdrant=qdrant,
            embed_model=embed_model,
            record_id=payload.record_id,
            owner_user_id=payload.owner_user_id,
            is_public=payload.is_public,
            text=payload.text,
            labels=payload.labels,
            chunk_words=payload.chunk_words if payload.chunk_words is not None else qe.CHUNK_WORDS,
            chunk_overlap=payload.chunk_overlap if payload.chunk_overlap is not None else qe.CHUNK_OVERLAP,
            collection=payload.collection,
        )
        logger.info(f"Upserted record {payload.record_id} to qdrant")
    except Exception as exc:  # pragma: no cover - runtime surfacing
        logger.error(f"Failed to upsert record {payload.record_id} to qdrant, error: \n{exc}")
        raise HTTPException(status_code=500, detail=f"qdrant upsert failed: {exc}") from exc
    return {"point_ids": point_ids}


@router.post("/query")
def query_records(payload: QueryRequest, deps=Depends(_get_clients)):
    qdrant, embed_model = deps
    try:
        hits = qe.search_record_ids_by_similarity(
            qdrant=qdrant,
            embed_model=embed_model,
            user_id=payload.user_id,
            query_text=payload.query_text,
            limit=payload.limit,
            collection=payload.collection,
            similarity_threshold=payload.similarity_threshold,
        )
        logger.info(f"Queried qdrant with text: {payload.query_text}")
    except Exception as exc:  # pragma: no cover - runtime surfacing
        logger.error(f"Failed to query qdrant with text: {payload.query_text}, error:\n{exc}")
        raise HTTPException(status_code=500, detail=f"qdrant query failed: {exc}") from exc

    # Aggregate chunks per record_id
    aggregated: dict[str, dict] = {}
    for hit in hits:
        record_id = hit.get("record_id")
        if not record_id:
            continue
        entry = aggregated.setdefault(
            record_id,
            {
                "record_id": record_id,
                "owner_user_id": hit.get("owner_user_id"),
                "is_public": hit.get("is_public"),
                "chunks": [],
                "best_score": hit.get("score"),
            },
        )
        if hit.get("chunk_text"):
            entry["chunks"].append(hit["chunk_text"])
        score = hit.get("score")
        if score is not None and (entry["best_score"] is None or score > entry["best_score"]):
            entry["best_score"] = score

    return {"results": list(aggregated.values())}


@router.post("/delete")
def delete_record(payload: DeleteRequest, deps=Depends(_get_clients)):
    qdrant, _ = deps
    try:
        qe.delete_vectors_by_record_id(
            qdrant=qdrant, record_id=payload.record_id, collection=payload.collection
        )
        logger.info(f"Deleted record {payload.record_id} from qdrant.")
    except Exception as exc:  # pragma: no cover - runtime surfacing
        logger.error(f"Failed to delete record {payload.record_id} from qdrant. error:\n{exc}")
        raise HTTPException(status_code=500, detail=f"qdrant delete failed: {exc}") from exc
    return {"deleted": True}


@router.post("/exists")
def record_exists(payload: ExistsRequest, deps=Depends(_get_clients)):
    qdrant, _ = deps
    try:
        exists = qe.record_exists(
            qdrant=qdrant, record_id=payload.record_id, collection=payload.collection
        )
    except Exception as exc:  # pragma: no cover - runtime surfacing
        raise HTTPException(status_code=500, detail=f"qdrant exists check failed: {exc}") from exc
    return {"record_id": payload.record_id, "exists": exists}
