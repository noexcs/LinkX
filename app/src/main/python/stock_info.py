import json

import _chaquopy_patch  # noqa: F401 — monkey-patches lxml→html.parser for Chaquopy
import akshare as ak


def stock_individual_info_em(symbol):
    """个股基本信息。返回: 最新价, 股票代码, 股票简称, 总股本, 流通股, 总市值, 流通市值, 行业, 上市时间."""
    df = ak.stock_individual_info_em(symbol=symbol)
    d = df.to_dict(orient="records")
    return json.dumps(d, ensure_ascii=False, indent=2) if d else "{}"


def stock_individual_spot_xq(symbol):
    """雪球个股行情。自动补全SH/SZ前缀, 返回30+字段: 代码, 名称, 现价, 涨跌幅, 成交量, 总市值, 流通值等."""
    code = _normalize_xq_code(symbol)
    df = ak.stock_individual_spot_xq(symbol=code)
    return df.to_markdown(index=False)


def _normalize_xq_code(symbol):
    """Normalize stock code to Xueqiu format (SH/SZ + 6 digits)."""
    s = symbol.strip()
    if s.upper().startswith(("SH", "SZ", "BJ")):
        return s.upper()
    if len(s) == 6:
        if s.startswith(("6", "9")):
            return f"SH{s}"
        elif s.startswith(("0", "3")):
            return f"SZ{s}"
        elif s.startswith(("4", "8")):
            return f"BJ{s}"
    return s
