"""估值指标"""
import akshare as ak
from fastapi import APIRouter, Query
from . import to_em_code, safe_to_json

router = APIRouter(prefix="/api/v1", tags=["valuation"])


@router.get("/valuation/{stock_code}")
def get_valuation(stock_code: str, years: int = Query(default=5, ge=1, le=10)):
    """
    获取估值对比指标: PE-TTM, PB, PS, 市值, PEG, 预测PE等。
    stock_code: '600519' 或 'SH600519' 均可
    """
    em_code = to_em_code(stock_code)
    result = {
        "stock_code": stock_code,
        "em_code": em_code,
        "status": "ok",
        "valuation": [],
        "errors": [],
    }

    try:
        df = ak.stock_zh_valuation_comparison_em(symbol=em_code)
        result["valuation"] = safe_to_json(df, years)
    except Exception as e:
        result["errors"].append(f"估值对比: {e}")

    # 财务分析指标中也有估值数据(PE/PB等)，作为补充
    try:
        df = ak.stock_financial_analysis_indicator(symbol=stock_code, start_year=str(2026 - years))
        # 只提取估值相关列
        valuation_cols = [c for c in df.columns if any(
            k in str(c) for k in ['市盈率', '市净率', '市销率', '市现率', '每股', 'PE', 'PB', 'PS', 'EPS']
        )]
        if valuation_cols:
            subset = df[valuation_cols].head(years).where(df.notna(), None)
            import json
            result["indicator_valuation"] = json.loads(subset.to_json(orient="records", force_ascii=False))
    except Exception as e:
        result["errors"].append(f"估值补充指标: {e}")

    return result
