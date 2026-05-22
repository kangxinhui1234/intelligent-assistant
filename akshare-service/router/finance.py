"""财务三表 + 核心指标"""
import akshare as ak
from fastapi import APIRouter, Query
from . import to_em_code, safe_to_json

router = APIRouter(prefix="/api/v1", tags=["finance"])


@router.get("/finance/{stock_code}")
def get_finance(stock_code: str, years: int = Query(default=5, ge=1, le=15)):
    """
    获取财务三表 + 核心财务指标。
    stock_code: '600519' 或 'SH600519' 均可
    返回: 利润表 + 资产负债表 + 现金流量表 + 核心指标
    """
    em_code = to_em_code(stock_code)
    result = {
        "stock_code": stock_code,
        "em_code": em_code,
        "status": "ok",
        "income_statement": [],
        "balance_sheet": [],
        "cash_flow": [],
        "key_indicators": [],
        "errors": [],
    }

    # 利润表
    try:
        df = ak.stock_profit_sheet_by_report_em(symbol=em_code)
        result["income_statement"] = safe_to_json(df, years)
    except Exception as e:
        result["errors"].append(f"利润表: {e}")

    # 资产负债表
    try:
        df = ak.stock_balance_sheet_by_report_em(symbol=em_code)
        result["balance_sheet"] = safe_to_json(df, years)
    except Exception as e:
        result["errors"].append(f"资产负债表: {e}")

    # 现金流量表
    try:
        df = ak.stock_cash_flow_sheet_by_report_em(symbol=em_code)
        result["cash_flow"] = safe_to_json(df, years)
    except Exception as e:
        result["errors"].append(f"现金流量表: {e}")

    # 财务分析指标（ROE/ROA/毛利率/净利率等）
    try:
        df = ak.stock_financial_analysis_indicator(symbol=stock_code, start_year=str(2026 - years))
        result["key_indicators"] = safe_to_json(df, years)
    except Exception as e:
        result["errors"].append(f"财务指标: {e}")

    return result
