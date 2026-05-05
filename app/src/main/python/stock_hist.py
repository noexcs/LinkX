import time

import _chaquopy_patch  # noqa: F401 — monkey-patches lxml→html.parser for Chaquopy
import akshare as ak


def stock_zh_a_hist(symbol, period="daily", start_date="20240101", end_date="20500101",
                    adjust="qfq", limit=60):
    """A股历史行情。返回: 日期, 开盘, 收盘, 最高, 最低, 成交量, 成交额, 振幅, 涨跌幅, 涨跌额, 换手率.

    period: daily/weekly/monthly
    adjust: qfq(前复权)/hfq(后复权)/""(不复权)
    东方财富失败时自动回退腾讯证券。
    """
    # Try East Money (3 retries)
    for attempt in range(3):
        try:
            df = ak.stock_zh_a_hist(
                symbol=symbol, period=period,
                start_date=start_date, end_date=end_date, adjust=adjust
            )
            if not df.empty:
                df = df.tail(limit)
                return df.to_markdown(index=False)
        except Exception:
            if attempt < 2:
                time.sleep(1)

    # Fallback: Tencent Securities (3 retries)
    code = _normalize_for_tx(symbol)
    for attempt in range(3):
        try:
            df = ak.stock_zh_a_hist_tx(
                symbol=code, start_date=start_date, end_date=end_date, adjust=adjust
            )
            if not df.empty:
                df = df.tail(limit)
                return df.to_markdown(index=False)
        except Exception:
            if attempt < 2:
                time.sleep(1)

    return f"无法获取 {symbol} 的历史行情数据"


def _normalize_for_tx(symbol):
    """Convert stock code to Tencent format (sh/sz + digits)."""
    s = symbol.strip()
    if s.lower().startswith(("sh", "sz", "bj")):
        return s.lower()
    if len(s) == 6:
        if s.startswith(("6", "9")):
            return f"sh{s}"
        elif s.startswith(("0", "3")):
            return f"sz{s}"
        elif s.startswith(("4", "8")):
            return f"bj{s}"
    return s
