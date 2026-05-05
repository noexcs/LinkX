import _chaquopy_patch  # noqa: F401 — monkey-patches lxml→html.parser for Chaquopy
import akshare as ak


def stock_board_industry_spot_em(symbol="小金属", limit=30):
    """东方财富行业板块行情。返回板块内个股: 代码, 名称, 最新价, 涨跌幅, 涨跌额, 成交量, 成交额, 振幅, 换手率, 市盈率等."""
    df = ak.stock_board_industry_spot_em(symbol=symbol)
    df = df.head(limit)
    return df.to_markdown(index=False)


def stock_board_concept_spot_em(symbol="可燃冰", limit=30):
    """东方财富概念板块行情。返回概念板块内个股: 代码, 名称, 最新价, 涨跌幅, 涨跌额, 成交量, 成交额, 振幅, 换手率, 市盈率等."""
    df = ak.stock_board_concept_spot_em(symbol=symbol)
    df = df.head(limit)
    return df.to_markdown(index=False)


def stock_board_industry_name_em():
    """东方财富行业板块名称列表。返回所有行业板块名称."""
    df = ak.stock_board_industry_name_em()
    return df.to_markdown(index=False)


def stock_board_concept_name_em():
    """东方财富概念板块名称列表。返回所有概念板块名称."""
    df = ak.stock_board_concept_name_em()
    return df.to_markdown(index=False)
