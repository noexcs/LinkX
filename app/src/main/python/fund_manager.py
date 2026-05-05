import _chaquopy_patch  # noqa: F401 — monkey-patches lxml→html5lib for Chaquopy
import akshare as ak


def fund_manager_em(name):
    """基金经理信息。按姓名筛选，返回: 姓名, 所属公司, 现任基金代码, 现任基金, 累计从业时间, 现任基金资产总规模, 现任基金最佳回报."""
    df = ak.fund_manager_em()
    df.rename(columns={"累计从业时间": "累计从业时间(天)"}, inplace=True)
    return df[df["姓名"] == name].to_markdown(index=False)
