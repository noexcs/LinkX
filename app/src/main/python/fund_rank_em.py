import _chaquopy_patch  # noqa: F401 — monkey-patches lxml→html5lib for Chaquopy
import akshare as ak


def fund_open_fund_rank_em(symbol="全部", sort_by="近1月", sort_ascending=False, limit=20):
    """开放式基金排行榜。symbol: 全部/股票型/混合型/债券型/指数型/QDII/FOF。返回: 基金代码, 简称, 单位净值, 日增长率-成立来等."""
    df = ak.fund_open_fund_rank_em(symbol=symbol)
    df.drop(columns=["序号"], inplace=True)
    df = df.sort_values(by=sort_by, ascending=sort_ascending).iloc[:limit]
    return df.to_markdown(index=False)
