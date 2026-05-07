from fastapi import FastAPI

from main.api.stock_api import router as stock_router
from main.api.qdrant_api import router as qdrant_router
from main.api.option_api import router as option_router

app = FastAPI(title="Market Pulse API", version="1.0.0")

app.include_router(stock_router)
app.include_router(qdrant_router)
app.include_router(option_router)
