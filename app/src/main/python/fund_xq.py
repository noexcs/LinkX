import json

import _chaquopy_patch  # noqa: F401 — monkey-patches lxml→html.parser for Chaquopy
import akshare as ak
import requests


def fund_individual_basic_info_xq(symbol):
    """雪球基金基本信息。返回: 基金名称, 成立时间, 最新规模, 基金公司, 基金经理, 托管银行, 基金类型, 评级, 投资策略等."""
    js = ak.fund_individual_basic_info_xq(symbol=symbol).to_json(
        orient="records", force_ascii=False
    )
    items = json.loads(js)
    return json.dumps({i["item"]: i["value"] for i in items}, ensure_ascii=False, indent=2)


def fund_individual_achievement_xq(symbol):
    """雪球基金业绩。返回: 业绩类型, 周期, 本产品区间收益, 本产品最大回撤, 周期收益同类排名."""
    return ak.fund_individual_achievement_xq(symbol=symbol).to_markdown(index=False)


def fund_individual_analysis_xq(symbol):
    """雪球基金数据分析。返回: 较同类风险收益比, 较同类抗风险波动, 年化波动率, 年化夏普比率, 最大回撤.

    Bypasses akshare and calls the Danjuan API directly to avoid
    pd.json_normalize issues with nested self_index structures.
    """
    url = f"https://danjuanfunds.com/djapi/fund/base/quote/data/index/analysis/{symbol}"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                      "(KHTML, like Gecko) Chrome/80.0.3987.149 Safari/537.36"
    }
    r = requests.get(url, headers=headers, timeout=None)
    resp = r.json()
    data = resp.get("data", resp)

    index_list = data.get("index_data_list")
    if not index_list:
        # No analysis data available for this fund
        return json.dumps(
            {"message": "该基金暂无数据分析", "fund_code": data.get("fund_code", symbol)},
            ensure_ascii=False, indent=2,
        )

    rows = []
    for item in index_list:
        si = item.get("self_index", {})
        rows.append({
            "周期": item.get("index_time_period", ""),
            "较同类风险收益比": item.get("investment_cost_performance", ""),
            "较同类抗风险波动": item.get("risk_control", ""),
            "年化波动率": f"{si.get('volatility_rank', 0) * 100:.2f}%",
            "年化夏普比率": f"{si.get('sharpe_rank', 0):.2f}",
            "最大回撤": f"{si.get('max_draw_down', 0) * 100:.2f}%",
        })

    return json.dumps(rows, ensure_ascii=False, indent=2)

def fund_individual_profit_probability_xq(symbol):
    """雪球基金盈利概率。历史任意时点买入持有满X年，盈利概率Y%."""
    return ak.fund_individual_profit_probability_xq(symbol=symbol).to_markdown(index=False)


def fund_individual_detail_info_xq(symbol):
    """雪球基金交易规则。返回: 买入规则, 卖出规则, 管理费, 托管费, 销售服务费."""
    return ak.fund_individual_detail_info_xq(symbol=symbol).to_markdown(index=False)


def fund_individual_detail_hold_xq(symbol, date="20231231"):
    """雪球基金持仓。返回资产类型占比: 股票, 债券, 现金, 其他."""
    return ak.fund_individual_detail_hold_xq(symbol=symbol, date=date).to_markdown(index=False)
