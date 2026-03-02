"""Helpers for embedding records into Qdrant with access control.

Functions:
- get_qdrant_client: build client
- get_embed_model: load sentence-transformers model
- upsert_vectors_by_record_id: chunk/embed/upsert with overwrite
- search_record_ids_by_similarity: query by text with ACL
- delete_vectors_by_record_id: remove vectors for a record_id
"""

from __future__ import annotations

import uuid
from typing import Any, Optional

from qdrant_client import QdrantClient
from qdrant_client.http.models import (
    Distance,
    VectorParams,
    PointStruct,
    Filter,
    FieldCondition,
    MatchValue,
    SearchParams,
    FilterSelector,
)
from sentence_transformers import SentenceTransformer

try:
    import torch
except ImportError:  # pragma: no cover - optional dependency
    torch = None

QDRANT_URL = "http://qdrant:6333"
COLLECTION = "records_chunks"
EMBED_MODEL_NAME = "BAAI/bge-m3"

# Chunking settings
CHUNK_WORDS = 60
CHUNK_OVERLAP = 10


def ensure_collection(client: QdrantClient, collection: str, vector_dim: int) -> None:
    """Create the collection if it doesn't exist."""
    existing = {c.name for c in client.get_collections().collections}
    if collection in existing:
        return

    client.create_collection(
        collection_name=collection,
        vectors_config=VectorParams(size=vector_dim, distance=Distance.COSINE),
    )


def embed_text(model: SentenceTransformer, text: str) -> list[float]:
    vec = model.encode(text, normalize_embeddings=True)
    return vec.astype("float32").tolist()


def chunk_by_words(text: str, chunk_words: int, overlap_words: int) -> list[str]:
    if chunk_words <= 0:
        raise ValueError("chunk_words must be > 0")
    if not 0 <= overlap_words < chunk_words:
        raise ValueError("overlap_words must be >= 0 and < chunk_words")

    words = text.split()
    if not words:
        return []

    step = chunk_words - overlap_words
    chunks: list[str] = []
    i = 0
    while i < len(words):
        chunk = " ".join(words[i : i + chunk_words])
        chunks.append(chunk)
        i += step
    return chunks


def _acl_filter(user_id: str) -> Filter:
    return Filter(
        should=[
            FieldCondition(key="owner_user_id", match=MatchValue(value=user_id)),
            FieldCondition(key="is_public", match=MatchValue(value=True)),
        ]
    )


def search_top_k(
    client: QdrantClient,
    collection: str,
    query_vector: list[float],
    user_id: str,
    limit: int = 20,
) -> list[Any]:
    """Return top-K nearest points (chunks) to the query vector."""
    return client.query_points(
        collection_name=collection,
        query=query_vector,
        query_filter=_acl_filter(user_id),
        limit=limit,
        with_payload=True,
        with_vectors=False,
    ).points


def search_distinct_record_ids(
    client: QdrantClient,
    collection: str,
    query_vector: list[float],
    user_id: str,
    limit: int = 20,
) -> list[str]:
    hits = search_top_k(client, collection, query_vector, user_id=user_id, limit=limit)

    best_scores: dict[str, float] = {}
    for h in hits:
        payload = h.payload or {}
        record_id = payload.get("record_id")
        if not record_id:
            continue
        if record_id not in best_scores or h.score > best_scores[record_id]:
            best_scores[record_id] = h.score

    sorted_records = sorted(best_scores.items(), key=lambda x: x[1], reverse=True)
    return [record_id for record_id, _ in sorted_records[:limit]]


def get_qdrant_client() -> QdrantClient:
    return QdrantClient(url=QDRANT_URL)


def get_embed_model() -> SentenceTransformer:
    device = "cpu"
    if torch:
        try:
            if hasattr(torch.backends, "mps") and torch.backends.mps.is_available():
                device = "mps"
            elif torch.cuda.is_available():
                device = "cuda"
        except Exception:
            device = "cpu"
    return SentenceTransformer(EMBED_MODEL_NAME, device=device)


def search_record_ids_by_similarity(
    *,
    qdrant: QdrantClient,
    embed_model: SentenceTransformer,
    user_id: str,
    query_text: str,
    limit: int = 20,
    collection: str = COLLECTION,
    similarity_threshold: Optional[float] = None,
) -> list[dict[str, Any]]:
    """Return top hits with record metadata and stored chunk text.

    Each item: {"record_id", "chunk_text", "owner_user_id", "is_public", "score"}.
    """
    vector_dim = embed_model.get_sentence_embedding_dimension()
    ensure_collection(qdrant, collection, vector_dim)

    query_vector = embed_text(embed_model, query_text)
    hits = search_top_k(
        qdrant,
        collection,
        query_vector,
        user_id=user_id,
        limit=limit,
    )

    results: list[dict[str, Any]] = []
    for h in hits:
        payload = h.payload or {}
        if similarity_threshold is not None and h.score is not None and h.score < similarity_threshold:
            continue
        results.append(
            {
                "record_id": payload.get("record_id"),
                "chunk_text": payload.get("text"),
                "owner_user_id": payload.get("owner_user_id"),
                "is_public": payload.get("is_public"),
                "score": h.score,
            }
        )
    return results


def delete_vectors_by_record_id(
    *,
    qdrant: QdrantClient,
    record_id: str,
    collection: str = COLLECTION,
) -> None:
    record_filter = Filter(
        must=[FieldCondition(key="record_id", match=MatchValue(value=record_id))]
    )
    qdrant.delete(
        collection_name=collection,
        points_selector=FilterSelector(filter=record_filter),
        wait=True,
    )


def upsert_vectors_by_record_id(
    *,
    qdrant: QdrantClient,
    embed_model: SentenceTransformer,
    record_id: str,
    owner_user_id: str,
    is_public: bool,
    text: str,
    labels: list[str] | None = None,
    chunk_words: int = CHUNK_WORDS,
    chunk_overlap: int = CHUNK_OVERLAP,
    collection: str = COLLECTION,
) -> list[str]:
    """Create or replace vectors for a record_id."""
    labels = labels or []

    vector_dim = embed_model.get_sentence_embedding_dimension()
    ensure_collection(qdrant, collection, vector_dim)

    delete_vectors_by_record_id(qdrant=qdrant, record_id=record_id, collection=collection)

    chunks = chunk_by_words(text, chunk_words, chunk_overlap)
    if not chunks:
        return []

    point_ids: list[str] = []
    for chunk_id, chunk_text in enumerate(chunks):
        vector = embed_text(embed_model, chunk_text)
        point_id = str(uuid.uuid4())
        point = PointStruct(
            id=point_id,
            vector=vector,
            payload={
                "record_id": record_id,
                "chunk_id": chunk_id,
                "labels": labels,
                "owner_user_id": owner_user_id,
                "is_public": is_public,
                "text": chunk_text,
            },
        )
        qdrant.upsert(collection_name=collection, points=[point])
        point_ids.append(point_id)

    return point_ids


def record_exists(
    *,
    qdrant: QdrantClient,
    record_id: str,
    collection: str = COLLECTION,
) -> bool:
    """Return True if any vector for the record_id exists."""
    record_filter = Filter(
        must=[FieldCondition(key="record_id", match=MatchValue(value=record_id))]
    )
    resp = qdrant.count(
        collection_name=collection,
        count_filter=record_filter,
        exact=True,
    )
    return bool(getattr(resp, "count", 0))
