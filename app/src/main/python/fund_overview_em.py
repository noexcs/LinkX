import json
from io import StringIO

import _chaquopy_patch  # noqa: F401 — monkey-patches lxml→html.parser for Chaquopy
import akshare as ak
import pandas as pd
import requests


def fund_overview_em(symbol):
    """基金基本概况。返回: 基金全称, 简称, 类型, 成立日期/规模, 净资产规模, 管理人, 托管人, 基金经理, 费率, 业绩比较基准等."""
    try:
        df = ak.fund_overview_em(symbol=symbol)
        d = df.to_dict(orient="records")
        return json.dumps(d[0], ensure_ascii=False, indent=2) if d else "{}"
    except Exception:
        return _fund_overview_em_direct(symbol)


def _fund_overview_em_direct(symbol):
    """Direct implementation bypassing akshare's pd.read_html chain."""
    url = f"https://fundf10.eastmoney.com/jbgk_{symbol}.html"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                      "(KHTML, like Gecko) Chrome/80.0.3987.149 Safari/537.36"
    }
    r = requests.get(url, headers=headers)
    r.encoding = "utf-8"

    try:
        tables = pd.read_html(StringIO(r.text))
    except Exception:
        return json.dumps({"error": "无法解析基金概况页面"}, ensure_ascii=False)

    if not tables:
        return "{}"

    # The last table contains the key-value pairs we need
    last_table = tables[-1]
    result = {}
    for _, row in last_table.iterrows():
        if len(row) >= 4:
            result[str(row[0]).strip()] = str(row[1]).strip()
            result[str(row[2]).strip()] = str(row[3]).strip()
        elif len(row) >= 2:
            result[str(row[0]).strip()] = str(row[1]).strip()

    return json.dumps(result, ensure_ascii=False, indent=2)
