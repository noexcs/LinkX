import _chaquopy_patch  # noqa: F401 — monkey-patches lxml→html.parser for Chaquopy
import akshare as ak


def stock_sector_spot(indicator="概念", sort_by="涨跌幅", sort_ascending=False, limit=20):
    """新浪行业板块行情。indicator: 新浪行业/启明星行业/概念/地域/行业。返回: 板块名, 公司家数, 平均价格, 涨跌幅, 总成交量, 总成交额等."""
    df = ak.stock_sector_spot(indicator=indicator)
    df = df.sort_values(sort_by, ascending=sort_ascending)
    df = df.head(limit)
    return df.to_markdown(index=False)


def stock_sector_detail(sector, sort_by="changepercent", sort_ascending=False, limit=20):
    """新浪行业板块详情。sector取自stock_sector_spot结果的label列。返回板块内个股: 代码, 名称, 最新价, 涨跌幅, 成交量, 金额, 市盈率, 市净率, 总市值等."""
    df = ak.stock_sector_detail(sector=sector)
    df = df.sort_values(sort_by, ascending=sort_ascending)
    df = df.head(limit)
    return df.to_markdown(index=False)
