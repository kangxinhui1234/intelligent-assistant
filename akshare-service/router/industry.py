"""行业分析 & 公司基本信息"""
import akshare as ak
from fastapi import APIRouter, Query
from . import to_em_code, safe_to_json
import json

router = APIRouter(prefix="/api/v1", tags=["industry"])


def _get_industry_for(stock_code: str) -> str | None:
    """通过股票代码获取行业名称，多重回退策略"""
    em_code = to_em_code(stock_code)

    # 方法1: 雪球(更可靠)
    try:
        df = ak.stock_individual_basic_info_xq(symbol=em_code)
        info = dict(zip(df["item"].astype(str), df["value"].astype(str)))
        # XQ 返回 affiliate_industry 字段，包含 {"ind_name": "保险"}
        aff = info.get("affiliate_industry", "")
        if aff and "ind_name" in aff:
            import json
            obj = json.loads(aff.replace("'", '"'))
            return obj.get("ind_name", "")
    except Exception:
        pass

    # 方法2: 东方财富
    try:
        df = ak.stock_individual_info_em(symbol=stock_code)
        info = dict(zip(df["item"].astype(str), df["value"].astype(str)))
        for k in ["行业", "所属行业", "industry"]:
            if k in info:
                return info[k]
    except Exception:
        pass

    return None


@router.get("/industry/peers/{stock_code}")
def get_industry_peers(stock_code: str):
    """
    获取同行业公司列表及对比数据。
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

    industry_name = _get_industry_for(stock_code)
    result["industry_name"] = industry_name

    if industry_name:
        try:
            peers_df = ak.stock_board_industry_cons_em(symbol=industry_name)
            if len(peers_df) > 0:
                peers_df = peers_df.head(30)
                peers_df = peers_df.where(peers_df.notna(), None)
                result["peers"] = json.loads(peers_df.to_json(orient="records", force_ascii=False))
        except Exception as e:
            result["errors"].append(f"同行业查询: {e}")
    else:
        result["errors"].append("无法确定行业分类")

    return result


@router.get("/stock_info/{stock_code}")
def get_stock_info(stock_code: str):
    """
    公司基本信息，多重回退策略确保数据可靠。
    """
    result = {
        "stock_code": stock_code,
        "status": "ok",
        "info": {},
        "errors": [],
    }

    def _item_to_dict(df):
        """item/value 两列表 → dict, 处理 NaN"""
        df = df.where(df.notna(), None)
        pairs = [(str(it), str(val)) for it, val in zip(df["item"], df["value"])]
        return dict(pairs)

    info = {}

    # 方法1: 雪球(更可靠)
    try:
        em_code = to_em_code(stock_code)
        df = ak.stock_individual_basic_info_xq(symbol=em_code)
        info = _item_to_dict(df)
        if len(info) < 3:
            raise ValueError("XQ returned too few fields")
    except Exception as e1:
        # 方法2: 东方财富
        try:
            df = ak.stock_individual_info_em(symbol=stock_code)
            info = _item_to_dict(df)
        except Exception as e2:
            result["errors"].append(f"公司信息(xq): {e1}")
            result["errors"].append(f"公司信息(em): {e2}")

    result["info"] = info

    # 补充行业信息
    has_industry = any(k for k in info if "行业" in k or "industry" in k.lower())
    if not has_industry:
        ind = _get_industry_for(stock_code)
        if ind:
            result["info"]["行业"] = ind

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
