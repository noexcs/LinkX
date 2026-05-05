"""Apply monkey-patches so that lxml-reliant libraries work on Chaquopy/Android.

lxml is built by Chaquopy but unusable at runtime because libxml2.so and
libxslt.so are not bundled. Force pandas.read_html() and BeautifulSoup to
use html.parser (Python stdlib, pure Python, always available) instead.

Switched from html5lib to html.parser to avoid SoupStrainer.name attribute
errors caused by bs4 4.14.x + html5lib 1.1 compatibility issues.
"""


# --- Patch pandas read_html --------------------------------------------------
def _patch_pandas_read_html():
    import pandas.io.html as _pd_html

    _orig_validate_flavor = _pd_html._validate_flavor

    def _patched_validate_flavor(flavor):
        if flavor is None:
            flavor = ("bs4",)
        return _orig_validate_flavor(flavor)

    _pd_html._validate_flavor = _patched_validate_flavor


# --- Patch BeautifulSoup -----------------------------------------------------
def _patch_beautifulsoup():
    import bs4

    _orig_init = bs4.BeautifulSoup.__init__

    def _patched_init(self, *args, **kwargs):
        if kwargs.get("features") in (None, "lxml", "xml"):
            kwargs["features"] = "html.parser"
        elif kwargs.get("features") == "lxml-xml":
            kwargs["features"] = "xml"
        _orig_init(self, *args, **kwargs)

    bs4.BeautifulSoup.__init__ = _patched_init


# --- Patch SoupStrainer for html.parser compatibility ------------------------
def _patch_soupstrainer():
    """Ensure SoupStrainer objects always have a 'name' attribute.

    bs4 4.14.x's SoupStrainer extends ElementFilter and may be checked
    for .name by tree-builder code paths. Add a fallback property.
    """
    import bs4.filter

    if not hasattr(bs4.filter.SoupStrainer, "name"):
        bs4.filter.SoupStrainer.name = None


_patch_pandas_read_html()
_patch_beautifulsoup()
_patch_soupstrainer()
