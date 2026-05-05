import _chaquopy_patch  # noqa: F401 — monkey-patches lxml→html.parser for Chaquopy
import akshare as ak


def stock_individual_fund_flow(stock, market="sh", limit=20):
    """个股资金流向。market: sh/sz/bj, 返回: 日期, 主力净流入, 超大单净流入, 大单净流入, 中单净流入, 小单净流入."""
    df = ak.stock_individual_fund_flow(stock=stock, market=market)
    return df.head(limit).to_markdown(index=False)


def stock_market_fund_flow(limit=20):
    """大盘资金流向。返回: 日期, 上证主力净流入, 深证主力净流入, 创业板主力净流入."""
    df = ak.stock_market_fund_flow()
    return df.head(limit).to_markdown(index=False)


def stock_sector_fund_flow_rank(indicator="今日", sector_type="行业资金流", limit=20):
    """板块资金流向排名。indicator: 今日/5日/10日, sector_type: 行业资金流/概念资金流/地域资金流."""
    df = ak.stock_sector_fund_flow_rank(indicator=indicator, sector_type=sector_type)
    return df.head(limit).to_markdown(index=False)


def stock_sector_fund_flow_summary(symbol, indicator="今日", limit=20):
    """行业板块个股资金流向。返回指定行业内个股的资金流向详情。indicator: 今日/5日/10日."""
    df = ak.stock_sector_fund_flow_summary(symbol=symbol, indicator=indicator)
    return df.head(limit).to_markdown(index=False)


def stock_sector_fund_flow_hist(symbol, limit=30):
    """行业板块历史资金流向。返回指定行业板块的资金流向历史走势."""
    df = ak.stock_sector_fund_flow_hist(symbol=symbol)
    return df.tail(limit).to_markdown(index=False)


def stock_concept_fund_flow_hist(symbol, limit=30):
    """概念板块历史资金流向。返回指定概念板块的资金流向历史走势."""
    df = ak.stock_concept_fund_flow_hist(symbol=symbol)
    return df.tail(limit).to_markdown(index=False)
