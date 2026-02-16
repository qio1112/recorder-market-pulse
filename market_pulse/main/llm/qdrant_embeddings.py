"""
Utility helpers for embedding text with Qdrant's FastEmbed integration.

Requires `qdrant-client[fastembed]` to be installed in the environment.
"""

from typing import Iterable, List, Optional, Sequence
import logging

logger = logging.getLogger(__name__)

try:
    from qdrant_client.fastembed import TextEmbedding
except ImportError:  # pragma: no cover - surfaced at call time
    TextEmbedding = None  # type: ignore


DEFAULT_MODEL_NAME = "BAAI/bge-small-en-v1.5"


class QdrantEmbeddingService:
    """
    Thin wrapper around Qdrant's FastEmbed to produce embeddings for text.
    """

    def __init__(self, model_name: str = DEFAULT_MODEL_NAME, cache_dir: Optional[str] = None):
        if TextEmbedding is None:
            raise ImportError("qdrant-client[fastembed] is required to use QdrantEmbeddingService")
        self.model_name = model_name
        self.cache_dir = cache_dir
        self._embedder = TextEmbedding(model_name=model_name, cache_dir=cache_dir)

    def embed_text(self, text: str) -> List[float]:
        """
        Embed a single piece of text and return the vector as a list of floats.
        """
        cleaned = text.strip()
        if not cleaned:
            raise ValueError("text must be non-empty")
        embedding = next(self._embedder.embed([cleaned]))
        return self._to_list(embedding)

    def embed_texts(self, texts: Iterable[str]) -> List[List[float]]:
        """
        Embed a batch of texts and return a list of vectors.
        """
        cleaned = [text.strip() for text in texts if text and text.strip()]
        if not cleaned:
            return []
        return [self._to_list(embedding) for embedding in self._embedder.embed(cleaned)]

    @staticmethod
    def _to_list(embedding: Sequence[float]) -> List[float]:
        if hasattr(embedding, "tolist"):
            try:
                return list(embedding.tolist())
            except Exception:  # pragma: no cover - defensive fallback
                logger.exception("Failed to convert embedding to list via tolist")
        return [float(value) for value in embedding]


def embed_text(text: str, model_name: str = DEFAULT_MODEL_NAME, cache_dir: Optional[str] = None) -> List[float]:
    """
    Convenience wrapper to embed a single string without managing a service instance.
    """
    return QdrantEmbeddingService(model_name=model_name, cache_dir=cache_dir).embed_text(text)


def embed_texts(texts: Iterable[str], model_name: str = DEFAULT_MODEL_NAME, cache_dir: Optional[str] = None) -> List[List[float]]:
    """
    Convenience wrapper to embed multiple strings without managing a service instance.
    """
    return QdrantEmbeddingService(model_name=model_name, cache_dir=cache_dir).embed_texts(texts)
