import _chaquopy_patch  # noqa: F401 — monkey-patches lxml→html.parser for Chaquopy
import akshare as ak


def fund_info_index_em(symbol="全部", indicator="全部", sort_by="近1月", sort_ascending=False, limit=20):
    """基金列表-指数型。返回: 基金代码, 基金名称, 单位净值, 日增长率, 近1周-成立来, 跟踪标的等."""
    df = ak.fund_info_index_em(symbol=symbol, indicator=indicator)
    df = df.sort_values(by=sort_by, ascending=sort_ascending)
    df = df.iloc[:limit]
    return df.to_markdown(index=False)


def fund_open_fund_info_em(symbol, indicator="单位净值走势", limit=30):
    """开放式基金历史数据。indicator: 单位净值走势/累计净值走势/累计收益率走势/同类排名走势/同类排名百分比/分红送配详情/拆分详情."""
    df = ak.fund_open_fund_info_em(symbol=symbol, indicator=indicator, period="成立来")
    df = df.iloc[-limit:]
    return df.to_markdown(index=False)


def fund_etf_fund_info_em(fund, start_date, end_date):
    """场内ETF基金历史净值明细。返回: 净值日期, 单位净值, 累计净值, 日增长率, 申购状态, 赎回状态."""
    df = ak.fund_etf_fund_info_em(fund=fund, start_date=start_date, end_date=end_date)
    return df.to_markdown(index=False)


def fund_value_estimation_em(symbol):
    """基金今日净值估算。返回单只基金的: 估算值, 估算增长率, 单位净值, 日增长率, 估算偏差."""
    df = ak.fund_value_estimation_em(symbol="全部")
    df = df[df["基金代码"] == symbol]
    return df.to_markdown(index=False)


def fund_value_estimation_em_rank(limit=15):
    """基金今日估算增长率排名。按估算增长率降序排列."""
    df = ak.fund_value_estimation_em(symbol="全部")
    col = next((c for c in df.columns if "估算增长率" in c), None)
    if col:
        df = df.sort_values(by=col, ascending=False)
    df = df.iloc[:limit]
    return df.to_markdown(index=False)
