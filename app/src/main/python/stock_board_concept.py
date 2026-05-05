import _chaquopy_patch  # noqa: F401 — monkey-patches lxml→html.parser for Chaquopy
import akshare as ak


def stock_board_concept_hist_em(symbol, period="daily", start_date="20240101",
                                 end_date="20500101", adjust="qfq", limit=60):
    """东方财富概念板块历史行情。返回板块指数K线: 日期, 开盘, 收盘, 最高, 最低, 成交量, 成交额, 涨跌幅等."""
    df = ak.stock_board_concept_hist_em(
        symbol=symbol, period=period, start_date=start_date,
        end_date=end_date, adjust=adjust
    )
    return df.tail(limit).to_markdown(index=False)


def stock_board_concept_cons_em(symbol="融资融券", limit=50):
    """东方财富概念板块成分股。返回概念板块包含的个股: 代码, 名称, 最新价, 涨跌幅, 涨跌额, 成交量, 成交额, 流通市值, 总市值等."""
    df = ak.stock_board_concept_cons_em(symbol=symbol)
    return df.head(limit).to_markdown(index=False)
