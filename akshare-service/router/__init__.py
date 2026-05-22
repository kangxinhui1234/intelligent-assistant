"""共享工具函数"""
import json


def to_em_code(stock_code: str) -> str:
    """
    将纯数字代码转为东方财富格式 (SH/SZ前缀)。
    - 6开头 → 上海 SH
    - 0/3开头 → 深圳 SZ
    - 已有前缀则直接返回
    """
    code = stock_code.strip()
    if code.startswith(("SH", "SZ")):
        return code
    if code.startswith("6"):
        return f"SH{code}"
    return f"SZ{code}"


def safe_to_json(df, years: int = 5):
    """DataFrame → JSON-safe list of dicts, 只取最近N年, NaN→None"""
    if df is None or len(df) == 0:
        return []
    df = df.head(years).where(df.notna(), None)
    return json.loads(df.to_json(orient="records", force_ascii=False))
