"""AKShare 数据微服务 —— 为 Java Agent 提供 A 股财务数据"""
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from router import finance, valuation, industry

app = FastAPI(
    title="AKShare Finance Data Service",
    description="为投资分析 Agent 提供财务数据、估值指标、行业对比",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(finance.router)
app.include_router(valuation.router)
app.include_router(industry.router)


@app.get("/api/v1/health")
def health():
    return {"status": "ok", "service": "akshare-finance-service"}


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8899, reload=False)
