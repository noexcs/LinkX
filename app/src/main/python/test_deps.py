"""Test all dependencies for Chaquopy Android. Call test() to run."""
import traceback


def test():
    results = []

    def run(name, stmt):
        try:
            exec(stmt)
            results.append(f"OK  {name}")
        except Exception:
            results.append(f"FAIL {name}: {traceback.format_exc()[-120:].strip()}")

    # Pure Python
    run("pytz", "import pytz")
    run("dateutil", "import dateutil")
    run("six", "import six")
    run("bs4", "import bs4")
    run("html5lib", "import html5lib")
    run("xlrd", "import xlrd")
    run("openpyxl", "import openpyxl")
    run("tqdm", "import tqdm")
    run("tabulate", "import tabulate")
    run("decorator", "import decorator")
    run("requests", "import requests")
    run("urllib3", "import urllib3")
    run("certifi", "import certifi")
    run("charset_normalizer", "import charset_normalizer")
    run("idna", "import idna")

    # Native
    run("numpy", "import numpy; results.append(f'    numpy {numpy.__version__}')")
    run("pandas", "import pandas; results.append(f'    pandas {pandas.__version__}')")
    # lxml is intentionally not installed: needs libxml2.so / libxslt.so
    # which Chaquopy does not bundle. Use html.parser (stdlib) instead.
    # See _chaquopy_patch.py for the runtime monkey-patches.

    # akshare
    run("akshare", "import akshare")

    # Functional test
    try:
        import akshare as ak
        df = ak.fund_em_value_estimation()
        results.append(f"OK  fund_em_value_estimation() -> {df.shape[0]} funds")
    except Exception:
        results.append(f"FAIL fund_em_value_estimation(): {traceback.format_exc()[-150:].strip()}")

    passed = sum(1 for r in results if r.startswith("OK"))
    total = len(results)
    results.append(f"\n{passed}/{total} passed" + (" - ALL OK" if passed == total else " - SOME FAILED"))
    return "\n".join(results)
