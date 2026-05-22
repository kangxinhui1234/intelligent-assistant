"""行业分析 & 公司基本信息"""
import akshare as ak
from fastapi import APIRouter, Query
from . import to_em_code, safe_to_json

router = APIRouter(prefix="/api/v1", tags=["industry"])


@router.get("/industry/peers/{stock_code}")
def get_industry_peers(stock_code: str):
    """
    获取同行业公司列表及对比数据。
    通过股票代码推断行业，返回同行业公司。
    """
    em_code = to_em_code(stock_code)
    result = {
        "stock_code": stock_code,
        "em_code": em_code,
        "status": "ok",
        "industry_name": None,
        "peers": [],
        "errors": [],
    }

    try:
        # 获取股票所属行业
        info_df = ak.stock_individual_info_em(symbol=stock_code)
        info_dict = dict(zip(info_df["item"].astype(str), info_df["value"].astype(str)))
        industry_name = info_dict.get("行业", info_dict.get("所属行业", ""))
        result["industry_name"] = industry_name

        # 查同行业股票
        if industry_name:
            peers_df = ak.stock_board_industry_cons_em(symbol=industry_name)
            peers_df = peers_df.head(30) if len(peers_df) > 30 else peers_df
            peers_df = peers_df.where(peers_df.notna(), None)
            import json
            result["peers"] = json.loads(peers_df.to_json(orient="records", force_ascii=False))
    except Exception as e:
        result["errors"].append(f"行业对比: {e}")

    return result


@router.get("/stock_info/{stock_code}")
def get_stock_info(stock_code: str):
    """
    公司基本信息：主营业务、行业、上市日期、总股本等。
    """
    result = {
        "stock_code": stock_code,
        "status": "ok",
        "info": {},
        "errors": [],
    }
    try:
        df = ak.stock_individual_info_em(symbol=stock_code)
        df = df.where(df.notna(), None)
        result["info"] = dict(zip(df["item"].astype(str), df["value"].astype(str)))
    except Exception as e:
        result["errors"].append(f"公司信息: {e}")
    return result


@router.get("/industry/names")
def list_industry_names():
    """列出所有行业板块名称"""
    try:
        df = ak.stock_board_industry_name_em()
        return {
            "status": "ok",
            "count": len(df),
            "industries": df["板块名称"].tolist(),
        }
    except Exception as e:
        return {"status": "error", "error": str(e)}
