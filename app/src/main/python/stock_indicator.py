"""技术指标工具。基于A股历史行情计算6组技术指标，返回最新值。

每组指标先通过 stock_hist.stock_zh_a_hist() 获取历史行情，
再执行纯 pandas/numpy 计算，无需额外 API 依赖。
"""

import numpy as np
import pandas as pd

import _chaquopy_patch  # noqa: F401
from stock_hist import stock_zh_a_hist as _fetch_hist


def _get_hist(symbol, start_date, end_date, period="daily", adjust="qfq"):
    """获取历史行情 DataFrame (通过 stock_hist 模块)。"""
    import akshare as ak
    import time
    for attempt in range(3):
        try:
            df = ak.stock_zh_a_hist(
                symbol=symbol, period=period,
                start_date=start_date, end_date=end_date, adjust=adjust
            )
            if not df.empty:
                return df
        except Exception:
            if attempt < 2:
                time.sleep(1)
    # TX fallback
    code = symbol.strip()
    if len(code) == 6:
        code = f"sh{code}" if code.startswith(("6", "9")) else f"sz{code}"
    for attempt in range(3):
        try:
            df = ak.stock_zh_a_hist_tx(
                symbol=code, start_date=start_date, end_date=end_date, adjust=adjust
            )
            if not df.empty:
                return df
        except Exception:
            if attempt < 2:
                time.sleep(1)
    return pd.DataFrame()


# ============================================================
# Trend indicators (趋势指标)
# ============================================================

def trend_indicator(symbol, start_date="20250101", end_date="20500101", limit=5):
    """趋势指标: MACD, DMA, TRIX, BOLL, SAR, MIKE。返回最新limit条。"""
    df = _get_hist(symbol, start_date, end_date, period="daily", adjust="qfq")
    if df.empty:
        return f"无法获取 {symbol} 的历史数据"

    df = _calc_macd(df)
    df = _calc_dma(df)
    df = _calc_trix(df)
    df = _calc_boll(df)
    df = _calc_sar(df)
    df = _calc_mike(df)

    cols = ["日期", "收盘", "DIF", "DEA", "MACD", "DDD", "AMA",
            "TRIX", "MATRIX", "BOLL", "UB", "LB", "SAR",
            "WR", "MR", "SR", "WS", "MS", "SS"]
    cols = [c for c in cols if c in df.columns]
    return df[cols].tail(limit).to_markdown(index=False)


def oscillator_indicator(symbol, start_date="20250101", end_date="20500101", limit=5):
    """振荡指标: KDJ, RSI, WR, ROC, BIAS, CCI。返回最新limit条。"""
    df = _get_hist(symbol, start_date, end_date, period="daily", adjust="qfq")
    if df.empty:
        return f"无法获取 {symbol} 的历史数据"

    df = _calc_kdj(df)
    df = _calc_rsi(df)
    df = _calc_wr(df)
    df = _calc_roc(df)
    df = _calc_bias(df)
    df = _calc_cci(df)

    cols = ["日期", "收盘", "K", "D", "J",
            "RSI6", "RSI12", "RSI24",
            "WR6", "WR10", "WR14",
            "ROC", "MAROC",
            "BIAS6", "BIAS12", "BIAS24", "CCI"]
    cols = [c for c in cols if c in df.columns]
    return df[cols].tail(limit).to_markdown(index=False)


def volume_indicator(symbol, start_date="20250101", end_date="20500101", limit=5):
    """成交量指标: OBV, VR。返回最新limit条。"""
    df = _get_hist(symbol, start_date, end_date, period="daily", adjust="qfq")
    if df.empty:
        return f"无法获取 {symbol} 的历史数据"

    df = _calc_obv(df)
    df = _calc_vr(df)

    cols = ["日期", "收盘", "成交量", "OBV", "VR"]
    cols = [c for c in cols if c in df.columns]
    return df[cols].tail(limit).to_markdown(index=False)


def momentum_indicator(symbol, start_date="20250101", end_date="20500101", limit=5):
    """动量指标: PSY, MTM。返回最新limit条。"""
    df = _get_hist(symbol, start_date, end_date, period="daily", adjust="qfq")
    if df.empty:
        return f"无法获取 {symbol} 的历史数据"

    df = _calc_psy(df)
    df = _calc_mtm(df)

    cols = ["日期", "收盘", "PSY", "PSYMA", "MTM", "MTMMA"]
    cols = [c for c in cols if c in df.columns]
    return df[cols].tail(limit).to_markdown(index=False)


def directional_indicator(symbol, start_date="20250101", end_date="20500101", limit=5):
    """方向指标: DMI (PDI, MDI, ADX, ADXR)。返回最新limit条。"""
    df = _get_hist(symbol, start_date, end_date, period="daily", adjust="qfq")
    if df.empty:
        return f"无法获取 {symbol} 的历史数据"

    df = _calc_dmi(df)

    cols = ["日期", "收盘", "PDI", "MDI", "ADX", "ADXR"]
    cols = [c for c in cols if c in df.columns]
    return df[cols].tail(limit).to_markdown(index=False)


def energy_indicator(symbol, start_date="20250101", end_date="20500101", limit=5):
    """能量指标: CR, ARBR。返回最新limit条。"""
    df = _get_hist(symbol, start_date, end_date, period="daily", adjust="qfq")
    if df.empty:
        return f"无法获取 {symbol} 的历史数据"

    df = _calc_cr(df)
    df = _calc_arbr(df)

    cols = ["日期", "收盘", "CR", "AR", "BR"]
    cols = [c for c in cols if c in df.columns]
    return df[cols].tail(limit).to_markdown(index=False)


# ============================================================
# Internal calculation functions
# ============================================================

def _calc_macd(df, short=12, long=26, m=9):
    """MACD (指数平滑异同移动平均线) → DIF, DEA, MACD"""
    ema_short = df["收盘"].ewm(span=short, adjust=False).mean()
    ema_long = df["收盘"].ewm(span=long, adjust=False).mean()
    df["DIF"] = ema_short - ema_long
    df["DEA"] = df["DIF"].ewm(span=m, adjust=False).mean()
    df["MACD"] = (df["DIF"] - df["DEA"]) * 2
    return df


def _calc_dma(df, short=10, long=50, m=10):
    """DMA (平行线差) → DDD, AMA"""
    ma_s = df["收盘"].rolling(window=short, min_periods=1).mean()
    ma_l = df["收盘"].rolling(window=long, min_periods=1).mean()
    df["DDD"] = ma_s - ma_l
    df["AMA"] = df["DDD"].rolling(window=m, min_periods=1).mean()
    return df


def _calc_trix(df, n=12, m=9):
    """TRIX (三重指数平滑) → TRIX, MATRIX"""
    ema1 = df["收盘"].ewm(span=n, adjust=False).mean()
    ema2 = ema1.ewm(span=n, adjust=False).mean()
    ema3 = ema2.ewm(span=n, adjust=False).mean()
    df["TRIX"] = (ema3 - ema3.shift(1)) / ema3.shift(1) * 100
    df["MATRIX"] = df["TRIX"].ewm(span=m, adjust=False).mean()
    return df


def _calc_boll(df, n=20, k=2.0):
    """BOLL (布林带) → BOLL, UB, LB"""
    df["BOLL"] = df["收盘"].rolling(window=n, min_periods=1).mean()
    std = df["收盘"].rolling(window=n, min_periods=1).std()
    df["UB"] = df["BOLL"] + k * std
    df["LB"] = df["BOLL"] - k * std
    return df


def _calc_sar(df, n=4, step=0.02, max_step=0.2):
    """SAR (抛物线) → SAR"""
    sar = [None] * len(df)
    af = step
    is_up = True
    if len(df) < n:
        df["SAR"] = df["收盘"]
        return df
    sar[n - 1] = df["最低"].iloc[:n].min()
    ep = df["最高"].iloc[:n].max()
    for i in range(n, len(df)):
        sar[i] = sar[i - 1] + af * (ep - sar[i - 1])
        if is_up:
            low_i1 = df["最低"].iloc[i - 1]
            low_i2 = df["最低"].iloc[i - 2] if i > 1 else low_i1
            sar[i] = min(sar[i], low_i1, low_i2)
            if df["最低"].iloc[i] < sar[i]:
                is_up = False
                sar[i] = ep
                ep = df["最低"].iloc[i]
                af = step
            elif df["最高"].iloc[i] > ep:
                ep = df["最高"].iloc[i]
                af = min(af + step, max_step)
        else:
            high_i1 = df["最高"].iloc[i - 1]
            high_i2 = df["最高"].iloc[i - 2] if i > 1 else high_i1
            sar[i] = max(sar[i], high_i1, high_i2)
            if df["最高"].iloc[i] > sar[i]:
                is_up = True
                sar[i] = ep
                ep = df["最高"].iloc[i]
                af = step
            elif df["最低"].iloc[i] < ep:
                ep = df["最低"].iloc[i]
                af = min(af + step, max_step)
    df["SAR"] = sar
    df["SAR"] = df["SAR"].fillna(method="bfill")
    return df


def _calc_mike(df, n=12):
    """MIKE (麦克) → WR, MR, SR, WS, MS, SS"""
    typ = (df["最高"] + df["最低"] + df["收盘"]) / 3
    ll = df["最低"].rolling(window=n, min_periods=1).min()
    hh = df["最高"].rolling(window=n, min_periods=1).max()
    df["WR"] = typ + (typ - ll)
    df["MR"] = typ + (hh - ll)
    df["SR"] = 2 * hh - ll
    df["WS"] = typ - (hh - typ)
    df["MS"] = typ - (hh - ll)
    df["SS"] = 2 * ll - hh
    return df


def _calc_kdj(df, n=9, m1=3, m2=3):
    """KDJ → K, D, J"""
    low_n = df["最低"].rolling(window=n, min_periods=1).min()
    high_n = df["最高"].rolling(window=n, min_periods=1).max()
    rsv = (df["收盘"] - low_n) / (high_n - low_n) * 100
    rsv = rsv.fillna(50)
    df["K"] = rsv.ewm(com=m1 - 1, adjust=False).mean()
    df["D"] = df["K"].ewm(com=m2 - 1, adjust=False).mean()
    df["J"] = 3 * df["K"] - 2 * df["D"]
    return df


def _calc_rsi(df, periods=(6, 12, 24)):
    """RSI (相对强弱) → RSI6, RSI12, RSI24"""
    for p in periods:
        delta = df["收盘"].diff()
        gain = (delta.where(delta > 0, 0)).rolling(window=p, min_periods=1).mean()
        loss = (-delta.where(delta < 0, 0)).rolling(window=p, min_periods=1).mean()
        rs = gain / loss
        df[f"RSI{p}"] = (100 - (100 / (1 + rs))).fillna(50)
    return df


def _calc_wr(df, periods=(6, 10, 14)):
    """W%R (威廉) → WR6, WR10, WR14"""
    for p in periods:
        high_n = df["最高"].rolling(window=p, min_periods=1).max()
        low_n = df["最低"].rolling(window=p, min_periods=1).min()
        df[f"WR{p}"] = ((high_n - df["收盘"]) / (high_n - low_n) * -100).fillna(-50)
    return df


def _calc_roc(df, n=12, m=6):
    """ROC (变动率) → ROC, MAROC"""
    df["ROC"] = (df["收盘"] - df["收盘"].shift(n)) / df["收盘"].shift(n) * 100
    df["MAROC"] = df["ROC"].rolling(window=m, min_periods=1).mean()
    return df


def _calc_bias(df, periods=(6, 12, 24)):
    """BIAS (乖离率) → BIAS6, BIAS12, BIAS24"""
    for p in periods:
        ma = df["收盘"].rolling(window=p, min_periods=1).mean()
        df[f"BIAS{p}"] = (df["收盘"] - ma) / ma * 100
    return df


def _calc_cci(df, n=14):
    """CCI (顺势) → CCI"""
    tp = (df["最高"] + df["最低"] + df["收盘"]) / 3
    ma = tp.rolling(window=n, min_periods=1).mean()
    md = tp.rolling(window=n, min_periods=1).apply(
        lambda x: np.abs(x - x.mean()).mean(), raw=True
    )
    df["CCI"] = ((tp - ma) / (0.015 * md)).fillna(0)
    return df


def _calc_obv(df):
    """OBV (能量潮) → OBV"""
    obv = 0
    obv_list = []
    for i in range(len(df)):
        if i == 0:
            obv = df.iloc[i]["成交量"]
        elif df.iloc[i]["收盘"] > df.iloc[i - 1]["收盘"]:
            obv += df.iloc[i]["成交量"]
        elif df.iloc[i]["收盘"] < df.iloc[i - 1]["收盘"]:
            obv -= df.iloc[i]["成交量"]
        obv_list.append(obv)
    df["OBV"] = obv_list
    return df


def _calc_vr(df, n=26):
    """VR (成交量变异率) → VR"""
    pc = df["收盘"].diff()
    av_up = df["成交量"].where(pc > 0, 0).rolling(window=n, min_periods=1).sum()
    av_down = df["成交量"].where(pc < 0, 0).rolling(window=n, min_periods=1).sum()
    av_eq = df["成交量"].where(pc == 0, 0).rolling(window=n, min_periods=1).sum()
    df["VR"] = ((av_up + av_eq / 2) / (av_down + av_eq / 2) * 100).fillna(100)
    return df


def _calc_psy(df, n=12, m=6):
    """PSY (心理线) → PSY, PSYMA"""
    up = (df["收盘"] > df["收盘"].shift(1)).astype(int)
    df["PSY"] = up.rolling(window=n, min_periods=1).sum() / n * 100
    df["PSYMA"] = df["PSY"].rolling(window=m, min_periods=1).mean()
    return df


def _calc_mtm(df, n=12, m=6):
    """MTM (动量) → MTM, MTMMA"""
    df["MTM"] = df["收盘"] - df["收盘"].shift(n)
    df["MTMMA"] = df["MTM"].rolling(window=m, min_periods=1).mean()
    return df


def _calc_dmi(df, n=14, m=6):
    """DMI (动向) → PDI, MDI, ADX, ADXR"""
    hd = df["最高"].diff()
    ld = -df["最低"].diff()
    pdm = hd.where((hd > ld) & (hd > 0), 0)
    mdm = ld.where((ld > hd) & (ld > 0), 0)
    tr = pd.concat([
        df["最高"] - df["最低"],
        (df["最高"] - df["收盘"].shift(1)).abs(),
        (df["最低"] - df["收盘"].shift(1)).abs(),
    ], axis=1).max(axis=1)
    atr = tr.rolling(window=n, min_periods=1).mean()
    df["PDI"] = (pdm.rolling(window=n, min_periods=1).mean() / atr * 100).fillna(0)
    df["MDI"] = (mdm.rolling(window=n, min_periods=1).mean() / atr * 100).fillna(0)
    dx = (df["PDI"] - df["MDI"]).abs() / (df["PDI"] + df["MDI"]) * 100
    df["ADX"] = dx.rolling(window=m, min_periods=1).mean()
    df["ADXR"] = (df["ADX"] + df["ADX"].shift(m)) / 2
    return df


def _calc_cr(df, n=26):
    """CR (能量) → CR"""
    mid = (df["最高"] + df["最低"] + df["收盘"]) / 3
    df["CR"] = (
        (df["最高"] - mid.shift(1)).rolling(window=n, min_periods=1).sum()
        / (mid.shift(1) - df["最低"]).rolling(window=n, min_periods=1).sum()
        * 100
    ).fillna(100)
    return df


def _calc_arbr(df, n=26):
    """ARBR (人气意愿) → AR, BR"""
    ho = df["最高"] - df["开盘"]
    ol = df["开盘"] - df["最低"]
    hc = df["最高"] - df["收盘"].shift(1)
    cl = df["收盘"].shift(1) - df["最低"]
    df["AR"] = (ho.rolling(window=n, min_periods=1).sum() / ol.rolling(window=n, min_periods=1).sum() * 100).fillna(100)
    df["BR"] = (hc.rolling(window=n, min_periods=1).sum() / cl.rolling(window=n, min_periods=1).sum() * 100).fillna(100)
    return df
