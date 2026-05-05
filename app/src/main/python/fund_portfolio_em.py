import json
import re
from io import StringIO

import _chaquopy_patch  # noqa: F401 — monkey-patches lxml→html.parser for Chaquopy
import akshare as ak
import pandas as pd
import requests


def fund_portfolio_hold_em(symbol, date, limit=15):
    """基金股票持仓。返回前limit只股票: 股票代码, 股票名称, 占净值比例, 持股数, 持股市值."""
    try:
        return _fund_portfolio_hold_em_akshare(symbol, date, limit)
    except Exception:
        return _fund_portfolio_hold_em_direct(symbol, date, limit)


def _fund_portfolio_hold_em_akshare(symbol, date, limit):
    df = ak.fund_portfolio_hold_em(symbol=symbol, date=date)
    df.drop(columns=["序号"], inplace=True)
    for q in (4, 3, 2, 1):
        mask = df["季度"] == f"{date}年{q}季度股票投资明细"
        df_q = df[mask]
        if df_q.shape[0] > 0:
            df = df_q
            break
    df = df.sort_values(by="占净值比例", ascending=False).iloc[:limit]
    return df.to_markdown(index=False)


def _fund_portfolio_hold_em_direct(symbol, date, limit):
    """Direct API call fallback for fund_portfolio_hold_em."""
    content, labels = _fetch_portfolio_content(symbol, date, "jjcc", {"topline": "10000", "month": ""})
    all_tables = pd.read_html(StringIO(content), converters={"股票代码": str})

    if not labels:
        return "无持仓数据"

    big_df = pd.DataFrame()
    for i in range(min(len(labels), len(all_tables))):
        t = all_tables[i]
        # Normalize column names
        _normalize_portfolio_columns(t, "stock")
        if "占净值比例" in t.columns:
            t["占净值比例"] = t["占净值比例"].astype(str).str.replace("%", "", regex=False)
            t["占净值比例"] = pd.to_numeric(t["占净值比例"], errors="coerce")
        t["季度"] = labels[i]
        big_df = pd.concat([big_df, t], ignore_index=True)

    cols = [c for c in ["股票代码", "股票名称", "占净值比例", "持股数", "持仓市值", "季度"] if c in big_df.columns]
    big_df = big_df[cols]
    # Filter to latest quarter with data (same logic as akshare wrapper)
    for q in (4, 3, 2, 1):
        mask = big_df["季度"].str.contains(f"{date}年{q}季度")
        df_q = big_df[mask]
        if df_q.shape[0] > 0:
            big_df = df_q
            break
    big_df["占净值比例"] = pd.to_numeric(big_df["占净值比例"], errors="coerce")
    big_df = big_df.sort_values(by="占净值比例", ascending=False).iloc[:limit]
    return big_df.to_markdown(index=False)


def fund_portfolio_bond_hold_em(symbol, date, limit=15):
    """基金债券持仓。返回前limit只债券: 债券代码, 债券名称, 占净值比例, 持仓市值."""
    try:
        return _fund_portfolio_bond_hold_em_akshare(symbol, date, limit)
    except Exception:
        return _fund_portfolio_bond_hold_em_direct(symbol, date, limit)


def _fund_portfolio_bond_hold_em_akshare(symbol, date, limit):
    df = ak.fund_portfolio_bond_hold_em(symbol=symbol, date=date)
    df.drop(columns=["序号"], inplace=True)
    for q in (4, 3, 2, 1):
        mask = df["季度"] == f"{date}年{q}季度债券投资明细"
        df_q = df[mask]
        if df_q.shape[0] > 0:
            df = df_q
            break
    df = df.sort_values(by="占净值比例", ascending=False).iloc[:limit]
    return df.to_markdown(index=False)


def _fund_portfolio_bond_hold_em_direct(symbol, date, limit):
    """Direct API call fallback for fund_portfolio_bond_hold_em."""
    content, labels = _fetch_portfolio_content(symbol, date, "zqcc", {})
    all_tables = pd.read_html(StringIO(content), converters={"债券代码": str})

    if not labels:
        return "无持仓数据"

    big_df = pd.DataFrame()
    for i in range(min(len(labels), len(all_tables))):
        t = all_tables[i]
        _normalize_portfolio_columns(t, "bond")
        if "占净值比例" in t.columns:
            t["占净值比例"] = t["占净值比例"].astype(str).str.replace("%", "", regex=False)
            t["占净值比例"] = pd.to_numeric(t["占净值比例"], errors="coerce")
        # Normalize 持仓市值 column name
        for c in t.columns:
            if "持仓市值" in c:
                t.rename(columns={c: "持仓市值"}, inplace=True)
                break
        if "持仓市值" in t.columns:
            t["持仓市值"] = pd.to_numeric(t["持仓市值"], errors="coerce")
        t["季度"] = labels[i]
        big_df = pd.concat([big_df, t], ignore_index=True)

    cols = [c for c in ["债券代码", "债券名称", "占净值比例", "持仓市值", "季度"] if c in big_df.columns]
    big_df = big_df[cols]
    # Filter to latest quarter with data
    for q in (4, 3, 2, 1):
        mask = big_df["季度"].str.contains(f"{date}年{q}季度")
        df_q = big_df[mask]
        if df_q.shape[0] > 0:
            big_df = df_q
            break
    big_df["占净值比例"] = pd.to_numeric(big_df["占净值比例"], errors="coerce")
    big_df = big_df.sort_values(by="占净值比例", ascending=False).iloc[:limit]
    return big_df.to_markdown(index=False)


def fund_portfolio_industry_allocation_em(symbol, date):
    """基金行业配置。返回最新季度的行业分布: 行业名称, 占净值比例."""
    df = ak.fund_portfolio_industry_allocation_em(symbol=symbol, date=date)
    df.drop(columns=["序号"], inplace=True)
    df = df.sort_values(by="截止时间", ascending=False)
    df = df[df["截止时间"] == df.iloc[0]["截止时间"]]
    df = df.sort_values(by="占净值比例", ascending=False)
    return df.to_markdown(index=False)


def fund_portfolio_change_em(symbol, indicator, date):
    """基金投资组合重大变动。indicator: 累计买入/累计卖出。返回: 股票代码, 股票名称, 本期累计买入/卖出金额, 占期初基金资产净值比例."""
    try:
        df = _fund_portfolio_change_em_akshare(symbol, indicator, date)
    except Exception:
        df = _fund_portfolio_change_em_direct(symbol, indicator, date)
    df.drop(columns=["序号"], inplace=True, errors="ignore")
    return df.to_markdown(index=False)


def _fund_portfolio_change_em_akshare(symbol, indicator, date):
    return ak.fund_portfolio_change_em(symbol=symbol, indicator=indicator, date=date)


def _fund_portfolio_change_em_direct(symbol, indicator, date):
    """Direct API call fallback for fund_portfolio_change_em."""
    indicator_map = {"累计买入": "1", "累计卖出": "2"}
    content, labels = _fetch_portfolio_content(
        symbol, date, "zdbd", {"zdbd": indicator_map.get(indicator, "1")}
    )
    all_tables = pd.read_html(StringIO(content), converters={"股票代码": str})

    if not labels:
        return pd.DataFrame()

    big_df = pd.DataFrame()
    for i in range(min(len(labels), len(all_tables))):
        t = all_tables[i]
        # Remove 相关资讯 column if present
        if "相关资讯" in t.columns:
            del t["相关资讯"]
        # Normalize key columns
        for col in t.columns:
            if "占期初基金资产净值比例" in col:
                t.rename(columns={col: "占期初基金资产净值比例"}, inplace=True)
                break
        if "占期初基金资产净值比例" in t.columns:
            t["占期初基金资产净值比例"] = (
                t["占期初基金资产净值比例"].astype(str).str.replace("%", "", regex=False)
            )
            t["占期初基金资产净值比例"] = pd.to_numeric(
                t["占期初基金资产净值比例"], errors="coerce"
            )
        t["季度"] = labels[i]
        big_df = pd.concat([big_df, t], ignore_index=True)

    return big_df


# --- Shared helpers ---

def _fetch_portfolio_content(symbol, date, data_type, extra_params):
    """Fetch the HTML content and extract h4 labels from East Money fund archives."""
    url = "https://fundf10.eastmoney.com/FundArchivesDatas.aspx"
    params = {
        "type": data_type,
        "code": symbol,
        "year": date,
        "rt": "0.913877030254846",
        **extra_params,
    }
    r = requests.get(url, params=params)
    data_text = r.text

    # The API returns: var apidata={ content:"...", ... }
    # Use regex to extract the content field (avoiding fragile JSON parsing)
    content_match = re.search(r'content:\s*"', data_text)
    if content_match:
        start = content_match.end()
        # Find the closing quote by tracking escape sequences
        content_parts = []
        i = start
        while i < len(data_text):
            if data_text[i] == '\\' and i + 1 < len(data_text):
                content_parts.append(data_text[i + 1])
                i += 2
            elif data_text[i] == '"':
                break
            else:
                content_parts.append(data_text[i])
                i += 1
        content = "".join(content_parts)
    else:
        # Fallback: try to extract between first { and last }
        content = data_text[data_text.find("{") + 1 : data_text.rfind("}")]
        content = re.search(r'"content":"(.*?)"', content, re.DOTALL)
        content = content.group(1) if content else ""

    # Extract quarter labels from h4 elements
    # The content has patterns like "2024年4季度股票投资明细" inside h4 tags
    labels = re.findall(r"(\d{4}年\d季度[^<\s]+(?:明细)?)", content)
    # Deduplicate while preserving order
    seen = set()
    unique_labels = []
    for lbl in labels:
        if lbl not in seen:
            seen.add(lbl)
            unique_labels.append(lbl)

    return content, unique_labels


def _normalize_portfolio_columns(df, holding_type):
    """Normalize portfolio DataFrame column names to expected values.

    Handles both plain string columns and MultiIndex (tuple) columns
    that pd.read_html produces for tables with merged headers.
    """
    # Flatten MultiIndex columns to strings first
    if isinstance(df.columns, pd.MultiIndex):
        df.columns = [
            " ".join(str(part) for part in col if str(part) not in ("nan", "Unnamed"))
            or str(col[0])
            for col in df.columns
        ]

    rename_map = {}
    for col in df.columns:
        col_str = str(col)
        col_clean = col_str.replace(" ", "").replace("\xa0", "")
        if "占净值比例" in col_clean or "占净值" in col_clean:
            rename_map[col] = "占净值比例"
        elif ("持股数" in col_clean or "持股数" in col_str) and holding_type == "stock":
            rename_map[col] = "持股数"
        elif "持仓市值" in col_clean or ("持仓市值" in col_str) or ("市值" in col_clean and "持仓" in col_str):
            rename_map[col] = "持仓市值"
        elif "股票代码" in col_clean:
            rename_map[col] = "股票代码"
        elif "股票名称" in col_clean:
            rename_map[col] = "股票名称"
        elif "债券代码" in col_clean:
            rename_map[col] = "债券代码"
        elif "债券名称" in col_clean:
            rename_map[col] = "债券名称"
        elif "序号" in col_clean:
            rename_map[col] = "序号"
    if rename_map:
        df.rename(columns=rename_map, inplace=True)
