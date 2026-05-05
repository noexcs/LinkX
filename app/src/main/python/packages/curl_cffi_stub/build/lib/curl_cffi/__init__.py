from . import requests as _requests_module


class _Dummy:
    """Dummy object that accepts any attribute access, call, or context manager."""
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
        return "<curl_cffi stub>"

    def __enter__(self):
        return self

    def __exit__(self, *args):
        pass


Curl = _Dummy
CurlError = _Dummy
