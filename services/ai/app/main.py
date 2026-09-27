from app.routers import docai, gap, jago, matching
from fastapi import FastAPI

app = FastAPI(title="Adi-Vritti AI Services", version="1.0.0")

app.include_router(matching.router)
app.include_router(gap.router)
app.include_router(jago.router)
app.include_router(docai.router)


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "ai"}
