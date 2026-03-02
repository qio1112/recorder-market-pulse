from fastapi import FastAPI

from main.api.stock_api import router as stock_router
from main.api.qdrant_api import router as qdrant_router

app = FastAPI(title="Market Pulse API", version="1.0.0")

app.include_router(stock_router)
app.include_router(qdrant_router)
