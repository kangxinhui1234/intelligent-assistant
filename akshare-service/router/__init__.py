"""共享工具函数"""
import json
from datetime import datetime, timezone, timedelta

# 中国时区 UTC+8
TZ_CHINA = timezone(timedelta(hours=8))

# 利润表关键列
INCOME_COLS = {
    "REPORT_DATE", "REPORT_TYPE", "REPORT_DATE_NAME",
    "SECUCODE", "SECURITY_CODE", "SECURITY_NAME_ABBR",
    "TOTAL_OPERATE_INCOME", "OPERATE_INCOME",
    "TOTAL_OPERATE_COST", "OPERATE_COST",
    "OPERATE_PROFIT", "TOTAL_PROFIT", "NET_PROFIT",
    "PARENT_NET_PROFIT", "MINORITY_INTEREST",
    "OPERATE_TAX_ADD", "SALE_EXPENSE", "MANAGE_EXPENSE",
    "RESEARCH_EXPENSE", "FINANCE_EXPENSE",
    "INTEREST_INCOME", "INTEREST_EXPENSE",
    "INVEST_INCOME", "ASSET_IMPAIRMENT_LOSS",
    "INCOME_TAX", "BASIC_EPS", "DILUTED_EPS",
}

# 资产负债表关键列
BALANCE_COLS = {
    "REPORT_DATE", "REPORT_TYPE",
    "SECUCODE", "SECURITY_CODE", "SECURITY_NAME_ABBR",
    "TOTAL_ASSETS", "TOTAL_LIABILITIES", "TOTAL_EQUITY",
    "TOTAL_CURRENT_ASSETS", "TOTAL_CURRENT_LIABILITIES",
    "TOTAL_NON_CURRENT_ASSETS", "TOTAL_NON_CURRENT_LIABILITIES",
    "FIXED_ASSETS", "INTANGIBLE_ASSETS", "GOODWILL",
    "SHORT_TERM_LOAN", "LONG_TERM_LOAN",
    "ACCOUNTS_RECEIVABLE", "INVENTORY",
    "CASH_EQUIVALENT", "MONETARYFUNDS",
    "RETAINED_EARNINGS", "MINORITY_EQUITY",
}

# 现金流量表关键列
CASHFLOW_COLS = {
    "REPORT_DATE", "REPORT_TYPE",
    "SECUCODE", "SECURITY_CODE", "SECURITY_NAME_ABBR",
    "NET_OPERATE_CASH_FLOW", "NET_INVEST_CASH_FLOW", "NET_FINANCE_CASH_FLOW",
    "CASH_EQUIVALENT_ADD",
    "SUB_TOTAL_OPERATE_CASH_IN", "SUB_TOTAL_OPERATE_CASH_OUT",
    "SUB_TOTAL_INVEST_CASH_IN", "SUB_TOTAL_INVEST_CASH_OUT",
    "SUB_TOTAL_FINANCE_CASH_IN", "SUB_TOTAL_FINANCE_CASH_OUT",
    "SALE_GOODS_SERVICE_CASH", "BUY_GOODS_SERVICE_CASH",
    "DEPRECIATION_AMORTIZATION",
}


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


def fix_timestamp(val):
    """Unix毫秒时间戳 → 'YYYY-MM-DD' 字符串"""
    if val is None:
        return None
    try:
        v = int(val)
        if v > 1_000_000_000_000:  # 毫秒
            v = v / 1000
        dt = datetime.fromtimestamp(v, tz=TZ_CHINA)
        return dt.strftime("%Y-%m-%d")
    except (ValueError, TypeError, OSError):
        return str(val)


def fix_date_column(df, col_name="日期"):
    """原地转换 DataFrame 中的时间戳列 → 可读日期"""
    if df is None or len(df) == 0:
        return df
    if col_name in df.columns:
        df = df.copy()
        df[col_name] = df[col_name].apply(fix_timestamp)
    return df


def keep_annual_only(df, date_col="REPORT_DATE"):
    """
    只保留年报数据。
    - 对三表数据: REPORT_DATE 或以 '-12-31' 结尾的行
    - 对指标数据: 日期列以 '-12-31' 结尾的行
    """
    if df is None or len(df) == 0:
        return df
    # 尝试多个列名
    for col in [date_col, "日期", "REPORT_DATE"]:
        if col in df.columns:
            vals = df[col].astype(str)
            mask = vals.str.contains("-12-31", na=False)
            if mask.sum() > 0:
                return df[mask].copy()
    return df


def safe_to_json(df, years: int = 5):
    """DataFrame → JSON-safe list of dicts, NaN→None"""
    if df is None or len(df) == 0:
        return []
    df = df.head(years * 4).where(df.notna(), None)  # 取足够行(含季报)
    df = fix_date_column(df)
    df = keep_annual_only(df)
    df = df.head(years).where(df.notna(), None)
    return json.loads(df.to_json(orient="records", force_ascii=False))


def safe_to_json_slim(df, years: int, key_cols: set):
    """同上，只保留关键列 + 仅年报"""
    if df is None or len(df) == 0:
        return []
    # 先取足够行(年报通常每4行才有1行)
    df = df.head(years * 5 + 1)
    # 先筛选年报
    if "REPORT_TYPE" in df.columns:
        # 中文年报：一季报/中报/三季报/年报
        df = df[df["REPORT_TYPE"].str.contains("年报", na=False)].copy()
    if len(df) == 0 and "REPORT_DATE" in df.columns:
        df = keep_annual_only(df)
    df = df.head(years)
    cols = [c for c in df.columns if c in key_cols]
    df = df[cols].where(df.notna(), None)
    return json.loads(df.to_json(orient="records", force_ascii=False))
