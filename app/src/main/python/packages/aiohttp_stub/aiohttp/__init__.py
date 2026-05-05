class _Dummy:
    """Dummy object that accepts any attribute access, call, or async context manager."""
    __slots__ = ()

    def __init__(self, *args, **kwargs):
        pass

    def __call__(self, *args, **kwargs):
        return _Dummy()

    def __getattr__(self, name):
        return _Dummy()

    def __getitem__(self, key):
        return _Dummy()

    def __setitem__(self, key, value):
        pass

    def __bool__(self):
        return False

    def __repr__(self):
        return "<aiohttp stub>"

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        pass

    def __await__(self):
        return _Dummy().__await__()


ClientSession = _Dummy
ClientTimeout = _Dummy
ClientError = _Dummy
ClientConnectionError = _Dummy
TCPConnector = _Dummy
ContentTypeError = _Dummy
ClientResponseError = _Dummy
ClientPayloadError = _Dummy
FormData = _Dummy
MultipartWriter = _Dummy
StreamReader = _Dummy
BasicAuth = _Dummy
CookieJar = _Dummy
DummyCookieJar = _Dummy
Timeout = _Dummy
WSMsgType = _Dummy
WSMessage = _Dummy
web = _Dummy()
hdrs = _Dummy()
payload = _Dummy()
