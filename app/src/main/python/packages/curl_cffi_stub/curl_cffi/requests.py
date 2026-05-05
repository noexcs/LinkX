class _DummyResponse:
    """Dummy response object with minimal required fields."""

    def __init__(self, *args, **kwargs):
        pass

    def __getattr__(self, name):
        return ""

    @property
    def status_code(self):
        return 200

    @property
    def text(self):
        return ""

    @property
    def content(self):
        return b""

    def json(self):
        return {}

    def __repr__(self):
        return "<curl_cffi stub Response>"


class _DummySession:
    def __init__(self, *args, **kwargs):
        pass

    def __getattr__(self, name):
        def method(*args, **kwargs):
            return _DummyResponse()
        return method

    def __enter__(self):
        return self

    def __exit__(self, *args):
        pass


def get(*args, **kwargs):
    return _DummyResponse()


def post(*args, **kwargs):
    return _DummyResponse()


def Session(*args, **kwargs):
    return _DummySession()
