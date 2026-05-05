import _chaquopy_patch  # noqa: F401 — monkey-patches lxml→html.parser for Chaquopy
import akshare as ak


def stock_intraday_em(symbol="603777"):
    """东方财富日内分时数据。返回: 时间, 价格, 成交量, 成交额, 均价."""
    df = ak.stock_intraday_em(symbol=symbol)
    return df.to_markdown(index=False)


def stock_intraday_sina(symbol, date):
    """新浪财经日内分时数据。symbol需带前缀(如sz000001), date格式YYYYMMDD。返回: ticktime, price, volume, prev_price, kind."""
    df = ak.stock_intraday_sina(symbol=symbol, date=date)
    return df.to_markdown(index=False)
