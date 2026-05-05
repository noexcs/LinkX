"""基金分析工具。

提供2个核心工具供AI分析基金：
1. fund_performance   — 综合业绩指标（收益、回撤、夏普、波动率）
2. fund_vs_benchmark  — 与基准（沪深300等）对比
"""

import json
import time

import numpy as np
import pandas as pd

import _chaquopy_patch  # noqa: F401
import akshare as ak


# ============================================================
# 数据获取
# ============================================================

def _get_fund_nav(symbol):
    """获取基金净值历史 (成立以来)。返回 DataFrame: 净值日期, 单位净值, 日增长率."""
    return ak.fund_open_fund_info_em(symbol=symbol, indicator="单位净值走势", period="成立来")


def _get_index_data(symbol="000300", start_date="20240101"):
    """获取指数历史行情。"""
    try:
        df = ak.stock_zh_a_hist(symbol=symbol, period="daily",
                                start_date=start_date, end_date="20500101", adjust="qfq")
        if not df.empty:
            return df
    except Exception:
        pass
    # TX fallback
    try:
        df = ak.stock_zh_a_hist_tx(symbol=f"sh{symbol}" if symbol.startswith("0") else f"sz{symbol}",
                                   start_date=start_date, end_date="20500101", adjust="qfq")
        if not df.empty:
            return df
    except Exception:
        pass
    return pd.DataFrame()


# ============================================================
# 1. fund_performance — 综合业绩指标
# ============================================================

def fund_performance(symbol):
    """基金综合业绩指标。返回: 各周期收益, 最大回撤, 年化收益/波动率, 夏普比率, 卡玛比率."""
    try:
        df = _get_fund_nav(symbol)
    except Exception as e:
        return json.dumps({"error": f"获取净值失败: {e}"}, ensure_ascii=False)

    if df.empty:
        return json.dumps({"error": f"基金 {symbol} 无净值数据"}, ensure_ascii=False)

    nav_series = df.set_index("净值日期")["单位净值"].dropna()
    if len(nav_series) < 5:
        return json.dumps({"error": "净值数据不足"}, ensure_ascii=False)

    latest_nav = float(nav_series.iloc[-1])
    latest_date = str(nav_series.index[-1])

    result = {
        "基金代码": symbol,
        "最新净值": round(latest_nav, 4),
        "净值日期": latest_date,
    }

    # 各周期收益
    periods = {"近1周": 5, "近1月": 21, "近3月": 63, "近6月": 126,
               "近1年": 252, "近2年": 504, "近3年": 756, "今年以来": None}
    start_of_year = pd.Timestamp(f"{pd.Timestamp.now().year}-01-01")

    for name, days in periods.items():
        try:
            if name == "今年以来":
                ytd = nav_series[nav_series.index >= start_of_year]
                if len(ytd) > 1:
                    ret = (latest_nav / float(ytd.iloc[0]) - 1) * 100
                    result[name] = f"{ret:.2f}%"
            elif len(nav_series) > days:
                ret = (latest_nav / float(nav_series.iloc[-days-1]) - 1) * 100
                result[name] = f"{ret:.2f}%"
        except Exception:
            pass

    # 最大回撤
    cummax = nav_series.cummax()
    drawdown = (nav_series - cummax) / cummax
    max_dd = float(drawdown.min()) * 100
    result["最大回撤"] = f"{max_dd:.2f}%"

    # 年化收益
    total_days = len(nav_series)
    if total_days > 60:
        total_ret = latest_nav / float(nav_series.iloc[0]) - 1
        years = total_days / 252
        if years > 0.1:
            annual_ret = (1 + total_ret) ** (1 / years) - 1
            result["年化收益"] = f"{annual_ret * 100:.2f}%"

    # 波动率 + 夏普比率
    if total_days > 21:
        daily_ret = nav_series.pct_change().dropna()
        vol = float(daily_ret.std()) * np.sqrt(252)
        result["年化波动率"] = f"{vol * 100:.2f}%"

        if vol > 0 and "年化收益" in result:
            ann_ret = float(result["年化收益"].replace("%", "")) / 100
            sharpe = (ann_ret - 0.025) / vol  # assuming 2.5% risk-free
            result["夏普比率"] = f"{sharpe:.2f}"
            # 卡玛比率 = 年化收益 / |最大回撤|
            if abs(max_dd) > 0.01:
                calmar = ann_ret / (abs(max_dd) / 100)
                result["卡玛比率"] = f"{calmar:.2f}"

    return json.dumps(result, ensure_ascii=False, indent=2)


# ============================================================
# 2. fund_vs_benchmark — 基准对比
# ============================================================

def fund_vs_benchmark(symbol, benchmark="000300"):
    """基金 vs 基准对比。benchmark默认沪深300(000300)。返回: 各周期超额收益, 相关性, 相对强弱."""
    try:
        fund_df = _get_fund_nav(symbol)
    except Exception as e:
        return json.dumps({"error": f"获取基金净值失败: {e}"}, ensure_ascii=False)

    if fund_df.empty or len(fund_df) < 30:
        return json.dumps({"error": f"基金 {symbol} 净值数据不足"}, ensure_ascii=False)

    fund_nav = fund_df.set_index("净值日期")["单位净值"]

    index_df = _get_index_data(benchmark, start_date=str(fund_nav.index[0])[:10])
    if index_df.empty:
        return json.dumps({"benchmark_note": f"无法获取{benchmark}指数数据, 仅返回基金数据"},
                         ensure_ascii=False)

    index_close = index_df.set_index("日期")["收盘"]

    # Align dates
    common_dates = fund_nav.index.intersection(index_close.index)
    if len(common_dates) < 20:
        return json.dumps({"error": "基金与基准共同交易日不足20天"}, ensure_ascii=False)

    f = fund_nav[common_dates]
    b = index_close[common_dates]

    f_ret = f.pct_change().dropna()
    b_ret = b.pct_change().dropna()

    # Align returns
    common = f_ret.index.intersection(b_ret.index)
    f_ret = f_ret[common]
    b_ret = b_ret[common]

    result = {
        "基金代码": symbol,
        "基准代码": benchmark,
        "共同交易日": len(common),
    }

    # 各周期超额收益
    periods = {"近1月": 21, "近3月": 63, "近6月": 126, "近1年": 252}
    for name, days in periods:
        if len(f) > days and len(b) > days:
            f_ret_p = float(f.iloc[-1] / f.iloc[-days-1] - 1) * 100
            b_ret_p = float(b.iloc[-1] / b.iloc[-days-1] - 1) * 100
            result[f"{name}_基金"] = f"{f_ret_p:.2f}%"
            result[f"{name}_基准"] = f"{b_ret_p:.2f}%"
            result[f"{name}_超额"] = f"{f_ret_p - b_ret_p:.2f}%"

    # 相关性
    if len(f_ret) > 20:
        corr = float(f_ret.corr(b_ret))
        result["相关性"] = round(corr, 3)

    # 相对强弱
    if len(f) > 5:
        relative = f / f.iloc[0]
        relative_bench = b / b.iloc[0]
        outperf = float(relative.iloc[-1] - relative_bench.iloc[-1]) * 100
        result["相对强弱"] = f"{outperf:+.2f}%"
        result["相对强弱说明"] = "正值为跑赢基准, 负值为跑输基准"

    return json.dumps(result, ensure_ascii=False, indent=2)


