"""用户持仓管理。

提供持仓CRUD + 汇总分析功能。数据存储在JSON文件中，路径由Android端传入。
"""

import json
import os

_storage_path = None


def portfolio_init(data_dir):
    """初始化持仓存储。由Android端在Python启动后调用。"""
    global _storage_path
    _storage_path = os.path.join(data_dir, "portfolio.json")
    if not os.path.exists(_storage_path):
        _save([])


def _load():
    if _storage_path is None:
        raise RuntimeError("portfolio未初始化, 请先调用portfolio_init")
    try:
        with open(_storage_path, "r") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return []


def _save(data):
    if _storage_path is None:
        raise RuntimeError("portfolio未初始化")
    os.makedirs(os.path.dirname(_storage_path), exist_ok=True)
    with open(_storage_path, "w") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


# ============================================================
# CRUD
# ============================================================

def portfolio_add(code, name="", cost_nav=1.0, shares=0.0, buy_date="", notes=""):
    """添加持仓。

    code: 基金代码(6位)
    name: 基金名称(可选, 不填则自动获取)
    cost_nav: 买入时的单位净值
    shares: 持有份额
    buy_date: 购买日期 YYYY-MM-DD
    notes: 备注(如: 定投/一次性/目标仓位等)
    """
    holdings = _load()
    max_id = max((h.get("id", 0) for h in holdings), default=0)

    holding = {
        "id": max_id + 1,
        "code": str(code).strip(),
        "name": str(name).strip(),
        "cost_nav": float(cost_nav),
        "shares": float(shares),
        "buy_date": str(buy_date).strip(),
        "notes": str(notes).strip(),
    }
    holdings.append(holding)
    _save(holdings)
    return json.dumps({"status": "ok", "action": "added", "holding": holding},
                      ensure_ascii=False, indent=2)


def portfolio_update(id, code=None, name=None, cost_nav=None, shares=None,
                     buy_date=None, notes=None):
    """更新持仓。只需传要更新的字段，不传则保持原值。"""
    holdings = _load()
    target = None
    for h in holdings:
        if h["id"] == int(id):
            target = h
            break
    if target is None:
        return json.dumps({"status": "error", "message": f"持仓 id={id} 不存在"},
                         ensure_ascii=False)

    if code is not None:
        target["code"] = str(code).strip()
    if name is not None:
        target["name"] = str(name).strip()
    if cost_nav is not None:
        target["cost_nav"] = float(cost_nav)
    if shares is not None:
        target["shares"] = float(shares)
    if buy_date is not None:
        target["buy_date"] = str(buy_date).strip()
    if notes is not None:
        target["notes"] = str(notes).strip()

    _save(holdings)
    return json.dumps({"status": "ok", "action": "updated", "holding": target},
                      ensure_ascii=False, indent=2)


def portfolio_remove(id):
    """删除指定持仓。"""
    holdings = _load()
    new_list = [h for h in holdings if h["id"] != int(id)]
    if len(new_list) == len(holdings):
        return json.dumps({"status": "error", "message": f"持仓 id={id} 不存在"},
                         ensure_ascii=False)
    _save(new_list)
    return json.dumps({"status": "ok", "action": "removed", "id": int(id)},
                      ensure_ascii=False)


def portfolio_list():
    """列出全部持仓。返回holdings数组。"""
    holdings = _load()
    if not holdings:
        return json.dumps({"status": "ok", "holdings": [], "count": 0},
                         ensure_ascii=False, indent=2)
    return json.dumps({"status": "ok", "holdings": holdings, "count": len(holdings)},
                      ensure_ascii=False, indent=2)


def portfolio_clear():
    """清空全部持仓(需确认)。"""
    _save([])
    return json.dumps({"status": "ok", "action": "cleared", "count": 0},
                      ensure_ascii=False)


# ============================================================
# 汇总分析
# ============================================================

def portfolio_summary():
    """持仓汇总分析。

    自动获取每支基金的最新净值和业绩数据，计算:
    - 当前市值/成本/盈亏金额/收益率
    - 持仓占比
    - 组合总盈亏
    同时获取每支基金的买卖信号。
    """
    from fund_analysis import fund_performance
    import akshare as ak

    holdings = _load()
    if not holdings:
        return json.dumps({"status": "ok", "message": "暂无持仓数据。请用 portfolio_add 添加持仓。"},
                         ensure_ascii=False, indent=2)

    total_cost = 0.0
    total_value = 0.0
    items = []

    for h in holdings:
        code = h["code"]
        cost_nav = h["cost_nav"]
        shares = h["shares"]
        cost = cost_nav * shares
        total_cost += cost

        # 获取当前净值
        current_nav = None
        fund_name = h["name"] or code
        try:
            df = ak.fund_open_fund_info_em(symbol=code, indicator="单位净值走势",
                                           period="成立来")
            if not df.empty:
                current_nav = float(df["单位净值"].iloc[-1])
                # 尝试获取基金名称
                if not h["name"]:
                    try:
                        js_url = f"https://fund.eastmoney.com/pingzhongdata/{code}.js"
                        import requests
                        r = requests.get(js_url, timeout=5)
                        import re
                        m = re.search(r'fS_name\s*=\s*"([^"]+)"', r.text)
                        if m:
                            fund_name = m.group(1)
                    except Exception:
                        pass
        except Exception:
            pass

        if current_nav is None:
            items.append({
                "id": h["id"], "code": code, "name": fund_name,
                "cost_nav": cost_nav, "shares": shares, "cost": round(cost, 2),
                "current_nav": "获取失败", "current_value": "N/A",
                "pnl": "N/A", "return": "N/A",
                "buy_date": h["buy_date"], "notes": h["notes"]
            })
            continue

        value = current_nav * shares
        total_value += value
        pnl = value - cost
        ret = (current_nav / cost_nav - 1) * 100 if cost_nav > 0 else 0

        items.append({
            "id": h["id"], "code": code, "name": fund_name,
            "cost_nav": round(cost_nav, 4), "current_nav": round(current_nav, 4),
            "shares": shares, "cost": round(cost, 2),
            "current_value": round(value, 2),
            "pnl": round(pnl, 2), "pnl_pct": f"{ret:+.2f}%",
            "buy_date": h["buy_date"], "notes": h["notes"]
        })

    total_pnl = total_value - total_cost
    total_ret = (total_value / total_cost - 1) * 100 if total_cost > 0 else 0

    result = {
        "持仓汇总": {
            "总成本": round(total_cost, 2),
            "总市值": round(total_value, 2),
            "总盈亏": round(total_pnl, 2),
            "总收益率": f"{total_ret:+.2f}%",
            "持仓数量": len(holdings),
        },
        "持仓明细": items,
    }

    # 持仓占比
    if total_value > 0:
        for item in items:
            if isinstance(item.get("current_value"), (int, float)):
                item["占比"] = f"{item['current_value'] / total_value * 100:.1f}%"

    return json.dumps(result, ensure_ascii=False, indent=2)


def portfolio_analyze_all():
    """对全部持仓逐一获取买卖信号 + 业绩指标，供AI全面分析。"""
    from fund_analysis import fund_performance

    holdings = _load()
    if not holdings:
        return json.dumps({"status": "ok", "message": "暂无持仓"}, ensure_ascii=False, indent=2)

    results = []
    for h in holdings:
        code = h["code"]
        item = {"id": h["id"], "code": code, "name": h["name"] or code,
                "cost_nav": h["cost_nav"], "shares": h["shares"],
                "buy_date": h["buy_date"], "notes": h["notes"]}

        try:
            item["performance"] = json.loads(fund_performance(code))
        except Exception as e:
            item["performance"] = {"error": str(e)}

        results.append(item)

    return json.dumps(results, ensure_ascii=False, indent=2)
